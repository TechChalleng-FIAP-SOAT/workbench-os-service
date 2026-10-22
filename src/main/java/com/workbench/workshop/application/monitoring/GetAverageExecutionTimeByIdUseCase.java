package com.workbench.workshop.application.monitoring;

import com.workbench.workshop.application.service.port.out.ServiceRepository;
import com.workbench.workshop.domain.monitoring.AverageTimeEnum;
import com.workbench.workshop.domain.monitoring.ServiceAverageTime;
import com.workbench.workshop.domain.service.ServiceStatusEnum;
import com.workbench.workshop.domain.service.Status;
import com.workbench.workshop.domain.service.WorkshopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Tempo de execução de um serviço específico (paridade com o monólito). */
@Service
@RequiredArgsConstructor
public class GetAverageExecutionTimeByIdUseCase {

    private final ServiceRepository serviceRepository;

    public ServiceAverageTime execute(UUID id, AverageTimeEnum timeUnit) {
        WorkshopService service = serviceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Servico nao encontrado. ID: " + id));

        if (service.getServiceTypeName() == null) {
            return new ServiceAverageTime("Unknown", 0.0);
        }

        double executionTime = calculateExecutionTime(service, timeUnit);
        return new ServiceAverageTime(service.getServiceTypeName(), executionTime);
    }

    private double calculateExecutionTime(WorkshopService service, AverageTimeEnum timeUnit) {
        List<Status> statuses = service.getServiceStatus();
        if (statuses == null) {
            return 0.0;
        }
        boolean hasCompleted = statuses.stream()
                .anyMatch(status -> status.getStatus() == ServiceStatusEnum.DONE);
        if (!hasCompleted) return 0.0;

        LocalDateTime doingTime = findEarliestStatusTime(statuses, ServiceStatusEnum.DOING);
        LocalDateTime doneTime = findEarliestStatusTime(statuses, ServiceStatusEnum.DONE);

        if (doingTime != null && doneTime != null && doneTime.isAfter(doingTime)) {
            ZoneId zone = ZoneId.of("America/Sao_Paulo");
            return timeUnit.calculate(Duration.between(
                    doingTime.atZone(zone).toInstant(), doneTime.atZone(zone).toInstant()));
        }
        return 0.0;
    }

    private LocalDateTime findEarliestStatusTime(List<Status> statuses, ServiceStatusEnum targetStatus) {
        return statuses.stream()
                .filter(status -> status.getStatus() == targetStatus)
                .map(Status::getChangedAt)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }
}
