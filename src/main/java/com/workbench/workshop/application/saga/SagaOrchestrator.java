package com.workbench.workshop.application.saga;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.workshop.application.AfterCommit;
import com.workbench.workshop.application.saga.port.out.SagaCommandPort;
import com.workbench.workshop.application.saga.port.out.SagaStateRepository;
import com.workbench.workshop.application.service.port.out.ServiceRepository;
import com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort;
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
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static com.workbench.workshop.application.saga.SagaMessages.*;

/**
 * Orquestrador da Saga da OS (ADR-0005). Reage aos eventos de resposta do Execution
 * e do Billing e decide o próximo comando (ou a compensação), mantendo o
 * {@link SagaState} persistido.
 *
 * <p>Fluxo feliz:
 * reserva confirmada → gerar orçamento → (aprovação humana) → ... → cobrar
 * pagamento → pago → entregue. Pausas humanas: aprovação do orçamento e confirmação
 * do pagamento. Compensações: recusa do orçamento libera estoque; falha de pagamento
 * NÃO estorna estoque (serviço já executado) — a OS fica retida.
 *
 * <p>Robustez: cada evento só é aceito no passo em que é esperado (descarta
 * repetidos e atrasados, já que o SQS entrega "pelo menos uma vez") e os comandos
 * só saem depois do commit ({@link AfterCommit}).
 */
@Service
@Slf4j
public class SagaOrchestrator {

    /** Passo em que cada evento é esperado; fora dele, o evento é repetido/atrasado. */
    private static final Map<String, SagaStep> EXPECTED_STEP = Map.of(
            STOCK_RESERVED, SagaStep.AGUARDANDO_RESERVA,
            RESERVATION_FAILED, SagaStep.AGUARDANDO_RESERVA,
            BUDGET_GENERATED, SagaStep.AGUARDANDO_ORCAMENTO,
            BUDGET_FAILED, SagaStep.AGUARDANDO_ORCAMENTO,
            RESERVATION_CONFIRMED, SagaStep.AGUARDANDO_PAGAMENTO,
            RESERVATION_RELEASED, SagaStep.COMPENSANDO,
            CHARGE_CREATED, SagaStep.AGUARDANDO_PAGAMENTO,
            PAYMENT_CONFIRMED, SagaStep.AGUARDANDO_PAGAMENTO,
            PAYMENT_FAILED, SagaStep.AGUARDANDO_PAGAMENTO);

    private final SagaStateRepository sagaStateRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceRepository serviceRepository;
    private final SagaCommandPort commandPort;
    private final CustomerNotificationPort customerNotificationPort;
    // Serialização interna do payload pendente (Map simples); não depende do Jackson do contexto.
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Máximo de reenvios de um comando antes de desistir/compensar a etapa. */
    @Value("${saga.max-retries:3}")
    private int maxRetries;

    // S9352: os beans candidatos (Noop/SNS) são mutuamente exclusivos via
    // @ConditionalOnProperty — só um existe em runtime. O analisador do Sonar não
    // avalia a condição, então o alerta de ambiguidade é falso positivo.
    @SuppressWarnings("java:S9352")
    public SagaOrchestrator(SagaStateRepository sagaStateRepository,
                            ServiceOrderRepository serviceOrderRepository,
                            ServiceRepository serviceRepository,
                            SagaCommandPort commandPort,
                            CustomerNotificationPort customerNotificationPort) {
        this.sagaStateRepository = sagaStateRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.serviceRepository = serviceRepository;
        this.commandPort = commandPort;
        this.customerNotificationPort = customerNotificationPort;
    }

    /** Inicia a saga ao criar a OS: comanda a reserva de estoque. */
    @Transactional
    public void start(ServiceOrder order, List<ReserveItem> items) {
        // Uma OS tem uma Saga só: num novo diagnóstico depois de falha, a mesma recomeça.
        SagaState saga = sagaStateRepository.findByServiceOrderId(order.getId())
                .map(existing -> {
                    existing.restart();
                    return existing;
                })
                .orElseGet(() -> SagaState.start(order.getId()));
        saga.advanceTo(SagaStep.AGUARDANDO_RESERVA, "INICIADA");
        log.info("Saga iniciada; solicitando reserva de estoque: os={}, itens={}",
                order.getId(), items.size());

        Map<String, Object> payload = Map.of("items", items.stream()
                .map(i -> Map.<String, Object>of("productId", i.productId(), "quantity", i.quantity()))
                .toList());
        dispatch(saga, RESERVE_STOCK, payload);
        sagaStateRepository.save(saga);
    }

