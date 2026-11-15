package com.workbench.workshop.adapter.in.web.serviceorder;

import com.workbench.workshop.domain.saga.SagaState;

import java.time.LocalDateTime;
import java.util.List;

/** Estado da Saga de uma OS, para acompanhamento e diagnóstico de falhas. */
public record OrderSagaResponse(
        String step,
        List<String> completedSteps,
        String failureReason,
        String pendingCommand,
        int retryCount,
        LocalDateTime updatedAt
) {
    public static OrderSagaResponse from(SagaState saga) {
        return new OrderSagaResponse(saga.getStep().name(), List.copyOf(saga.getCompletedSteps()),
                saga.getFailureReason(), saga.getPendingCommand(), saga.getRetryCount(), saga.getUpdatedAt());
    }
}
