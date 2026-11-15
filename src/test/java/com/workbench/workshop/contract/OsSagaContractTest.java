package com.workbench.workshop.contract;

import com.workbench.workshop.application.saga.SagaMessages;
import com.workbench.workshop.application.saga.SagaOrchestrator;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Contrato da Saga do lado do orquestrador (OS Service): nomes de mensagem, os
 * comandos que ele PUBLICA (campos obrigatórios presentes) e os eventos que ele
 * CONSOME (o exemplo do contrato é aceito e faz a Saga avançar).
 */
class OsSagaContractTest {

    private static final String SERVICE = "os-service";
    private final SagaContract contract = SagaContract.load();
    private final UUID osId = UUID.randomUUID();

    private SagaStateRepository sagaStateRepository;
    private ServiceOrderRepository serviceOrderRepository;
    private ServiceRepository serviceRepository;
    private SagaCommandPort commandPort;
    private CustomerNotificationPort customerNotificationPort;
    private SagaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        sagaStateRepository = mock(SagaStateRepository.class);
        serviceOrderRepository = mock(ServiceOrderRepository.class);
        serviceRepository = mock(ServiceRepository.class);
        commandPort = mock(SagaCommandPort.class);
        customerNotificationPort = mock(CustomerNotificationPort.class);
        orchestrator = new SagaOrchestrator(sagaStateRepository, serviceOrderRepository,
                serviceRepository, commandPort, customerNotificationPort);
        ReflectionTestUtils.setField(orchestrator, "maxRetries", 3);
        when(sagaStateRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(serviceOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void messageNamesMatchContract() {
        var names = SagaContract.constantValues(SagaMessages.class);
        assertTrue(contract.typesInvolving(SERVICE).containsAll(names),
                "SagaMessages tem nome fora do contrato: " + names);
        assertTrue(names.containsAll(contract.typesImplementedBy(SERVICE)),
                "SagaMessages não cobre o contrato: " + contract.typesImplementedBy(SERVICE));
    }

    // ---------- comandos publicados ----------

    @Test
    void reserveStockCommandConforms() {
        orchestrator.start(order(OrderServiceStatusEnum.EM_DIAGNOSTICO),
                List.of(new SagaOrchestrator.ReserveItem("p-1", new BigDecimal("2"))));

        assertCommandConforms(SagaMessages.RESERVE_STOCK);
    }

    @Test
    void stockReservedFromContractProducesConformingGenerateBudget() {
        // Encadeia o contrato: o ReservaConfirmada do Execution vira o ComandoGerarOrcamento do Billing.
        sagaAt(SagaStep.AGUARDANDO_RESERVA);
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(order(OrderServiceStatusEnum.EM_DIAGNOSTICO)));
        var service = new WorkshopService(UUID.randomUUID(), "TROCA_OLEO", osId, List.of());
        service.defineLabor(new BigDecimal("150.00"), null);
        when(serviceRepository.findByIdOS(osId)).thenReturn(List.of(service));

        orchestrator.onEvent(SagaMessages.STOCK_RESERVED, osId.toString(), osId, contract.example(SagaMessages.STOCK_RESERVED));

        assertCommandConforms(SagaMessages.GENERATE_BUDGET);
    }

    @Test
    void approvalAndRejectionCommandsConform() {
        sagaAt(SagaStep.AGUARDANDO_APROVACAO);
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(order(OrderServiceStatusEnum.AGUARDANDO_APROVACAO)));
        orchestrator.onBudgetApproved(osId);
        assertCommandConforms(SagaMessages.CONFIRM_RESERVATION);

        sagaAt(SagaStep.AGUARDANDO_APROVACAO);
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(order(OrderServiceStatusEnum.AGUARDANDO_APROVACAO)));
        orchestrator.onBudgetRejected(osId, "muito caro");
        assertCommandConforms(SagaMessages.RELEASE_RESERVATION);
    }

    @Test
    void chargePaymentCommandConforms() {
        sagaAt(SagaStep.AGUARDANDO_PAGAMENTO);

        orchestrator.onPaymentPending(osId);

        assertCommandConforms(SagaMessages.CHARGE_PAYMENT);
    }

    // ---------- eventos consumidos ----------

    @Test
    void consumesReservationFailed() {
        assertEventAccepted(SagaStep.AGUARDANDO_RESERVA, SagaMessages.RESERVATION_FAILED, SagaStep.FALHOU);
    }

    @Test
    void consumesBudgetGenerated() {
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(order(OrderServiceStatusEnum.EM_DIAGNOSTICO)));
        assertEventAccepted(SagaStep.AGUARDANDO_ORCAMENTO, SagaMessages.BUDGET_GENERATED, SagaStep.AGUARDANDO_APROVACAO);
    }

    @Test
    void consumesReservationConfirmedAndReleased() {
        assertEventAccepted(SagaStep.AGUARDANDO_PAGAMENTO, SagaMessages.RESERVATION_CONFIRMED, SagaStep.AGUARDANDO_PAGAMENTO);
        assertEventAccepted(SagaStep.COMPENSANDO, SagaMessages.RESERVATION_RELEASED, SagaStep.FALHOU);
    }

    @Test
    void consumesChargeCreatedAndSendsPaymentLink() {
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(order(OrderServiceStatusEnum.PAGAMENTO_PENDENTE)));

        assertEventAccepted(SagaStep.AGUARDANDO_PAGAMENTO, SagaMessages.CHARGE_CREATED, SagaStep.AGUARDANDO_PAGAMENTO);

        String checkoutUrl = (String) contract.example(SagaMessages.CHARGE_CREATED).get("checkoutUrl");
        verify(customerNotificationPort).notifyPaymentLink(any(), eq(checkoutUrl));
    }

    @Test
    void consumesPaymentConfirmedAndFailed() {
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(order(OrderServiceStatusEnum.PAGAMENTO_PENDENTE)));
        assertEventAccepted(SagaStep.AGUARDANDO_PAGAMENTO, SagaMessages.PAYMENT_CONFIRMED, SagaStep.CONCLUIDA);
        assertEventAccepted(SagaStep.AGUARDANDO_PAGAMENTO, SagaMessages.PAYMENT_FAILED, SagaStep.AGUARDANDO_PAGAMENTO);
    }

    // ---------- apoio ----------

    private void assertEventAccepted(SagaStep from, String type, SagaStep expectedStep) {
        clearInvocations(sagaStateRepository);
        sagaAt(from);

        orchestrator.onEvent(type, osId.toString(), osId, contract.example(type));

        ArgumentCaptor<SagaState> saved = ArgumentCaptor.forClass(SagaState.class);
        verify(sagaStateRepository, atLeastOnce()).save(saved.capture());
        assertEquals(expectedStep, saved.getValue().getStep(), "exemplo do contrato de " + type + " não foi aceito");
    }

    @SuppressWarnings("unchecked")
    private void assertCommandConforms(String type) {
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        // O sagaId é o da rodada (nova Saga = id novo); o contrato confere o payload.
        verify(commandPort, atLeastOnce()).send(eq(type), any(UUID.class), eq(osId), payload.capture());
        assertEquals(List.of(), contract.missingFields(type, payload.getValue()),
                "campos obrigatórios ausentes em " + type);
    }

    private void sagaAt(SagaStep step) {
        when(sagaStateRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(
                SagaState.reconstitute(1L, osId, step, List.of(), null, LocalDateTime.now(), LocalDateTime.now())));
    }

    private ServiceOrder order(OrderServiceStatusEnum status) {
        return ServiceOrder.reconstitute(osId, "52998224725", "ABC1234", List.of("TROCA_OLEO"),
                "[TROCA_OLEO]", status, null, LocalDateTime.now(), LocalDateTime.now());
    }
}
