package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.saga.SagaOrchestrator;
import com.workbench.workshop.application.serviceorder.port.out.OrderMetricsPort;
import com.workbench.workshop.domain.serviceorder.Decision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DecideOrderUseCaseTest {

    private SagaOrchestrator sagaOrchestrator;
    private OrderMetricsPort metrics;
    private DecideOrderUseCase useCase;

    private final UUID osId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sagaOrchestrator = mock(SagaOrchestrator.class);
        metrics = mock(OrderMetricsPort.class);
        useCase = new DecideOrderUseCase(sagaOrchestrator, metrics);
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
}
