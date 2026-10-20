package com.workbench.workshop.application.saga;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.workshop.application.saga.port.out.SagaCommandPort;
import com.workbench.workshop.application.saga.port.out.SagaStateRepository;
import com.workbench.workshop.application.service.port.out.ServiceRepository;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.saga.SagaState;
import com.workbench.workshop.domain.saga.SagaStep;
import com.workbench.workshop.domain.service.WorkshopService;
import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orquestrador da Saga da OS (ADR-0005). Reage aos eventos de resposta do Execution
 * e do Billing e decide o próximo comando (ou a compensação), mantendo o
 * {@link SagaState} persistido.
 *
 * <p>Fluxo feliz:
 * reserva confirmada → gerar orçamento → (aprovação humana) → ... → cobrar
 * pagamento → pago → entregue. Pausas humanas: aprovação do orçamento e webhook de
 * pagamento. Compensações: recusa do orçamento libera estoque; falha de pagamento
 * NÃO estorna estoque (serviço já executado) — a OS fica retida.
 */
@Service
@Slf4j
public class SagaOrchestrator {

    private final SagaStateRepository sagaStateRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceRepository serviceRepository;
    private final SagaCommandPort commandPort;
    // Serialização interna do payload pendente (Map simples); não depende do Jackson do contexto.
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Máximo de reenvios de um comando antes de desistir/compensar a etapa. */
    @Value("${saga.max-retries:3}")
    private int maxRetries;

    public SagaOrchestrator(SagaStateRepository sagaStateRepository,
                            ServiceOrderRepository serviceOrderRepository,
                            ServiceRepository serviceRepository,
                            SagaCommandPort commandPort) {
        this.sagaStateRepository = sagaStateRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.serviceRepository = serviceRepository;
        this.commandPort = commandPort;
    }

    /** Inicia a saga ao criar a OS: comanda a reserva de estoque. */
    @Transactional
    public void start(ServiceOrder order, List<ReserveItem> items) {
        SagaState saga = SagaState.start(order.getId());
        saga.advanceTo(SagaStep.AGUARDANDO_RESERVA, "INICIADA");
        log.info("Saga iniciada; solicitando reserva de estoque: os={}, itens={}",
                order.getId(), items.size());

        Map<String, Object> payload = Map.of("items", items.stream()
                .map(i -> Map.<String, Object>of("productId", i.productId(), "quantity", i.quantity()))
                .toList());
        dispatch(saga, "ComandoReservarEstoque", order.getId(), payload);
        sagaStateRepository.save(saga);
    }

    /**
     * Envia um comando que aguarda resposta e registra a pendência no SagaState
     * (para reenvio por timeout). O payload é serializado para permitir o reenvio.
     */
    private void dispatch(SagaState saga, String command, UUID osId, Map<String, Object> payload) {
        saga.awaitCommand(command, writeJson(payload));
        commandPort.send(command, osId, osId, payload);
    }

    /**
     * Decisão humana de aprovação do orçamento. Confirma a reserva (baixa de
     * estoque definitiva) no Execution e avança a OS para APROVADO.
     */
    @Transactional
    public void onBudgetApproved(UUID osId) {
        SagaState saga = requireSaga(osId);
        advanceOrder(osId, OrderServiceStatusEnum.APROVADO);
        saga.advanceTo(SagaStep.AGUARDANDO_PAGAMENTO, "APROVACAO");
        log.info("Orçamento aprovado pelo cliente; confirmando reserva de estoque: os={}", osId);
        dispatch(saga, "ComandoConfirmarReserva", osId, Map.of());
        sagaStateRepository.save(saga);
    }

    /**
     * Decisão humana de recusa do orçamento (compensação). Libera a reserva de
     * estoque no Execution (as peças voltam ao disponível) — único ponto em que
     * há estorno de estoque (ADR-0005) — e marca a OS como RECUSADA.
     */
    @Transactional
    public void onBudgetRejected(UUID osId, String reason) {
        SagaState saga = requireSaga(osId);
        ServiceOrder order = serviceOrderRepository.findById(osId).orElseThrow();
        order.reject(reason);
        serviceOrderRepository.save(order);
        saga.markCompensating(reason == null ? "orçamento recusado" : reason);
        dispatch(saga, "ComandoLiberarReserva", osId, Map.of());
        sagaStateRepository.save(saga);
    }

    private SagaState requireSaga(UUID osId) {
        return sagaStateRepository.findByServiceOrderId(osId)
                .orElseThrow(() -> new IllegalStateException("Saga inexistente para a OS " + osId));
    }