    /** Estado da Saga da OS (passo, passos concluídos, motivo da falha), se houver. */
    @Transactional(readOnly = true)
    public Optional<SagaState> findSaga(UUID osId) {
        return sagaStateRepository.findByServiceOrderId(osId);
    }

    /** True quando a OS é conduzida por uma Saga (não pode ser avançada à mão nos passos dela). */
    @Transactional(readOnly = true)
    public boolean hasSaga(UUID osId) {
        return sagaStateRepository.findByServiceOrderId(osId).isPresent();
    }

    /**
     * Envia um comando que aguarda resposta e registra a pendência no SagaState
     * (para reenvio por timeout). O payload é serializado para permitir o reenvio.
     */
    private void dispatch(SagaState saga, String command, Map<String, Object> payload) {
        saga.awaitCommand(command, writeJson(payload));
        send(saga, command, payload);
    }

    /** Publica o comando, com o sagaId da rodada atual, só depois do commit do estado da Saga. */
    private void send(SagaState saga, String command, Map<String, Object> payload) {
        UUID osId = saga.getServiceOrderId();
        UUID sagaId = saga.getSagaId();
        AfterCommit.run("comando " + command + " da OS " + osId,
                () -> commandPort.send(command, sagaId, osId, payload));
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
        dispatch(saga, CONFIRM_RESERVATION, Map.of());
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
        ServiceOrder order = serviceOrderRepository.findById(osId)
                .orElseThrow(() -> new NoSuchElementException("Ordem de serviço não encontrada: " + osId));
        order.reject(reason);
        serviceOrderRepository.save(order);
        saga.markCompensating(reason == null ? "orçamento recusado" : reason);
        dispatch(saga, RELEASE_RESERVATION, Map.of());
        sagaStateRepository.save(saga);
    }

    private SagaState requireSaga(UUID osId) {
        return sagaStateRepository.findByServiceOrderId(osId)
                .orElseThrow(() -> new IllegalStateException("Saga inexistente para a OS " + osId));
    }

    /**
     * A OS entrou em PAGAMENTO_PENDENTE (serviço finalizado): comanda o Billing a
     * criar a cobrança automaticamente ({@code ComandoCobrarPagamento}). A confirmação
     * virá depois ({@code PagamentoConfirmado}). Idempotente: se a cobrança já foi
     * comandada para esta OS, não reenvia.
     */
    @Transactional
    public void onPaymentPending(UUID osId) {
        // Tolerante: OS avançada manualmente (sem Saga ativa) não dispara cobrança — apenas
        // segue. A geração automática de cobrança só se aplica quando há uma Saga em curso.
        SagaState saga = sagaStateRepository.findByServiceOrderId(osId).orElse(null);
        if (saga == null) {
            log.info("OS em PAGAMENTO_PENDENTE sem Saga ativa; cobrança automática não aplicável: os={}", osId);
            return;
        }
        if (CHARGE_PAYMENT.equals(saga.getPendingCommand())) {
            log.info("Cobrança já comandada para a OS; ignorando novo disparo: os={}", osId);
            return;
        }
        log.info("OS em PAGAMENTO_PENDENTE; comandando criação de cobrança: os={}", osId);
        dispatch(saga, CHARGE_PAYMENT, Map.of());
        sagaStateRepository.save(saga);
    }

