package com.workbench.workshop.infrastructure.persistence.saga;

import com.workbench.workshop.domain.saga.SagaStep;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity @Table(name = "saga_state")
public class SagaStateEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "service_order_id", nullable = false, unique = true)
    private UUID serviceOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaStep step;

    /** Passos concluídos, serializados como CSV simples. */
    @Column(name = "completed_steps", length = 1000)
    private String completedSteps;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "pending_command", length = 100)
    private String pendingCommand;

    @Column(name = "pending_payload", length = 4000)
    private String pendingPayloadJson;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_command_at")
    private LocalDateTime lastCommandAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
