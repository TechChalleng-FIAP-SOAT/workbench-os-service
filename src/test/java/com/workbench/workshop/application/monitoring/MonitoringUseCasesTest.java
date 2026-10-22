package com.workbench.workshop.application.monitoring;

import com.workbench.workshop.application.service.port.out.ServiceRepository;
import com.workbench.workshop.domain.monitoring.AverageTimeEnum;
import com.workbench.workshop.domain.monitoring.ServiceAverageTime;
import com.workbench.workshop.domain.service.ServiceStatusEnum;
import com.workbench.workshop.domain.service.Status;
import com.workbench.workshop.domain.service.WorkshopService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonitoringUseCasesTest {

    private ServiceRepository serviceRepository;
    private GetAverageExecutionTimeUseCase allUseCase;
    private GetAverageExecutionTimeByIdUseCase byIdUseCase;

    @BeforeEach
    void setUp() {
        serviceRepository = mock(ServiceRepository.class);
        allUseCase = new GetAverageExecutionTimeUseCase(serviceRepository);
        byIdUseCase = new GetAverageExecutionTimeByIdUseCase(serviceRepository);
    }

    private WorkshopService service(UUID id, String type, LocalDateTime doing, LocalDateTime done) {
        var statuses = new java.util.ArrayList<Status>();
        statuses.add(new Status(ServiceStatusEnum.TO_DO, doing.minusMinutes(5)));
        if (doing != null) statuses.add(new Status(ServiceStatusEnum.DOING, doing));
        if (done != null) statuses.add(new Status(ServiceStatusEnum.DONE, done));
        return new WorkshopService(id, type, UUID.randomUUID(), statuses);
    }

    @Test
    void averageByTypeComputesDurationBetweenDoingAndDone() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 8, 0);
        var s1 = service(UUID.randomUUID(), "TROCA_OLEO", base, base.plusHours(2));
        var s2 = service(UUID.randomUUID(), "TROCA_OLEO", base, base.plusHours(4));
        when(serviceRepository.findAll()).thenReturn(List.of(s1, s2));

        List<ServiceAverageTime> result = allUseCase.execute(AverageTimeEnum.HOURS);

        assertEquals(1, result.size());
        assertEquals("TROCA_OLEO", result.get(0).getServiceTypeName());
        assertEquals(3.0, result.get(0).getAverageTime(), 0.001); // média de 2h e 4h
    }

    @Test
    void incompleteServiceCountsAsZeroAndIsExcludedFromAverage() {
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 8, 0);
        var done = service(UUID.randomUUID(), "ALINHAMENTO", base, base.plusHours(1));
        // serviço sem DONE: não entra na média
        var ongoing = new WorkshopService(UUID.randomUUID(), "ALINHAMENTO", UUID.randomUUID(),
                List.of(new Status(ServiceStatusEnum.DOING, base)));
        when(serviceRepository.findAll()).thenReturn(List.of(done, ongoing));

        var result = allUseCase.execute(AverageTimeEnum.HOURS);

        assertEquals(1, result.size());
        assertEquals(1.0, result.get(0).getAverageTime(), 0.001);
    }

    @Test
    void byIdReturnsExecutionTimeInMinutes() {
        UUID id = UUID.randomUUID();
        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 8, 0);
        when(serviceRepository.findById(id))
                .thenReturn(Optional.of(service(id, "REVISAO", base, base.plusMinutes(90))));

        var result = byIdUseCase.execute(id, AverageTimeEnum.MINUTES);

        assertEquals("REVISAO", result.getServiceTypeName());
        assertEquals(90.0, result.getAverageTime(), 0.001);
    }

    @Test
    void byIdReturnsZeroWhenNotCompleted() {
        UUID id = UUID.randomUUID();
        var ongoing = new WorkshopService(id, "REVISAO", UUID.randomUUID(),
                List.of(new Status(ServiceStatusEnum.DOING, LocalDateTime.now())));
        when(serviceRepository.findById(id)).thenReturn(Optional.of(ongoing));

        var result = byIdUseCase.execute(id, AverageTimeEnum.HOURS);

        assertEquals(0.0, result.getAverageTime(), 0.001);
    }

    @Test
    void byIdThrowsWhenServiceMissing() {
        UUID id = UUID.randomUUID();
        when(serviceRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> byIdUseCase.execute(id, AverageTimeEnum.HOURS));
    }
}
