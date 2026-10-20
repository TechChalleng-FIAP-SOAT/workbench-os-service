package com.workbench.workshop.infrastructure.persistence.saga;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SagaStateJpaRepository extends JpaRepository<SagaStateEntity, UUID> {
    Optional<SagaStateEntity> findByServiceOrderId(UUID serviceOrderId);

    List<SagaStateEntity> findByPendingCommandIsNotNullAndLastCommandAtBefore(java.time.LocalDateTime threshold);
}
