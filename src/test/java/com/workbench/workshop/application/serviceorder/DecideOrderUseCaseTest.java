package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.saga.SagaOrchestrator;
import com.workbench.workshop.application.serviceorder.port.out.OrderMetricsPort;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.serviceorder.Decision;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DecideOrderUseCaseTest {

    private SagaOrchestrator sagaOrchestrator;
    private OrderMetricsPort metrics;
    private ServiceOrderRepository serviceOrderRepository;
    private DecideOrderUseCase useCase;

    private final UUID osId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sagaOrchestrator = mock(SagaOrchestrator.class);
        metrics = mock(OrderMetricsPort.class);
        serviceOrderRepository = mock(ServiceOrderRepository.class);
        useCase = new DecideOrderUseCase(sagaOrchestrator, metrics, serviceOrderRepository);
    }

    @Test
    void approvedDelegatesToOrchestrator() {
        useCase.execute(osId, Decision.APPROVED, null);
        verify(sagaOrchestrator).onBudgetApproved(osId);
    }

    @Test
    void rejectedDelegatesWithReason() {
        useCase.execute(osId, Decision.REJECTED, "muito caro");
        verify(sagaOrchestrator).onBudgetRejected(osId, "muito caro");
    }

    @Test
    void nullDecisionThrows() {
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(osId, null, null));
    }

    @Test
    void failureIsRecordedInMetrics() {
        doThrow(new IllegalStateException("boom")).when(sagaOrchestrator).onBudgetApproved(osId);
        assertThrows(IllegalStateException.class, () -> useCase.execute(osId, Decision.APPROVED, null));
        verify(metrics).processingFailed("decide_order");
    }

    // --- Ownership (cliente só decide a própria OS) ---

    private ServiceOrder orderOwnedBy(String cpfCnpj) {
        return ServiceOrder.reconstitute(osId, cpfCnpj, "ABC1234", List.of("TROCA_OLEO"),
                "[TROCA_OLEO]", com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum.AGUARDANDO_APROVACAO,
                null, null, null);
    }

    @Test
    void clientOwnerCanDecide() {
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(orderOwnedBy("52998224725")));
        useCase.execute(osId, Decision.APPROVED, null, "52998224725", true);
        verify(sagaOrchestrator).onBudgetApproved(osId);
    }

    @Test
    void clientNonOwnerIsRejected() {
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(orderOwnedBy("52998224725")));
        assertThrows(SecurityException.class,
                () -> useCase.execute(osId, Decision.APPROVED, null, "11111111111", true));
        verify(sagaOrchestrator, never()).onBudgetApproved(osId);
    }

    @Test
    void staffDecidesWithoutOwnershipCheck() {
        useCase.execute(osId, Decision.REJECTED, "orcamento alto", "admin@workbench.dev", false);
        verify(sagaOrchestrator).onBudgetRejected(osId, "orcamento alto");
        verify(serviceOrderRepository, never()).findById(osId);
    }
}
