package com.workbench.workshop.adapter.in.web.monitoring;

import com.workbench.workshop.application.monitoring.GetAverageExecutionTimeByIdUseCase;
import com.workbench.workshop.application.monitoring.GetAverageExecutionTimeUseCase;
import com.workbench.workshop.domain.monitoring.AverageTimeEnum;
import com.workbench.workshop.domain.monitoring.ServiceAverageTime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Relatório gerencial de tempo médio de execução dos serviços (paridade com o
 * monólito). Protegido por role ADMIN/TECHNICIAN (regra padrão do OS Service).
 */
@RestController
@RequestMapping("/monitoring")
@RequiredArgsConstructor
@Tag(name = "Monitoring", description = "Tempo médio de execução dos serviços")
public class MonitoringController {

    private final GetAverageExecutionTimeUseCase getAverageExecutionTimeUseCase;
    private final GetAverageExecutionTimeByIdUseCase getAverageExecutionTimeByIdUseCase;

    @PostMapping("/all")
    @Operation(summary = "Tempo médio de execução por tipo de serviço")
    public ResponseEntity<List<ServiceAverageTime>> getAverageExecutionTime(
            @RequestBody AverageTimeRequest request) {
        return ResponseEntity.ok(getAverageExecutionTimeUseCase.execute(request.timeUnit()));
    }

    @PostMapping("/by-id")
    @Operation(summary = "Tempo de execução de um serviço específico")
    public ResponseEntity<ServiceAverageTime> getAverageExecutionTimeById(
            @RequestBody AverageTimeByIdRequest request) {
        return ResponseEntity.ok(
                getAverageExecutionTimeByIdUseCase.execute(request.id(), request.timeUnit()));
    }

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
