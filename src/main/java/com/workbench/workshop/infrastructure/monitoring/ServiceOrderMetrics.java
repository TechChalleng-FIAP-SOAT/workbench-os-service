package com.workbench.workshop.infrastructure.monitoring;

import com.workbench.workshop.application.serviceorder.port.out.OrderMetricsPort;
import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Adapter de métricas de negócio (Micrometer/Datadog). Implementa OrderMetricsPort,
 * invertendo a dependência: a camada application fala apenas com a porta, e esta
 * infraestrutura a implementa. Remove o vazamento application → infrastructure que
 * existia no monólito.
 */
@Component
public class ServiceOrderMetrics implements OrderMetricsPort {
    private final MeterRegistry registry;

    public ServiceOrderMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void created() {
        Counter.builder("workshop.service_orders.created").description("Service orders created")
                .register(registry).increment();
    }

    @Override
    public void processingFailed(String operation) {
        Counter.builder("workshop.service_orders.processing.failed")
                .description("Failed service-order processing operations")
                .tag("operation", operation).register(registry).increment();
    }

    @Override
    public void integrationFailed(String integration) {
        Counter.builder("workshop.integrations.failed").description("Failed external integrations")
                .tag("integration", integration).register(registry).increment();
    }

    @Override
    public void recordTimeInStatus(OrderServiceStatusEnum previousStatus, LocalDateTime enteredAt) {
        String stage = switch (previousStatus) {
            case EM_DIAGNOSTICO -> "diagnostico";
            case EM_EXECUCAO -> "execucao";
            case FINALIZADA -> "finalizacao";
            default -> null;
        };
        if (stage != null && enteredAt != null) {
            Duration duration = Duration.between(enteredAt, LocalDateTime.now());
            if (!duration.isNegative()) {
                Timer.builder("workshop.service_orders.status.duration")
                        .description("Time an order spends in each operational stage")
                        .tag("status", stage).register(registry).record(duration);
            }
        }
    }
}
