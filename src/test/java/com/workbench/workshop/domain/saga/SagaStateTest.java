package com.workbench.workshop.domain.saga;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SagaStateTest {

    @Test
    void failedSagaRestartsCleanForANewDiagnosis() {
        SagaState saga = SagaState.reconstitute(1L, UUID.randomUUID(), SagaStep.FALHOU,
                List.of("INICIADA"), "Estoque disponível insuficiente", "ComandoReservarEstoque", "{}",
                2, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now());

        saga.restart();

        assertEquals(SagaStep.INICIADA, saga.getStep());
        assertTrue(saga.getCompletedSteps().isEmpty());
        assertNull(saga.getFailureReason());
        assertFalse(saga.hasPendingCommand());
        assertEquals(0, saga.getRetryCount());
    }

    @Test
    void sagaInProgressCannotRestart() {
        SagaState saga = SagaState.reconstitute(1L, UUID.randomUUID(), SagaStep.AGUARDANDO_APROVACAO,
                List.of("INICIADA", "ESTOQUE_RESERVADO"), null, LocalDateTime.now(), LocalDateTime.now());

        IllegalStateException ex = assertThrows(IllegalStateException.class, saga::restart);
        assertTrue(ex.getMessage().contains("AGUARDANDO_APROVACAO"));
    }
}
