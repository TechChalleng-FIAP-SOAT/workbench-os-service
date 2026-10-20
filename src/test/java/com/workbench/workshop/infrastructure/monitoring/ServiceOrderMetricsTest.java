package com.workbench.workshop.infrastructure.monitoring;

import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ServiceOrderMetricsTest {

    private MeterRegistry registry;
    private ServiceOrderMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new ServiceOrderMetrics(registry);
    }

    @Test
    void createdIncrementsCounter() {
        metrics.created();
        assertEquals(1.0, registry.get("workshop.service_orders.created").counter().count());
    }

    @Test
    void processingFailedTagsOperation() {
        metrics.processingFailed("create");
        assertEquals(1.0, registry.get("workshop.service_orders.processing.failed")
                .tag("operation", "create").counter().count());
    }

    @Test
    void integrationFailedTagsIntegration() {
        metrics.integrationFailed("aws_sns");
        assertEquals(1.0, registry.get("workshop.integrations.failed")
                .tag("integration", "aws_sns").counter().count());
    }

    @Test
    void recordsTimeForMappedStage() {
        metrics.recordTimeInStatus(OrderServiceStatusEnum.EM_DIAGNOSTICO, LocalDateTime.now().minusSeconds(5));
        assertEquals(1L, registry.get("workshop.service_orders.status.duration")
                .tag("status", "diagnostico").timer().count());
    }

    @Test
    void ignoresUnmappedStage() {
        metrics.recordTimeInStatus(OrderServiceStatusEnum.RECEBIDA, LocalDateTime.now().minusSeconds(5));
        assertNull(registry.find("workshop.service_orders.status.duration").timer());
    }

    @Test
    void ignoresNullEnteredAt() {
        metrics.recordTimeInStatus(OrderServiceStatusEnum.EM_EXECUCAO, null);
        assertNull(registry.find("workshop.service_orders.status.duration").timer());
    }
}