    /**
     * Processa um evento de resposta da Saga.
     *
     * @param sagaId rodada a que a resposta pertence (o sagaId do comando, devolvido pelo participante)
     */
    @Transactional
    public void onEvent(String type, String sagaId, UUID serviceOrderId, Map<String, Object> payload) {
        SagaState saga = sagaStateRepository.findByServiceOrderId(serviceOrderId).orElse(null);
        if (saga == null) {
            log.warn("Evento sem saga correspondente: type={}, os={}", type, serviceOrderId);
            return;
        }
        if (saga.isFromAnotherRun(sagaId)) {
            log.warn("Evento de outra rodada da Saga ignorado: type={}, os={}, sagaId={}, rodada atual={}",
                    type, serviceOrderId, sagaId, saga.getSagaId());
            return;
        }
        SagaStep expected = EXPECTED_STEP.get(type);
        if (expected != null && saga.getStep() != expected) {
            log.warn("Evento repetido ou fora de ordem ignorado: type={}, os={}, passo atual={}, esperado={}",
                    type, serviceOrderId, saga.getStep(), expected);
            return;
        }
        switch (type) {
            case STOCK_RESERVED -> onStockReserved(saga, serviceOrderId, payload);
            case RESERVATION_FAILED -> onReservationFailed(saga, serviceOrderId, payload);
            case BUDGET_GENERATED -> onBudgetGenerated(saga, serviceOrderId);
            case BUDGET_FAILED -> onBudgetFailed(saga, serviceOrderId, payload);
            case RESERVATION_CONFIRMED -> onReservationConfirmed(saga, serviceOrderId);
            case RESERVATION_RELEASED -> onReservationReleased(saga, serviceOrderId);
            case CHARGE_CREATED -> onChargeCreated(saga, serviceOrderId, payload);
            case PAYMENT_CONFIRMED -> onPaymentConfirmed(saga, serviceOrderId);
            case PAYMENT_FAILED -> onPaymentFailed(saga, serviceOrderId, payload);
            default -> log.warn("Evento ignorado pelo orquestrador: type={}", type);
        }
    }

    // Reserva OK → pede orçamento com peças (vindas do Execution no evento) + mão de
    // obra (dos serviços da OS, dona do laborCost). O Billing soma as duas partes. O
    // documento do cliente vai junto para o Billing saber o dono do orçamento.
    private void onStockReserved(SagaState saga, UUID osId, Map<String, Object> payload) {
        saga.advanceTo(SagaStep.AGUARDANDO_ORCAMENTO, "RESERVA");
        log.info("Reserva confirmada; solicitando orçamento: os={}", osId);

        Map<String, Object> command = new HashMap<>();
        command.put("parts", payload.getOrDefault("parts", List.of()));
        command.put("labor", laborItems(osId));
        serviceOrderRepository.findById(osId)
                .ifPresent(order -> command.put("customerDocument", order.getCpfCnpj()));
        dispatch(saga, GENERATE_BUDGET, command);
        sagaStateRepository.save(saga);
    }

