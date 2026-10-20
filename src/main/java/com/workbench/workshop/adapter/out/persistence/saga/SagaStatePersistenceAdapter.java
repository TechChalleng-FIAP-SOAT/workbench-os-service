package com.workbench.workshop.adapter.out.persistence.saga;

import com.workbench.workshop.application.saga.port.out.SagaStateRepository;
import com.workbench.workshop.domain.saga.SagaState;
import com.workbench.workshop.domain.saga.SagaStep;
import com.workbench.workshop.infrastructure.persistence.saga.SagaStateEntity;
import com.workbench.workshop.infrastructure.persistence.saga.SagaStateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SagaStatePersistenceAdapter implements SagaStateRepository {

    private final SagaStateJpaRepository jpaRepository;

    @Override
    public SagaState save(SagaState s) {
        SagaStateEntity entity = jpaRepository.findByServiceOrderId(s.getServiceOrderId())
                .orElseGet(() -> SagaStateEntity.builder()
                        .id(UUID.randomUUID())
                        .serviceOrderId(s.getServiceOrderId())
                        .build());
        entity.setStep(s.getStep());
        entity.setCompletedSteps(String.join(",", s.getCompletedSteps()));
        entity.setFailureReason(s.getFailureReason());
        entity.setPendingCommand(s.getPendingCommand());
        entity.setPendingPayloadJson(s.getPendingPayloadJson());
        entity.setRetryCount(s.getRetryCount());
        entity.setLastCommandAt(s.getLastCommandAt());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<SagaState> findByServiceOrderId(UUID serviceOrderId) {
        return jpaRepository.findByServiceOrderId(serviceOrderId).map(this::toDomain);
    }

    @Override
    public List<SagaState> findPendingOlderThan(java.time.LocalDateTime threshold) {
        return jpaRepository.findByPendingCommandIsNotNullAndLastCommandAtBefore(threshold)
                .stream().map(this::toDomain).toList();
    }

    private SagaState toDomain(SagaStateEntity e) {
        List<String> steps = (e.getCompletedSteps() == null || e.getCompletedSteps().isBlank())
                ? List.of()
                : List.of(e.getCompletedSteps().split(","));
        return SagaState.reconstitute(
                longHash(e.getId()), e.getServiceOrderId(), e.getStep(),
                steps, e.getFailureReason(),
                e.getPendingCommand(), e.getPendingPayloadJson(), e.getRetryCount(), e.getLastCommandAt(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    // O domínio usa Long id apenas como referência opcional; o id real é UUID.
    private Long longHash(UUID id) {
        return id == null ? null : (long) id.hashCode();
    }
}
