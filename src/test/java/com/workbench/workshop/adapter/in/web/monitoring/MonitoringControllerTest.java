package com.workbench.workshop.adapter.in.web.monitoring;

import com.workbench.workshop.application.monitoring.GetAverageExecutionTimeByIdUseCase;
import com.workbench.workshop.application.monitoring.GetAverageExecutionTimeUseCase;
import com.workbench.workshop.domain.monitoring.AverageTimeEnum;
import com.workbench.workshop.domain.monitoring.ServiceAverageTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MonitoringControllerTest {

    private GetAverageExecutionTimeUseCase allUseCase;
    private GetAverageExecutionTimeByIdUseCase byIdUseCase;
    private MonitoringController controller;

    @BeforeEach
    void setUp() {
        allUseCase = mock(GetAverageExecutionTimeUseCase.class);
        byIdUseCase = mock(GetAverageExecutionTimeByIdUseCase.class);
        controller = new MonitoringController(allUseCase, byIdUseCase);
    }

    @Test
    void allDelegatesWithProvidedUnit() {
        when(allUseCase.execute(AverageTimeEnum.HOURS))
                .thenReturn(List.of(new ServiceAverageTime("TROCA_OLEO", 2.0)));

        var res = controller.getAverageExecutionTime(
                new MonitoringController.AverageTimeRequest(AverageTimeEnum.HOURS));

        assertEquals(200, res.getStatusCode().value());
        assertEquals(1, res.getBody().size());
        verify(allUseCase).execute(AverageTimeEnum.HOURS);
    }

    @Test
    void allDefaultsToHoursWhenUnitNull() {
        when(allUseCase.execute(any())).thenReturn(List.of());
        controller.getAverageExecutionTime(new MonitoringController.AverageTimeRequest(null));
        verify(allUseCase).execute(AverageTimeEnum.HOURS);
    }

    @Test
    void byIdDelegates() {
        UUID id = UUID.randomUUID();
        when(byIdUseCase.execute(eq(id), eq(AverageTimeEnum.MINUTES)))
                .thenReturn(new ServiceAverageTime("REVISAO", 90.0));

        var res = controller.getAverageExecutionTimeById(
                new MonitoringController.AverageTimeByIdRequest(id, AverageTimeEnum.MINUTES));

        assertEquals(200, res.getStatusCode().value());
        assertEquals(90.0, res.getBody().getAverageTime(), 0.001);
    }
}