    // Orçamento falhou (após a reserva): tenta de novo algumas vezes; esgotado, compensa
    // liberando a reserva (as peças ainda não foram baixadas) — ADR-0005 / Saga.
    private void onBudgetFailed(SagaState saga, UUID osId, Map<String, Object> payload) {
        String reason = String.valueOf(payload.getOrDefault("reason", "orçamento falhou"));
        if (saga.getRetryCount() < maxRetries) {
            saga.markRetry();
            sagaStateRepository.save(saga);
            log.warn("Orçamento falhou (tentativa {} de {}); reenviando: os={}",
                    saga.getRetryCount(), maxRetries, osId);
            send(saga, GENERATE_BUDGET, readJson(saga.getPendingPayloadJson()));
            return;
        }
        // Tentativas esgotadas → compensa: libera a reserva de estoque (ainda ACTIVE).
        log.warn("Orçamento falhou após {} tentativas; compensando (liberar reserva): os={}", maxRetries, osId);
        saga.markCompensating(reason);
        dispatch(saga, RELEASE_RESERVATION, Map.of());
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
    private void onReservationFailed(SagaState saga, UUID osId, Map<String, Object> payload) {
        saga.markFailed(String.valueOf(payload.getOrDefault("reason", "reserva falhou")));
        sagaStateRepository.save(saga);
        log.warn("Saga abortada por falha de reserva: os={}", osId);
    }

    // Orçamento gerado → OS aguarda aprovação do cliente (pausa humana).
    // A OS recém-criada está em RECEBIDA; o fluxo (OrderServiceStatusEnum) exige
    // passar por EM_DIAGNOSTICO antes de AGUARDANDO_APROVACAO. O orquestrador avança
    // os dois passos de forma idempotente (só avança o que ainda não foi avançado).
    private void onBudgetGenerated(SagaState saga, UUID osId) {
        ServiceOrder order = serviceOrderRepository.findById(osId)
                .orElseThrow(() -> new NoSuchElementException("Ordem de serviço não encontrada: " + osId));
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
    // aguardando o pagamento. Se a resposta chegou atrasada, depois de já haver uma
    // cobrança pendente, não apaga essa pendência.
    private void onReservationConfirmed(SagaState saga, UUID osId) {
        if (CONFIRM_RESERVATION.equals(saga.getPendingCommand())) {
            saga.clearPendingCommand();
        }
        sagaStateRepository.save(saga);
        log.info("Reserva baixada (estoque confirmado): os={}", osId);
    }

    // Reserva liberada (compensação concluída após recusa/falha do orçamento): a OS já
    // está em RECUSADA; a saga confirma o encerramento da compensação.
    private void onReservationReleased(SagaState saga, UUID osId) {
        saga.clearPendingCommand();
        saga.advanceTo(SagaStep.FALHOU, "COMPENSACAO");
        sagaStateRepository.save(saga);
        log.info("Compensação concluída (estoque liberado): os={}", osId);
    }

    // Cobrança criada no Billing (resposta do ComandoCobrarPagamento): envia ao cliente
    // o LINK de pagamento (checkout) por e-mail e segue em PAGAMENTO_PENDENTE aguardando
    // a confirmação. Sem novo comando pendente.
    private void onChargeCreated(SagaState saga, UUID osId, Map<String, Object> payload) {
        saga.clearPendingCommand();
        sagaStateRepository.save(saga);

        Object checkoutUrl = payload.get("checkoutUrl");
        if (checkoutUrl != null) {
            serviceOrderRepository.findById(osId).ifPresent(order -> AfterCommit.run(
                    "e-mail com link de pagamento da OS " + osId,
                    () -> customerNotificationPort.notifyPaymentLink(order, String.valueOf(checkoutUrl))));
        }
        log.info("Cobrança criada; link de pagamento enviado ao cliente; aguardando confirmação: os={}", osId);
    }

    // Pagamento confirmado → OS vai para PAGO e, em seguida, ENTREGUE. O cliente recebe UM e-mail,
    // o do estado final: a OS passa por PAGO e ENTREGUE na mesma transação, então avisar nos dois
    // mandaria duas mensagens no mesmo segundo.
    private void onPaymentConfirmed(SagaState saga, UUID osId) {
        advanceOrder(osId, OrderServiceStatusEnum.PAGO);
        ServiceOrder delivered = advanceOrder(osId, OrderServiceStatusEnum.ENTREGUE);
        saga.advanceTo(SagaStep.CONCLUIDA, "PAGAMENTO");
        saga.clearPendingCommand();
        saga.markCompleted();
        sagaStateRepository.save(saga);
        AfterCommit.run("e-mail de pagamento confirmado da OS " + osId,
                () -> customerNotificationPort.notifyCustomer(delivered));
        log.info("Pagamento confirmado; Saga concluída (OS paga e entregue): os={}", osId);
    }

    // Pagamento falhou → OS permanece retida em PAGAMENTO_PENDENTE (ADR-0005):
    // NÃO estorna estoque (serviço já executado), veículo não é liberado. É tratamento
    // de erro, não compensação: a Saga segue em AGUARDANDO_PAGAMENTO com o motivo
    // registrado, e o cliente/equipe pode pedir uma nova cobrança (retryPayment).
    private void onPaymentFailed(SagaState saga, UUID osId, Map<String, Object> payload) {
        // O SQS entrega "pelo menos uma vez" e a Saga segue em AGUARDANDO_PAGAMENTO depois da recusa,
        // então a mesma recusa pode chegar de novo. Só avisa o cliente na primeira: o payment-retry
        // limpa o motivo, e uma recusa da cobrança nova volta a avisar.
        boolean firstRefusal = saga.getFailureReason() == null;
        saga.clearPendingCommand();
        saga.recordFailure(String.valueOf(payload.getOrDefault("reason", "pagamento recusado")));
        sagaStateRepository.save(saga);
        if (firstRefusal) {
            serviceOrderRepository.findById(osId).ifPresent(order -> AfterCommit.run(
                    "e-mail de pagamento recusado da OS " + osId,
                    () -> customerNotificationPort.notifyPaymentFailed(order)));
        }
        log.warn("Pagamento falhou; OS retida em PAGAMENTO_PENDENTE (sem estorno de estoque): os={}", osId);
    }

    /**
     * Nova cobrança depois de um pagamento recusado: comanda o Billing a cobrar de novo
     * (novo link de pagamento). Só vale com a Saga aguardando pagamento, um pagamento já
     * recusado e nenhuma cobrança em andamento.
     */
    @Transactional
    public void retryPayment(UUID osId) {
        SagaState saga = requireSaga(osId);
        if (saga.getStep() != SagaStep.AGUARDANDO_PAGAMENTO || saga.getFailureReason() == null
                || saga.hasPendingCommand()) {
            throw new IllegalStateException("Nova cobrança só é possível depois de um pagamento recusado.");
        }
        saga.clearFailure();
        log.info("Nova cobrança solicitada após pagamento recusado: os={}", osId);
        dispatch(saga, CHARGE_PAYMENT, Map.of());
        sagaStateRepository.save(saga);
    }

    /** OS com comando pendente cujo último envio é anterior a {@code olderThan}. */
    @Transactional(readOnly = true)
    public List<UUID> findTimedOut(LocalDateTime olderThan) {
        return sagaStateRepository.findPendingOlderThan(olderThan).stream()
                .map(SagaState::getServiceOrderId)
                .toList();
    }

    /**
     * Reenvia o comando pendente de uma OS cujo último envio passou do timeout (comando ou
     * resposta perdidos). Esgotadas as tentativas de uma etapa compensável, aciona a
     * compensação. Uma transação por OS: um conflito ou erro numa Saga não desfaz o reenvio
     * das outras. Chamado por um job agendado (com lock distribuído) — ver
     * {@code SagaTimeoutScheduler}.
     *
     * @param olderThan limite: só reenvia se o último envio ainda for anterior a isso
     */
    @Transactional
    public void resendTimedOut(UUID osId, LocalDateTime olderThan) {
        SagaState saga = sagaStateRepository.findByServiceOrderId(osId).orElse(null);
        // A resposta pode ter chegado entre a busca e agora.
        if (saga == null || !saga.hasPendingCommand() || saga.getLastCommandAt() == null
                || !saga.getLastCommandAt().isBefore(olderThan)) {
            return;
        }
        if (saga.getRetryCount() >= maxRetries) {
            // Esgotou: se a etapa pendente é compensável (reserva ainda não baixada),
            // compensa; senão apenas registra a falha (nada a reverter).
            log.warn("Timeout: comando {} esgotou {} tentativas para OS {}",
                    saga.getPendingCommand(), maxRetries, osId);
            compensateOnTimeout(saga, osId);
            return;
        }
        saga.markRetry();
        sagaStateRepository.save(saga);
        log.warn("Timeout: reenviando {} (tentativa {} de {}) para OS {}",
                saga.getPendingCommand(), saga.getRetryCount(), maxRetries, osId);
        send(saga, saga.getPendingCommand(), readJson(saga.getPendingPayloadJson()));
    }

    // Decide a compensação quando uma etapa esgota as tentativas por timeout.
    private void compensateOnTimeout(SagaState saga, UUID osId) {
        String pending = saga.getPendingCommand();
        String reason = "timeout em " + pending;
        // Até a baixa (reserva/orçamento) a reserva está ACTIVE: compensável liberando o
        // estoque.
        if (RESERVE_STOCK.equals(pending) || GENERATE_BUDGET.equals(pending)) {
            saga.markCompensating(reason);
            dispatch(saga, RELEASE_RESERVATION, Map.of());
        } else if (CONFIRM_RESERVATION.equals(pending) || CHARGE_PAYMENT.equals(pending)) {
            // Depois da aprovação não há o que reverter, mas a OS ainda vai ser paga: a Saga
            // segue em AGUARDANDO_PAGAMENTO com o motivo. Assim a nova cobrança
            // (retryPayment) é aceita, e uma resposta atrasada do participante também.
            saga.clearPendingCommand();
            saga.recordFailure(reason);
        } else {
            saga.markFailed(reason);
        }
        sagaStateRepository.save(saga);
    }

    private ServiceOrder advanceOrder(UUID osId, OrderServiceStatusEnum target) {
        ServiceOrder order = serviceOrderRepository.findById(osId)
                .orElseThrow(() -> new NoSuchElementException("Ordem de serviço não encontrada: " + osId));
        order.advanceTo(target);
        return serviceOrderRepository.save(order);
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

    public record ReserveItem(String productId, BigDecimal quantity) {}
}
