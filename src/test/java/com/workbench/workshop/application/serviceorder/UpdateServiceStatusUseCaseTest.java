package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.service.port.out.ServiceRepository;
import com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort;
import com.workbench.workshop.application.serviceorder.port.out.OrderMetricsPort;
import com.workbench.workshop.application.serviceorder.port.out.OrderNotificationPort;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.service.ServiceStatusEnum;
import com.workbench.workshop.domain.service.Status;
import com.workbench.workshop.domain.service.WorkshopService;
import com.workbench.workshop.domain.serviceorder.InvalidStatusTransitionException;
import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UpdateServiceStatusUseCaseTest {

    private ServiceOrderRepository orderRepository;
    private ServiceRepository serviceRepository;
    private OrderNotificationPort notificationPort;
    private UpdateServiceStatusUseCase useCase;

    private final UUID osId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        orderRepository = mock(ServiceOrderRepository.class);
        serviceRepository = mock(ServiceRepository.class);
        notificationPort = mock(OrderNotificationPort.class);
        useCase = new UpdateServiceStatusUseCase(orderRepository, serviceRepository, notificationPort,
                mock(CustomerNotificationPort.class), mock(OrderMetricsPort.class));
        when(serviceRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ServiceOrder order(OrderServiceStatusEnum status) {
        return ServiceOrder.reconstitute(osId, "52998224725", "ABC1234", List.of("TROCA_OLEO", "REVISAO"),
                "[TROCA_OLEO, REVISAO]", status, null, LocalDateTime.now(), LocalDateTime.now());
    }

    private WorkshopService service(String type, ServiceStatusEnum... history) {
        List<Status> statuses = new ArrayList<>();
        for (ServiceStatusEnum s : history) {
            statuses.add(new Status(s, LocalDateTime.now()));
        }
        return new WorkshopService(UUID.randomUUID(), type, osId, statuses);
    }

    @Test
    void startsServiceAndKeepsOrderInExecution() {
        ServiceOrder order = order(OrderServiceStatusEnum.EM_EXECUCAO);
        WorkshopService oil = service("TROCA_OLEO", ServiceStatusEnum.TO_DO);
        when(orderRepository.findById(osId)).thenReturn(Optional.of(order));
        when(serviceRepository.findByIdOS(osId)).thenReturn(List.of(oil, service("REVISAO", ServiceStatusEnum.TO_DO)));

        WorkshopService result = useCase.execute(osId, oil.getId(), ServiceStatusEnum.DOING);

        assertEquals(ServiceStatusEnum.DOING, result.currentStatus());
        assertEquals(OrderServiceStatusEnum.EM_EXECUCAO, order.getStatus());
    }

    @Test
    void finishingLastServiceFinalizesTheOrder() {
        ServiceOrder order = order(OrderServiceStatusEnum.EM_EXECUCAO);
        WorkshopService oil = service("TROCA_OLEO", ServiceStatusEnum.TO_DO, ServiceStatusEnum.DOING);
        WorkshopService review = service("REVISAO", ServiceStatusEnum.TO_DO, ServiceStatusEnum.DOING, ServiceStatusEnum.DONE);
        when(orderRepository.findById(osId)).thenReturn(Optional.of(order));
        when(serviceRepository.findByIdOS(osId)).thenReturn(List.of(oil, review));

        useCase.execute(osId, oil.getId(), ServiceStatusEnum.DONE);

        assertEquals(OrderServiceStatusEnum.FINALIZADA, order.getStatus());
        verify(notificationPort).notifyStatusChange(order);
    }

    @Test
    void refusesWhenOrderIsNotInExecution() {
        WorkshopService oil = service("TROCA_OLEO", ServiceStatusEnum.TO_DO);
        when(orderRepository.findById(osId)).thenReturn(Optional.of(order(OrderServiceStatusEnum.APROVADO)));

        assertThrows(IllegalStateException.class, () -> useCase.execute(osId, oil.getId(), ServiceStatusEnum.DOING));
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void refusesSkippingAStep() {
        WorkshopService oil = service("TROCA_OLEO", ServiceStatusEnum.TO_DO);
        when(orderRepository.findById(osId)).thenReturn(Optional.of(order(OrderServiceStatusEnum.EM_EXECUCAO)));
        when(serviceRepository.findByIdOS(osId)).thenReturn(List.of(oil));

        assertThrows(InvalidStatusTransitionException.class,
                () -> useCase.execute(osId, oil.getId(), ServiceStatusEnum.DONE));
    }

    @Test
    void refusesServiceFromAnotherOrder() {
        when(orderRepository.findById(osId)).thenReturn(Optional.of(order(OrderServiceStatusEnum.EM_EXECUCAO)));
        when(serviceRepository.findByIdOS(osId)).thenReturn(List.of(service("TROCA_OLEO", ServiceStatusEnum.TO_DO)));

        assertThrows(NoSuchElementException.class,
                () -> useCase.execute(osId, UUID.randomUUID(), ServiceStatusEnum.DOING));
    }
}