    /** Processa um evento de resposta da Saga. */
    @Transactional
    public void onEvent(String type, UUID serviceOrderId, Map<String, Object> payload) {
        SagaState saga = sagaStateRepository.findByServiceOrderId(serviceOrderId).orElse(null);
        if (saga == null) {
            log.warn("Evento sem saga correspondente: type={}, os={}", type, serviceOrderId);
            return;
        }
        switch (type) {
            case "ReservaConfirmada" -> onReservaConfirmada(saga, serviceOrderId, payload);
            case "ReservaFalhou" -> onReservaFalhou(saga, serviceOrderId, payload);
            case "OrcamentoGerado" -> onOrcamentoGerado(saga, serviceOrderId);
            case "OrcamentoFalhou" -> onOrcamentoFalhou(saga, serviceOrderId, payload);
            case "ReservaBaixada" -> onReservaBaixada(saga, serviceOrderId);
            case "ReservaLiberada" -> onReservaLiberada(saga, serviceOrderId);
            case "PagamentoConfirmado" -> onPagamentoConfirmado(saga, serviceOrderId);
            case "PagamentoFalhou" -> onPagamentoFalhou(saga, serviceOrderId, payload);
            default -> log.warn("Evento ignorado pelo orquestrador: type={}", type);
        }
    }

    // Reserva OK → pede orçamento com peças (vindas do Execution no evento) + mão de
    // obra (dos serviços da OS, dona do laborCost). O Billing soma as duas partes.
    private void onReservaConfirmada(SagaState saga, UUID osId, Map<String, Object> payload) {
        saga.advanceTo(SagaStep.AGUARDANDO_ORCAMENTO, "RESERVA");
        log.info("Reserva confirmada; solicitando orçamento: os={}", osId);

        Map<String, Object> command = new HashMap<>();
        command.put("parts", payload.getOrDefault("parts", List.of()));
        command.put("labor", laborItems(osId));
        dispatch(saga, "ComandoGerarOrcamento", osId, command);
        sagaStateRepository.save(saga);
    }

    // Orçamento falhou (após a reserva): tenta de novo algumas vezes; esgotado, compensa
    // liberando a reserva (as peças ainda não foram baixadas) — ADR-0005 / Saga.
    private void onOrcamentoFalhou(SagaState saga, UUID osId, Map<String, Object> payload) {
        String reason = String.valueOf(payload.getOrDefault("reason", "orçamento falhou"));
        if (saga.getRetryCount() < maxRetries) {
            saga.markRetry();
            sagaStateRepository.save(saga);
            log.warn("Orçamento falhou (tentativa {} de {}); reenviando: os={}",
                    saga.getRetryCount(), maxRetries, osId);
            commandPort.send("ComandoGerarOrcamento", osId, osId, readJson(saga.getPendingPayloadJson()));
            return;
        }
        // Tentativas esgotadas → compensa: libera a reserva de estoque (ainda ACTIVE).
        log.warn("Orçamento falhou após {} tentativas; compensando (liberar reserva): os={}", maxRetries, osId);
        saga.markCompensating(reason);
        dispatch(saga, "ComandoLiberarReserva", osId, Map.of());
        sagaStateRepository.save(saga);
    }

    // Mão de obra = um item por serviço da OS com laborCost > 0 (SERVICE_COST no Billing).
    private List<Map<String, Object>> laborItems(UUID osId) {
        List<Map<String, Object>> labor = new ArrayList<>();
        for (WorkshopService service : serviceRepository.findByIdOS(osId)) {
            BigDecimal cost = service.getLaborCost();
            if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("serviceName", service.getServiceTypeName());
            item.put("laborCode", null);
            item.put("laborCost", cost);
            labor.add(item);
        }
        return labor;
    }

    // Reserva falhou (estoque insuficiente) → aborta; nada a compensar ainda
    // (a reserva não chegou a prender estoque). Tratamento de erro, não compensação.
    private void onReservaFalhou(SagaState saga, UUID osId, Map<String, Object> payload) {
        saga.clearPendingCommand();
        saga.markFailed(String.valueOf(payload.getOrDefault("reason", "reserva falhou")));
        sagaStateRepository.save(saga);
        log.warn("Saga abortada por falha de reserva: os={}", osId);
    }

    // Orçamento gerado → OS aguarda aprovação do cliente (pausa humana).
    // A OS recém-criada está em RECEBIDA; o fluxo (OrderServiceStatusEnum) exige
    // passar por EM_DIAGNOSTICO antes de AGUARDANDO_APROVACAO. O orquestrador avança
    // os dois passos de forma idempotente (só avança o que ainda não foi avançado).
    private void onOrcamentoGerado(SagaState saga, UUID osId) {
        ServiceOrder order = serviceOrderRepository.findById(osId).orElseThrow();
        if (order.getStatus() == OrderServiceStatusEnum.RECEBIDA) {
            order.advanceTo(OrderServiceStatusEnum.EM_DIAGNOSTICO);
        }
        if (order.getStatus() == OrderServiceStatusEnum.EM_DIAGNOSTICO) {
            order.advanceTo(OrderServiceStatusEnum.AGUARDANDO_APROVACAO);
        }
        serviceOrderRepository.save(order);
        saga.advanceTo(SagaStep.AGUARDANDO_APROVACAO, "ORCAMENTO");
        saga.clearPendingCommand(); // pausa humana: nenhum comando pendente aguardando resposta
        sagaStateRepository.save(saga);
        log.info("Orçamento gerado; OS aguardando aprovação do cliente: os={}", osId);
    }

