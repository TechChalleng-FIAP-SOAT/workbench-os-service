package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.saga.SagaOrchestrator;
import com.workbench.workshop.application.service.port.out.ServiceRepository;
import com.workbench.workshop.application.service.port.out.ServiceTypeRepository;
import com.workbench.workshop.application.serviceorder.port.out.OrderMetricsPort;
import com.workbench.workshop.application.serviceorder.port.out.OrderNotificationPort;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.service.ServiceType;
import com.workbench.workshop.domain.service.WorkshopService;
import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DiagnoseOrderUseCaseTest {

    private ServiceOrderRepository serviceOrderRepository;
    private ServiceRepository serviceRepository;
    private ServiceTypeRepository serviceTypeRepository;
    private SagaOrchestrator sagaOrchestrator;
    private OrderNotificationPort notificationPort;
    private OrderMetricsPort metrics;
    private DiagnoseOrderUseCase useCase;

    private final UUID osId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        serviceOrderRepository = mock(ServiceOrderRepository.class);
        serviceRepository = mock(ServiceRepository.class);
        serviceTypeRepository = mock(ServiceTypeRepository.class);
        sagaOrchestrator = mock(SagaOrchestrator.class);
        notificationPort = mock(OrderNotificationPort.class);
        metrics = mock(OrderMetricsPort.class);
        useCase = new DiagnoseOrderUseCase(serviceOrderRepository, serviceRepository,
                serviceTypeRepository, sagaOrchestrator, notificationPort, metrics);
        when(serviceOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(serviceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ServiceOrder received() {
        return ServiceOrder.reconstitute(osId, "52998224725", "ABC1234",
                List.of("TROCA_OLEO"), "[TROCA_OLEO]", OrderServiceStatusEnum.RECEBIDA, null,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private WorkshopService osService() {
        WorkshopService s = new WorkshopService(UUID.randomUUID(), "TROCA_OLEO", osId, List.of());
        return s;
    }

    @Test
    void diagnoseAdvancesToDiagnosticoAndStartsSaga() {
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(received()));
        when(serviceRepository.findByIdOS(osId)).thenReturn(List.of(osService()));
        when(serviceTypeRepository.findByName("TROCA_OLEO"))
                .thenReturn(Optional.of(new ServiceType(UUID.randomUUID(), "TROCA_OLEO", "d",
                        new BigDecimal("100"), new BigDecimal("1"))));

        var parts = List.of(new DiagnoseOrderUseCase.PartItem("p1", new BigDecimal("2")));
        var services = List.of(new DiagnoseOrderUseCase.ServiceLabor(
                "TROCA_OLEO", new BigDecimal("150"), new BigDecimal("1.5"), false));

        ServiceOrder result = useCase.execute(osId, parts, services);

        assertEquals(OrderServiceStatusEnum.EM_DIAGNOSTICO, result.getStatus());
        verify(sagaOrchestrator).start(any(ServiceOrder.class), anyList());
    }

    @Test
    void fieldValueWinsOverCatalogDefault() {
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(received()));
        when(serviceRepository.findByIdOS(osId)).thenReturn(List.of(osService()));
        when(serviceTypeRepository.findByName("TROCA_OLEO"))
                .thenReturn(Optional.of(new ServiceType(UUID.randomUUID(), "TROCA_OLEO", "d",
                        new BigDecimal("100"), new BigDecimal("1"))));

        var services = List.of(new DiagnoseOrderUseCase.ServiceLabor(
                "TROCA_OLEO", new BigDecimal("150"), null, true));

        useCase.execute(osId, List.of(), services);

        // laborCost=150 (campo vence), laborHours=1 (useDefault puxa do catálogo, pois campo nulo).
        verify(serviceRepository).save(argThat(s ->
                s.getLaborCost().compareTo(new BigDecimal("150")) == 0
                        && s.getLaborHours().compareTo(new BigDecimal("1")) == 0));
    }

    @Test
    void withoutUseDefaultAndNullFieldLaborIsZero() {
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(received()));
        when(serviceRepository.findByIdOS(osId)).thenReturn(List.of(osService()));
        when(serviceTypeRepository.findByName("TROCA_OLEO"))
                .thenReturn(Optional.of(new ServiceType(UUID.randomUUID(), "TROCA_OLEO", "d",
                        new BigDecimal("100"), new BigDecimal("1"))));

        var services = List.of(new DiagnoseOrderUseCase.ServiceLabor("TROCA_OLEO", null, null, false));

        useCase.execute(osId, List.of(), services);

        verify(serviceRepository).save(argThat(s ->
                s.getLaborCost().compareTo(BigDecimal.ZERO) == 0 && s.getLaborHours() == null));
    }

    @Test
    void unknownOrderThrows() {
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> useCase.execute(osId, List.of(), List.of()));
        verify(metrics).processingFailed("diagnose");
    }

    @Test
    void serviceNotInOrderThrows() {
        when(serviceOrderRepository.findById(osId)).thenReturn(Optional.of(received()));
        when(serviceRepository.findByIdOS(osId)).thenReturn(List.of(osService()));

        var services = List.of(new DiagnoseOrderUseCase.ServiceLabor("INEXISTENTE", null, null, false));

        assertThrows(NoSuchElementException.class, () -> useCase.execute(osId, List.of(), services));
    }
}
