package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.saga.SagaOrchestrator;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.saga.SagaState;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FindOrderSagaUseCaseTest {

    private final ServiceOrderRepository orders = mock(ServiceOrderRepository.class);
    private final SagaOrchestrator orchestrator = mock(SagaOrchestrator.class);
    private final FindOrderSagaUseCase useCase = new FindOrderSagaUseCase(orders, orchestrator);
    private final UUID id = UUID.randomUUID();

    @Test
    void returnsTheSagaOfTheOrder() {
        SagaState saga = SagaState.start(id);
        when(orders.findById(id)).thenReturn(Optional.of(mock(ServiceOrder.class)));
        when(orchestrator.findSaga(id)).thenReturn(Optional.of(saga));

        assertSame(saga, useCase.execute(id));
    }

    @Test
    void orderWithoutDiagnosisHasNoSaga() {
        when(orders.findById(id)).thenReturn(Optional.of(mock(ServiceOrder.class)));
        when(orchestrator.findSaga(id)).thenReturn(Optional.empty());

        NoSuchElementException ex = assertThrows(NoSuchElementException.class, () -> useCase.execute(id));
        assertTrue(ex.getMessage().contains("ainda não tem Saga"));
    }

    @Test
    void unknownOrderIs404() {
        when(orders.findById(id)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> useCase.execute(id));
        verifyNoInteractions(orchestrator);
    }
}
