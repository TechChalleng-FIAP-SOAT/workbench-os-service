package com.workbench.workshop.adapter.out.persistence.saga;

import com.workbench.workshop.domain.saga.SagaState;
import com.workbench.workshop.domain.saga.SagaStep;
import com.workbench.workshop.infrastructure.persistence.saga.SagaStateEntity;
import com.workbench.workshop.infrastructure.persistence.saga.SagaStateJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SagaStatePersistenceAdapterTest {

    private SagaStateJpaRepository jpaRepository;
    private SagaStatePersistenceAdapter adapter;

    private final UUID osId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jpaRepository = mock(SagaStateJpaRepository.class);
        adapter = new SagaStatePersistenceAdapter(jpaRepository);
        when(jpaRepository.save(any(SagaStateEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void saveCreatesNewEntityWhenAbsent() {
        when(jpaRepository.findByServiceOrderId(osId)).thenReturn(Optional.empty());

        SagaState saga = SagaState.start(osId);
        saga.advanceTo(SagaStep.AGUARDANDO_RESERVA, "INICIADA");

        SagaState result = adapter.save(saga);

        assertEquals(osId, result.getServiceOrderId());
        assertEquals(SagaStep.AGUARDANDO_RESERVA, result.getStep());
        verify(jpaRepository).save(argThat(e ->
                e.getServiceOrderId().equals(osId)
                        && e.getStep() == SagaStep.AGUARDANDO_RESERVA
                        && "INICIADA".equals(e.getCompletedSteps())));
    }

    @Test
    void saveUpdatesExistingEntity() {
        SagaStateEntity existing = SagaStateEntity.builder()
                .id(UUID.randomUUID())
                .serviceOrderId(osId)
                .step(SagaStep.AGUARDANDO_RESERVA)
                .build();
        when(jpaRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(existing));

        SagaState saga = SagaState.reconstitute(1L, osId, SagaStep.AGUARDANDO_ORCAMENTO,
                java.util.List.of("RESERVA"), null, LocalDateTime.now(), LocalDateTime.now());

        adapter.save(saga);

        verify(jpaRepository).save(argThat(e ->
                e.getStep() == SagaStep.AGUARDANDO_ORCAMENTO && "RESERVA".equals(e.getCompletedSteps())));
    }

    @Test
    void findByServiceOrderIdMapsCsvToList() {
        SagaStateEntity entity = SagaStateEntity.builder()
                .id(UUID.randomUUID())
                .serviceOrderId(osId)
                .step(SagaStep.AGUARDANDO_APROVACAO)
                .completedSteps("RESERVA,ORCAMENTO")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(jpaRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(entity));

        SagaState saga = adapter.findByServiceOrderId(osId).orElseThrow();

        assertTrue(saga.hasCompleted("RESERVA"));
        assertTrue(saga.hasCompleted("ORCAMENTO"));
        assertEquals(SagaStep.AGUARDANDO_APROVACAO, saga.getStep());
    }

    @Test
    void findByServiceOrderIdHandlesBlankCompletedSteps() {
        SagaStateEntity entity = SagaStateEntity.builder()
                .id(UUID.randomUUID())
                .serviceOrderId(osId)
                .step(SagaStep.INICIADA)
                .completedSteps("")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(jpaRepository.findByServiceOrderId(osId)).thenReturn(Optional.of(entity));

        SagaState saga = adapter.findByServiceOrderId(osId).orElseThrow();

        assertFalse(saga.hasCompleted("RESERVA"));
    }

    @Test
    void findByServiceOrderIdReturnsEmptyWhenAbsent() {
        when(jpaRepository.findByServiceOrderId(osId)).thenReturn(Optional.empty());
        assertTrue(adapter.findByServiceOrderId(osId).isEmpty());
    }
}
