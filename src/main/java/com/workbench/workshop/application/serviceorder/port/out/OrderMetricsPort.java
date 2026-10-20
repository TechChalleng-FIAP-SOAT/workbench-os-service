package com.workbench.workshop.application.serviceorder.port.out;

import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;

import java.time.LocalDateTime;

/**
 * Porta de métricas de negócio. Introduzida na extração (Fase 4) para remover o
 * vazamento application → infrastructure que existia no monólito, onde os use
 * cases dependiam diretamente de ServiceOrderMetrics (infraestrutura Micrometer).
 */
public interface OrderMetricsPort {
    void created();
    void processingFailed(String operation);
    void integrationFailed(String integration);
    void recordTimeInStatus(OrderServiceStatusEnum previousStatus, LocalDateTime enteredAt);
}