    // Reserva baixada (estoque confirmado após aprovação): resposta recebida; a OS segue
    // aguardando o pagamento (pausa até o webhook). Sem novo comando pendente.
    private void onReservaBaixada(SagaState saga, UUID osId) {
        saga.clearPendingCommand();
        sagaStateRepository.save(saga);
        log.info("Reserva baixada (estoque confirmado): os={}", osId);
    }

    // Reserva liberada (compensação concluída após recusa/falha do orçamento): a OS já
    // está em RECUSADA; a saga confirma o encerramento da compensação.
    private void onReservaLiberada(SagaState saga, UUID osId) {
        saga.clearPendingCommand();
        saga.advanceTo(SagaStep.FALHOU, "COMPENSACAO");
        sagaStateRepository.save(saga);
        log.info("Compensação concluída (estoque liberado): os={}", osId);
    }

    // Pagamento confirmado (webhook) → OS vai para PAGO e, em seguida, ENTREGUE.
    private void onPagamentoConfirmado(SagaState saga, UUID osId) {
        advanceOrder(osId, OrderServiceStatusEnum.PAGO);
        advanceOrder(osId, OrderServiceStatusEnum.ENTREGUE);
        saga.advanceTo(SagaStep.CONCLUIDA, "PAGAMENTO");
        saga.clearPendingCommand();
        saga.markCompleted();
        sagaStateRepository.save(saga);
        log.info("Pagamento confirmado; Saga concluída (OS paga e entregue): os={}", osId);
    }

    // Pagamento falhou → OS permanece retida em PAGAMENTO_PENDENTE (ADR-0005):
    // NÃO estorna estoque (serviço já executado), veículo não é liberado. É tratamento
    // de erro (retenção + lembrete), não compensação — nada anterior precisa ser revertido.
    private void onPagamentoFalhou(SagaState saga, UUID osId, Map<String, Object> payload) {
        saga.clearPendingCommand();
        saga.markCompensating(String.valueOf(payload.getOrDefault("reason", "pagamento recusado")));
        sagaStateRepository.save(saga);
        log.warn("Pagamento falhou; OS retida em PAGAMENTO_PENDENTE (sem estorno de estoque): os={}", osId);
    }

    /**
     * Reenvia comandos pendentes cujo último envio passou do timeout (comando/resposta
     * perdido). Esgotadas as tentativas de uma etapa compensável, aciona a compensação.
     * Chamado por um job agendado (com lock distribuído) — ver {@code SagaTimeoutScheduler}.
     *
     * @param olderThan limite: só sagas com {@code lastCommandAt} anterior a isso
     */
    @Transactional
    public void resendTimedOut(LocalDateTime olderThan) {
        for (SagaState saga : sagaStateRepository.findPendingOlderThan(olderThan)) {
            UUID osId = saga.getServiceOrderId();
            if (saga.getRetryCount() >= maxRetries) {
                // Esgotou: se a etapa pendente é compensável (reserva ainda não baixada),
                // compensa; senão apenas marca falha (nada a reverter).
                log.warn("Timeout: comando {} esgotou {} tentativas para OS {}",
                        saga.getPendingCommand(), maxRetries, osId);
                compensateOnTimeout(saga, osId);
                continue;
            }
            saga.markRetry();
            sagaStateRepository.save(saga);
            log.warn("Timeout: reenviando {} (tentativa {} de {}) para OS {}",
                    saga.getPendingCommand(), saga.getRetryCount(), maxRetries, osId);
            commandPort.send(saga.getPendingCommand(), osId, osId, readJson(saga.getPendingPayloadJson()));
        }
    }

    // Decide a compensação quando uma etapa esgota as tentativas por timeout.
    private void compensateOnTimeout(SagaState saga, UUID osId) {
        String pending = saga.getPendingCommand();
        // Até a baixa (ComandoReservarEstoque/ComandoGerarOrcamento) a reserva está ACTIVE:
        // compensável liberando o estoque. Demais etapas: sem reversão (trata o erro).
        if ("ComandoReservarEstoque".equals(pending) || "ComandoGerarOrcamento".equals(pending)) {
            saga.markCompensating("timeout em " + pending);
            dispatch(saga, "ComandoLiberarReserva", osId, Map.of());
        } else {
            saga.markFailed("timeout em " + pending);
        }
        sagaStateRepository.save(saga);
    }

    private void advanceOrder(UUID osId, OrderServiceStatusEnum target) {
        ServiceOrder order = serviceOrderRepository.findById(osId).orElseThrow();
        order.advanceTo(target);
        serviceOrderRepository.save(order);
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar payload do comando da Saga", e);
        }
    }

    private Map<String, Object> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao desserializar payload do comando da Saga", e);
        }
    }

    public record ReserveItem(String productId, java.math.BigDecimal quantity) {}
}
