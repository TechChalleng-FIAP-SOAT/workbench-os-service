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

    /** O que o SagaTimeoutScheduler faz: uma chamada (transação) por OS vencida. */
    private void resendAllTimedOut() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(60);
        for (UUID id : orchestrator.findTimedOut(threshold)) {
            orchestrator.resendTimedOut(id, threshold);
        }
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
        // Cada Saga nova abre uma rodada com sagaId próprio (não é mais o id da OS).
        verify(commandPort).send(eq("ComandoReservarEstoque"), argThat(id -> !osId.equals(id)), eq(osId), anyMap());
    }

    @Test
    void newDiagnosisRestartsAFailedSaga() {
        SagaState failed = sagaAt(SagaStep.FALHOU);
        failed.recordFailure("Estoque disponível insuficiente");
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(failed));

        orchestrator.start(order(OrderServiceStatusEnum.EM_DIAGNOSTICO),
                List.of(new SagaOrchestrator.ReserveItem("p1", BigDecimal.ONE)));

        verify(sagaStateRepository).save(argThat(s -> s == failed
                && s.getStep() == SagaStep.AGUARDANDO_RESERVA && s.getFailureReason() == null));
        verify(commandPort).send(eq("ComandoReservarEstoque"), eq(failed.getSagaId()), eq(osId), anyMap());
    }

    @Test
    void replyFromThePreviousRunIsIgnoredAfterANewDiagnosis() {
        SagaState saga = sagaAt(SagaStep.FALHOU);
        String previousRun = saga.getSagaId().toString();
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(saga));

        orchestrator.start(order(OrderServiceStatusEnum.EM_DIAGNOSTICO),
                List.of(new SagaOrchestrator.ReserveItem("p1", BigDecimal.ONE)));
        assertNotEquals(previousRun, saga.getSagaId().toString());

        // O SQS reentrega o ReservaFalhou da rodada anterior: não pode derrubar a nova.
        orchestrator.onEvent("ReservaFalhou", previousRun, osId, Map.of("reason", "faltou peça"));
        assertEquals(SagaStep.AGUARDANDO_RESERVA, saga.getStep());

        // A resposta da rodada atual segue normalmente.
        orchestrator.onEvent("ReservaConfirmada", saga.getSagaId().toString(), osId, Map.of("parts", List.of()));
        assertEquals(SagaStep.AGUARDANDO_ORCAMENTO, saga.getStep());
    }

    @Test
    void newDiagnosisWhileSagaRunsIsRejected() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_ORCAMENTO)));

        assertThrows(IllegalStateException.class,
                () -> orchestrator.start(order(OrderServiceStatusEnum.EM_DIAGNOSTICO), List.of()));
        verify(commandPort, never()).send(any(), any(), any(), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void stockReservedCommandsBudgetWithPartsAndLabor() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_RESERVA)));
        // Dois serviços na OS: um com MO > 0 (entra no orçamento), outro com MO 0 (ignorado).
        var s1 = new WorkshopService(UUID.randomUUID(), "TROCA_OLEO", osId, List.of());
        s1.defineLabor(new BigDecimal("150"), null);
        var s2 = new WorkshopService(UUID.randomUUID(), "REVISAO", osId, List.of());
        s2.defineLabor(BigDecimal.ZERO, null);
        when(serviceRepository.findByIdOS(osId)).thenReturn(List.of(s1, s2));

        var parts = List.of(Map.<String, Object>of("productId", "p1", "unitPrice", new BigDecimal("500")));
        orchestrator.onEvent("ReservaConfirmada", osId.toString(), osId, Map.of("parts", parts));

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
    @SuppressWarnings("unchecked")
    void stockReservedSendsCustomerDocumentToBilling() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_RESERVA)));
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(order(OrderServiceStatusEnum.EM_DIAGNOSTICO)));

        orchestrator.onEvent("ReservaConfirmada", osId.toString(), osId, Map.of("parts", List.of()));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(commandPort).send(eq("ComandoGerarOrcamento"), eq(osId), eq(osId), captor.capture());
        assertEquals("52998224725", captor.getValue().get("customerDocument"));
    }

    @Test
    void duplicateStockReservedIsIgnored() {
        // A Saga já passou da reserva: um ReservaConfirmada repetido/atrasado não pode
        // gerar um 2º orçamento nem voltar o passo.
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_APROVACAO)));

        orchestrator.onEvent("ReservaConfirmada", osId.toString(), osId, Map.of("parts", List.of()));

        verify(commandPort, never()).send(any(), any(), any(), anyMap());
        verify(sagaStateRepository, never()).save(any());
    }

    @Test
    void latePaymentConfirmedAfterCompletionIsIgnored() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.CONCLUIDA)));

        orchestrator.onEvent("PagamentoConfirmado", osId.toString(), osId, Map.of());

        verify(serviceOrderRepository, never()).save(any());
    }

    @Test
    void reservationFailedMarksFailed() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_RESERVA)));

        orchestrator.onEvent("ReservaFalhou", osId.toString(), osId, Map.of("reason", "sem estoque"));

        verify(sagaStateRepository).save(argThat(s -> s.getStep() == SagaStep.FALHOU));
        verify(commandPort, never()).send(any(), any(), any(), anyMap());
    }

    @Test
    void budgetGeneratedAdvancesOrderToAwaitingApproval() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_ORCAMENTO)));
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(order(OrderServiceStatusEnum.RECEBIDA)));

        orchestrator.onEvent("OrcamentoGerado", osId.toString(), osId, Map.of());

        verify(serviceOrderRepository).save(argThat(o ->
                o.getStatus() == OrderServiceStatusEnum.AGUARDANDO_APROVACAO));
    }

    @Test
    void paymentConfirmedAdvancesToDeliveredAndCompletes() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_PAGAMENTO)));
        when(serviceOrderRepository.findById(osId))
                .thenReturn(Optional.of(order(OrderServiceStatusEnum.PAGAMENTO_PENDENTE)));

        orchestrator.onEvent("PagamentoConfirmado", osId.toString(), osId, Map.of());

        // PAGAMENTO_PENDENTE → PAGO → ENTREGUE: duas transições, estado final ENTREGUE.
        verify(serviceOrderRepository, times(2)).save(argThat(o ->
                o.getStatus() == OrderServiceStatusEnum.ENTREGUE));
        verify(sagaStateRepository).save(argThat(s -> s.getStep() == SagaStep.CONCLUIDA));
    }

    @Test
    void paymentFailedKeepsOrderRetainedWithoutStockReversal() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_PAGAMENTO)));

        orchestrator.onEvent("PagamentoFalhou", osId.toString(), osId, Map.of("reason", "cartao recusado"));

        // Sem estorno de estoque; OS retida; a Saga segue aguardando pagamento com o motivo.
        verify(commandPort, never()).send(any(), any(), any(), anyMap());
        verify(serviceOrderRepository, never()).save(any());
        verify(sagaStateRepository).save(argThat(s -> s.getStep() == SagaStep.AGUARDANDO_PAGAMENTO
                && "cartao recusado".equals(s.getFailureReason())));
    }

    @Test
    void retryPaymentAfterRejectionCommandsNewCharge() {
        SagaState saga = sagaAt(SagaStep.AGUARDANDO_PAGAMENTO);
        saga.recordFailure("cartao recusado");
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(saga));

        orchestrator.retryPayment(osId);

        verify(commandPort).send(eq("ComandoCobrarPagamento"), eq(osId), eq(osId), anyMap());
        assertNull(saga.getFailureReason());
    }

    @Test
    void retryPaymentWithoutRejectionIsRefused() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_PAGAMENTO)));

        assertThrows(IllegalStateException.class, () -> orchestrator.retryPayment(osId));
        verify(commandPort, never()).send(any(), any(), any(), anyMap());
    }

    @Test
    void paymentConfirmedAfterRetryIsAccepted() {
        // Recusado → nova cobrança → pago: a Saga continua em AGUARDANDO_PAGAMENTO e aceita a confirmação.
        SagaState saga = sagaAt(SagaStep.AGUARDANDO_PAGAMENTO);
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(saga));
        when(serviceOrderRepository.findById(osId))
                .thenReturn(Optional.of(order(OrderServiceStatusEnum.PAGAMENTO_PENDENTE)));

        orchestrator.onEvent("PagamentoFalhou", osId.toString(), osId, Map.of("reason", "cancelado"));
        orchestrator.onEvent("PagamentoConfirmado", osId.toString(), osId, Map.of());

        assertEquals(SagaStep.CONCLUIDA, saga.getStep());
    }

    @Test
    void onBudgetApprovedConfirmsReservationAndAdvancesToApproved() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_APROVACAO)));
        when(serviceOrderRepository.findById(osId))
                .thenReturn(Optional.of(order(OrderServiceStatusEnum.AGUARDANDO_APROVACAO)));

        orchestrator.onBudgetApproved(osId);

        verify(serviceOrderRepository).save(argThat(o -> o.getStatus() == OrderServiceStatusEnum.APROVADO));
        verify(commandPort).send(eq("ComandoConfirmarReserva"), eq(osId), eq(osId), anyMap());
    }

    @Test
    void onBudgetRejectedReleasesStockAndMarksRejected() {
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
    void reservationReleasedFinishesCompensation() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.COMPENSANDO)));

        orchestrator.onEvent("ReservaLiberada", osId.toString(), osId, Map.of());

        verify(sagaStateRepository).save(argThat(s -> s.getStep() == SagaStep.FALHOU));
    }

    @Test
    void reservationConfirmedClearsPendingWithoutOrderChangeOrCommand() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_PAGAMENTO)));

        orchestrator.onEvent("ReservaBaixada", osId.toString(), osId, Map.of());

        verify(serviceOrderRepository, never()).save(any());
        verify(commandPort, never()).send(any(), any(), any(), anyMap());
        verify(sagaStateRepository).save(argThat(s -> !s.hasPendingCommand()));
    }

    @Test
    void budgetFailedRetriesWhileUnderLimit() {
        SagaState saga = sagaAt(SagaStep.AGUARDANDO_ORCAMENTO);
        saga.awaitCommand("ComandoGerarOrcamento", "{\"parts\":[]}");
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(saga));

        orchestrator.onEvent("OrcamentoFalhou", osId.toString(), osId, Map.of("reason", "erro transitório"));

        // Reenvia o comando (não compensa ainda) e incrementa a tentativa.
        verify(commandPort).send(eq("ComandoGerarOrcamento"), eq(osId), eq(osId), anyMap());
        verify(commandPort, never()).send(eq("ComandoLiberarReserva"), any(), any(), anyMap());
        verify(sagaStateRepository).save(argThat(s -> s.getRetryCount() == 1));
    }

    @Test
    void budgetFailedCompensatesWhenRetriesExhausted() {
        SagaState saga = SagaState.reconstitute(1L, osId, SagaStep.AGUARDANDO_ORCAMENTO,
                List.of("RESERVA"), null, "ComandoGerarOrcamento", "{\"parts\":[]}", 3,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(saga));

        orchestrator.onEvent("OrcamentoFalhou", osId.toString(), osId, Map.of("reason", "erro permanente"));

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
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(saga));

        resendAllTimedOut();

        verify(commandPort).send(eq("ComandoGerarOrcamento"), eq(osId), eq(osId), anyMap());
        verify(sagaStateRepository).save(argThat(s -> s.getRetryCount() == 1));
    }

    @Test
    void resendTimedOutCompensatesWhenExhausted() {
        SagaState saga = SagaState.reconstitute(1L, osId, SagaStep.AGUARDANDO_ORCAMENTO,
                List.of("RESERVA"), null, "ComandoGerarOrcamento", "{\"parts\":[]}", 3,
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now(), LocalDateTime.now());
        when(sagaStateRepository.findPendingOlderThan(any())).thenReturn(List.of(saga));
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(saga));

        resendAllTimedOut();

        verify(commandPort).send(eq("ComandoLiberarReserva"), eq(osId), eq(osId), anyMap());
        verify(sagaStateRepository).save(argThat(s -> s.getStep() == SagaStep.COMPENSANDO));
    }

    private SagaState exhausted(SagaStep step, String pendingCommand) {
        return SagaState.reconstitute(1L, osId, step, List.of("RESERVA", "ORCAMENTO", "APROVACAO"), null,
                pendingCommand, "{}", 3, LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void chargeTimeoutKeepsSagaAwaitingPaymentSoANewChargeIsAccepted() {
        SagaState saga = exhausted(SagaStep.AGUARDANDO_PAGAMENTO, "ComandoCobrarPagamento");
        when(sagaStateRepository.findPendingOlderThan(any())).thenReturn(List.of(saga));
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(saga));
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(saga));

        resendAllTimedOut();

        assertEquals(SagaStep.AGUARDANDO_PAGAMENTO, saga.getStep());
        assertFalse(saga.hasPendingCommand());
        assertEquals("timeout em ComandoCobrarPagamento", saga.getFailureReason());

        // Billing voltou: a nova cobrança é aceita...
        orchestrator.retryPayment(osId);
        verify(commandPort).send(eq("ComandoCobrarPagamento"), eq(osId), eq(osId), anyMap());

        // ...e a confirmação do pagamento conclui a Saga.
        when(serviceOrderRepository.findById(osId))
                .thenReturn(Optional.of(order(OrderServiceStatusEnum.PAGAMENTO_PENDENTE)));
        orchestrator.onEvent("PagamentoConfirmado", osId.toString(), osId, Map.of());
        assertEquals(SagaStep.CONCLUIDA, saga.getStep());
    }

    @Test
    void confirmReservationTimeoutKeepsSagaAwaitingPayment() {
        SagaState saga = exhausted(SagaStep.AGUARDANDO_PAGAMENTO, "ComandoConfirmarReserva");
        when(sagaStateRepository.findPendingOlderThan(any())).thenReturn(List.of(saga));
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(saga));

        resendAllTimedOut();

        assertEquals(SagaStep.AGUARDANDO_PAGAMENTO, saga.getStep());
        assertFalse(saga.hasPendingCommand());
        verify(commandPort, never()).send(any(), any(), any(), anyMap());
    }

    @Test
    void lateReservationConfirmedDoesNotClearPendingCharge() {
        SagaState saga = exhausted(SagaStep.AGUARDANDO_PAGAMENTO, "ComandoCobrarPagamento");
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(saga));

        orchestrator.onEvent("ReservaBaixada", osId.toString(), osId, Map.of());

        assertEquals("ComandoCobrarPagamento", saga.getPendingCommand());
    }

    @Test
    void failedSagaLeavesNothingPendingForTheTimeoutJob() {
        SagaState saga = exhausted(SagaStep.COMPENSANDO, "ComandoLiberarReserva");
        when(sagaStateRepository.findPendingOlderThan(any())).thenReturn(List.of(saga));
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(saga));

        resendAllTimedOut();

        assertEquals(SagaStep.FALHOU, saga.getStep());
        assertFalse(saga.hasPendingCommand());
    }

    @Test
    void timedOutSagaThatGotItsReplyMeanwhileIsNotResent() {
        SagaState answered = sagaAt(SagaStep.AGUARDANDO_ORCAMENTO); // sem comando pendente
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(answered));

        orchestrator.resendTimedOut(osId, LocalDateTime.now());

        verify(commandPort, never()).send(any(), any(), any(), anyMap());
        verify(sagaStateRepository, never()).save(any());
    }

    @Test
    void newSagaRunsToDeliveredWhenEveryReplyCarriesItsRun() {
        // Caminho real: Saga nova (sagaId aleatório, não o id da OS) da reserva ao pagamento.
        // Pegou a regressão em que o billing publicava o pagamento com o id da OS.
        SagaState[] stored = new SagaState[1];
        when(sagaStateRepository.save(any())).thenAnswer(i -> stored[0] = i.getArgument(0));
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenAnswer(i -> Optional.ofNullable(stored[0]));
        ServiceOrder order = order(OrderServiceStatusEnum.EM_DIAGNOSTICO);
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(order));

        orchestrator.start(order, List.of(new SagaOrchestrator.ReserveItem("p1", BigDecimal.ONE)));
        String run = stored[0].getSagaId().toString();
        assertNotEquals(osId.toString(), run);

        orchestrator.onEvent("ReservaConfirmada", run, osId, Map.of("parts", List.of()));
        orchestrator.onEvent("OrcamentoGerado", run, osId, Map.of());
        orchestrator.onBudgetApproved(osId);
        orchestrator.onEvent("ReservaBaixada", run, osId, Map.of());
        order.advanceTo(OrderServiceStatusEnum.EM_EXECUCAO);
        order.advanceTo(OrderServiceStatusEnum.FINALIZADA);
        order.advanceTo(OrderServiceStatusEnum.PAGAMENTO_PENDENTE);
        orchestrator.onPaymentPending(osId);
        orchestrator.onEvent("CobrancaCriada", run, osId, Map.of("checkoutUrl", "http://checkout"));

        // Pagamento publicado com o id da OS (o bug): descartado, a OS não anda.
        orchestrator.onEvent("PagamentoConfirmado", osId.toString(), osId, Map.of());
        assertEquals(OrderServiceStatusEnum.PAGAMENTO_PENDENTE, order.getStatus());

        // Com o sagaId da rodada (o que o billing grava na cobrança): a Saga termina.
        orchestrator.onEvent("PagamentoConfirmado", run, osId, Map.of());
        assertEquals(SagaStep.CONCLUIDA, stored[0].getStep());
        assertEquals(OrderServiceStatusEnum.ENTREGUE, order.getStatus());
    }

    @Test
    void onEventWithoutSagaIsIgnored() {
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.empty());

        orchestrator.onEvent("ReservaConfirmada", osId.toString(), osId, Map.of());

        verify(commandPort, never()).send(any(), any(), any(), anyMap());
    }

    @Test
    void unknownEventTypeIsIgnored() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_RESERVA)));

        orchestrator.onEvent("EventoDesconhecido", osId.toString(), osId, Map.of());

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
    void onChargeCreatedSendsPaymentLinkEmail() {
        when(sagaStateRepository.findByServiceOrderId(osId))
                .thenReturn(Optional.of(sagaAt(SagaStep.AGUARDANDO_PAGAMENTO)));
        when(serviceOrderRepository.findById(osId))
                .thenReturn(Optional.of(order(OrderServiceStatusEnum.PAGAMENTO_PENDENTE)));

        orchestrator.onEvent("CobrancaCriada", osId.toString(), osId, Map.of("checkoutUrl", "https://checkout/xyz"));

        verify(customerNotificationPort).notifyPaymentLink(any(), eq("https://checkout/xyz"));
    }
}
