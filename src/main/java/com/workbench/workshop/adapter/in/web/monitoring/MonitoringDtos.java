package com.workbench.workshop.adapter.in.web.monitoring;

import com.workbench.workshop.domain.monitoring.AverageTimeEnum;

import java.util.UUID;

/** DTOs de entrada do relatório de tempo médio de execução. */
public final class MonitoringDtos {

    private MonitoringDtos() {}

    /** timeUnit: SECONDS | MINUTES | HOURS (default HOURS). */
    public record AverageTimeRequest(AverageTimeEnum timeUnit) {
        public AverageTimeRequest {
            if (timeUnit == null) {
                timeUnit = AverageTimeEnum.HOURS;
            }
        }
    }

    public record AverageTimeByIdRequest(UUID id, AverageTimeEnum timeUnit) {
        public AverageTimeByIdRequest {
            if (timeUnit == null) {
                timeUnit = AverageTimeEnum.HOURS;
            }
        }
    }
}
