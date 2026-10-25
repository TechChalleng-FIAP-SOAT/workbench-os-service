package com.workbench.workshop.application.saga;

import com.workbench.workshop.application.saga.port.out.SagaCommandPort;
import com.workbench.workshop.application.saga.port.out.SagaStateRepository;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.saga.SagaState;
import com.workbench.workshop.domain.saga.SagaStep;
import com.workbench.workshop.domain.service.WorkshopService;
import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SagaOrchestratorTest {

    private SagaStateRepository sagaStateRepository;
    private ServiceOrderRepository serviceOrderRepository;
    private com.workbench.workshop.application.service.port.out.ServiceRepository serviceRepository;
    private SagaCommandPort commandPort;
    private com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort customerNotificationPort;
    private SagaOrchestrator orchestrator;

    private final UUID osId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sagaStateRepository = mock(SagaStateRepository.class);
        serviceOrderRepository = mock(ServiceOrderRepository.class);
        serviceRepository = mock(com.workbench.workshop.application.service.port.out.ServiceRepository.class);
        commandPort = mock(SagaCommandPort.class);
        customerNotificationPort = mock(com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort.class);
        orchestrator = new SagaOrchestrator(sagaStateRepository, serviceOrderRepository,
                serviceRepository, commandPort, customerNotificationPort);
        org.springframework.test.util.ReflectionTestUtils.setField(orchestrator, "maxRetries", 3);
        when(sagaStateRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(serviceOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(serviceRepository.findByIdOS(any())).thenReturn(List.of());
    }

    private ServiceOrder order(OrderServiceStatusEnum status) {
        return ServiceOrder.reconstitute(osId, "52998224725", "ABC1234",
                List.of("TROCA_OLEO"), "[TROCA_OLEO]", status, null,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private SagaState sagaAt(SagaStep step) {
        return SagaState.reconstitute(1L, osId, step, List.of(), null,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void startSavesSagaAndCommandsReservation() {
        var items = List.of(new SagaOrchestrator.ReserveItem("p1", new BigDecimal("2")));

        orchestrator.start(order(OrderServiceStatusEnum.RECEBIDA), items);

        verify(sagaStateRepository).save(argThat(s -> s.getStep() == SagaStep.AGUARDANDO_RESERVA));
        verify(commandPort).send(eq("ComandoReservarEstoque"), eq(osId), eq(osId), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reservaConfirmadaCommandsBudgetWithPartsAndLabor() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_RESERVA)));
        // Dois serviços na OS: um com MO > 0 (entra no orçamento), outro com MO 0 (ignorado).
        var s1 = new WorkshopService(UUID.randomUUID(), "TROCA_OLEO", osId, List.of());
        s1.setLaborCost(new BigDecimal("150"));
        var s2 = new WorkshopService(UUID.randomUUID(), "REVISAO", osId, List.of());
        s2.setLaborCost(BigDecimal.ZERO);
        when(serviceRepository.findByIdOS(osId)).thenReturn(List.of(s1, s2));

        var parts = List.of(Map.<String, Object>of("productId", "p1", "unitPrice", new BigDecimal("500")));
        orchestrator.onEvent("ReservaConfirmada", osId, Map.of("parts", parts));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(commandPort).send(eq("ComandoGerarOrcamento"), eq(osId), eq(osId), captor.capture());
        Map<String, Object> cmd = captor.getValue();
        assertEquals(parts, cmd.get("parts"));
        List<Map<String, Object>> labor = (List<Map<String, Object>>) cmd.get("labor");
        assertEquals(1, labor.size()); // só o serviço com MO > 0
        assertEquals("TROCA_OLEO", labor.get(0).get("serviceName"));
        assertEquals(new BigDecimal("150"), labor.get(0).get("laborCost"));
    }

    @Test
    void reservaFalhouMarksFailed() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_RESERVA)));

        orchestrator.onEvent("ReservaFalhou", osId, Map.of("reason", "sem estoque"));

        verify(sagaStateRepository).save(argThat(s -> s.getStep() == SagaStep.FALHOU));
        verify(commandPort, never()).send(any(), any(), any(), anyMap());
    }

    @Test
    void orcamentoGeradoAdvancesOrderToAguardandoAprovacao() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_ORCAMENTO)));
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(order(OrderServiceStatusEnum.RECEBIDA)));

        orchestrator.onEvent("OrcamentoGerado", osId, Map.of());

        verify(serviceOrderRepository).save(argThat(o ->
                o.getStatus() == OrderServiceStatusEnum.AGUARDANDO_APROVACAO));
    }

    @Test
    void pagamentoConfirmadoAdvancesToEntregueAndCompletes() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_PAGAMENTO)));
        when(serviceOrderRepository.findById(osId))
                .thenReturn(Optional.of(order(OrderServiceStatusEnum.PAGAMENTO_PENDENTE)));

        orchestrator.onEvent("PagamentoConfirmado", osId, Map.of());

        // PAGAMENTO_PENDENTE → PAGO → ENTREGUE: duas transições, estado final ENTREGUE.
        verify(serviceOrderRepository, times(2)).save(argThat(o ->
                o.getStatus() == OrderServiceStatusEnum.ENTREGUE));
        verify(sagaStateRepository).save(argThat(s -> s.getStep() == SagaStep.CONCLUIDA));
    }

    @Test
    void pagamentoFalhouKeepsOrderRetainedWithoutStockReversal() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_PAGAMENTO)));

        orchestrator.onEvent("PagamentoFalhou", osId, Map.of("reason", "cartao recusado"));

        // Compensação: sem comando de estorno de estoque; OS permanece como está.
        verify(commandPort, never()).send(any(), any(), any(), anyMap());
        verify(serviceOrderRepository, never()).save(any());
        verify(sagaStateRepository).save(argThat(s -> s.getStep() == SagaStep.COMPENSANDO));
    }

    @Test
    void onBudgetApprovedConfirmsReservationAndAdvancesToAprovado() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_APROVACAO)));
        when(serviceOrderRepository.findById(osId))
                .thenReturn(Optional.of(order(OrderServiceStatusEnum.AGUARDANDO_APROVACAO)));

        orchestrator.onBudgetApproved(osId);

        verify(serviceOrderRepository).save(argThat(o -> o.getStatus() == OrderServiceStatusEnum.APROVADO));
        verify(commandPort).send(eq("ComandoConfirmarReserva"), eq(osId), eq(osId), anyMap());
    }

    @Test
    void onBudgetRejectedReleasesStockAndMarksRecusada() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_APROVACAO)));
        when(serviceOrderRepository.findById(osId))
                .thenReturn(Optional.of(order(OrderServiceStatusEnum.AGUARDANDO_APROVACAO)));

        orchestrator.onBudgetRejected(osId, "muito caro");

        verify(serviceOrderRepository).save(argThat(o -> o.getStatus() == OrderServiceStatusEnum.RECUSADA));
        verify(commandPort).send(eq("ComandoLiberarReserva"), eq(osId), eq(osId), anyMap());
        verify(sagaStateRepository).save(argThat(s -> s.getStep() == SagaStep.COMPENSANDO));
    }

    @Test
    void reservaLiberadaFinalizaCompensacao() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.COMPENSANDO)));

        orchestrator.onEvent("ReservaLiberada", osId, Map.of());

        verify(sagaStateRepository).save(argThat(s -> s.getStep() == SagaStep.FALHOU));
    }

    @Test
    void reservaBaixadaClearsPendingWithoutOrderChangeOrCommand() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_PAGAMENTO)));

        orchestrator.onEvent("ReservaBaixada", osId, Map.of());

        verify(serviceOrderRepository, never()).save(any());
        verify(commandPort, never()).send(any(), any(), any(), anyMap());
        verify(sagaStateRepository).save(argThat(s -> !s.hasPendingCommand()));
    }

    @Test
    void orcamentoFalhouRetriesWhileUnderLimit() {
        SagaState saga = sagaAt(SagaStep.AGUARDANDO_ORCAMENTO);
        saga.awaitCommand("ComandoGerarOrcamento", "{\"parts\":[]}");
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(saga));

        orchestrator.onEvent("OrcamentoFalhou", osId, Map.of("reason", "erro transitório"));

        // Reenvia o comando (não compensa ainda) e incrementa a tentativa.
        verify(commandPort).send(eq("ComandoGerarOrcamento"), eq(osId), eq(osId), anyMap());
        verify(commandPort, never()).send(eq("ComandoLiberarReserva"), any(), any(), anyMap());
        verify(sagaStateRepository).save(argThat(s -> s.getRetryCount() == 1));
    }

    @Test
    void orcamentoFalhouCompensatesWhenRetriesExhausted() {
        SagaState saga = SagaState.reconstitute(1L, osId, SagaStep.AGUARDANDO_ORCAMENTO,
                List.of("RESERVA"), null, "ComandoGerarOrcamento", "{\"parts\":[]}", 3,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(saga));

        orchestrator.onEvent("OrcamentoFalhou", osId, Map.of("reason", "erro permanente"));

        // Esgotou: compensa liberando a reserva (estoque ainda ACTIVE) e marca COMPENSANDO.
        verify(commandPort).send(eq("ComandoLiberarReserva"), eq(osId), eq(osId), anyMap());
        verify(sagaStateRepository).save(argThat(s -> s.getStep() == SagaStep.COMPENSANDO));
    }

    @Test
    void resendTimedOutResendsPendingCommand() {
        SagaState saga = SagaState.reconstitute(1L, osId, SagaStep.AGUARDANDO_ORCAMENTO,
                List.of("RESERVA"), null, "ComandoGerarOrcamento", "{\"parts\":[]}", 0,
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now(), LocalDateTime.now());
        when(sagaStateRepository.findPendingOlderThan(any())).thenReturn(List.of(saga));

        orchestrator.resendTimedOut(LocalDateTime.now().minusSeconds(60));

        verify(commandPort).send(eq("ComandoGerarOrcamento"), eq(osId), eq(osId), anyMap());
        verify(sagaStateRepository).save(argThat(s -> s.getRetryCount() == 1));
    }

    @Test
    void resendTimedOutCompensatesWhenExhausted() {
        SagaState saga = SagaState.reconstitute(1L, osId, SagaStep.AGUARDANDO_ORCAMENTO,
                List.of("RESERVA"), null, "ComandoGerarOrcamento", "{\"parts\":[]}", 3,
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now(), LocalDateTime.now());
        when(sagaStateRepository.findPendingOlderThan(any())).thenReturn(List.of(saga));

        orchestrator.resendTimedOut(LocalDateTime.now().minusSeconds(60));

        verify(commandPort).send(eq("ComandoLiberarReserva"), eq(osId), eq(osId), anyMap());
        verify(sagaStateRepository).save(argThat(s -> s.getStep() == SagaStep.COMPENSANDO));
    }

    @Test
    void onEventWithoutSagaIsIgnored() {
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.empty());

        orchestrator.onEvent("ReservaConfirmada", osId, Map.of());

        verify(commandPort, never()).send(any(), any(), any(), anyMap());
    }

    @Test
    void unknownEventTypeIsIgnored() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_RESERVA)));

        orchestrator.onEvent("EventoDesconhecido", osId, Map.of());

        verify(commandPort, never()).send(any(), any(), any(), anyMap());
        verify(serviceOrderRepository, never()).save(any());
    }

    @Test
    void onBudgetApprovedWithoutSagaThrows() {
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> orchestrator.onBudgetApproved(osId));
    }

    @Test
    void onPaymentPendingCommandsCharge() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_PAGAMENTO)));

        orchestrator.onPaymentPending(osId);

        verify(commandPort).send(eq("ComandoCobrarPagamento"), eq(osId), eq(osId), anyMap());
    }

    @Test
    void onPaymentPendingWithoutSagaIsNoop() {
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.empty());

        orchestrator.onPaymentPending(osId); // OS avançada manualmente, sem Saga → não explode

        verify(commandPort, never()).send(any(), any(), any(), anyMap());
    }

    @Test
    void onCobrancaCriadaSendsPaymentLinkEmail() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_PAGAMENTO)));
        when(serviceOrderRepository.findById(osId))
                .thenReturn(Optional.of(order(OrderServiceStatusEnum.PAGAMENTO_PENDENTE)));

        orchestrator.onEvent("CobrancaCriada", osId, Map.of("checkoutUrl", "https://checkout/xyz"));

        verify(customerNotificationPort).notifyPaymentLink(any(), eq("https://checkout/xyz"));
    }
}
