package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.Requester;
import com.workbench.workshop.application.service.CreateServiceUseCase;
import com.workbench.workshop.application.serviceorder.port.out.OrderMetricsPort;
import com.workbench.workshop.application.serviceorder.port.out.OrderNotificationPort;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.serviceorder.InvalidStatusTransitionException;
import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import com.workbench.workshop.domain.service.WorkshopService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceOrderUseCaseTest {

    private static ServiceOrder createTestOrder(UUID id, String cpfCnpj, String placa, OrderServiceStatusEnum status) {
        return ServiceOrder.reconstitute(
                id, cpfCnpj, placa,
                List.of("TROCA_OLEO"), "[TROCA_OLEO]",
                status, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class CreateOrderUseCaseTests {

        @Mock private CreateServiceUseCase createServiceUseCase;
        @Mock private ServiceOrderRepository serviceOrderRepository;
        @Mock private OrderNotificationPort notificationPort;
        @Mock private com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort customerNotificationPort;
        @Mock private OrderMetricsPort metrics;
        @Mock private com.workbench.workshop.application.serviceorder.port.out.CustomerValidationPort customerValidation;
        @InjectMocks private CreateOrderUseCase useCase;

        @Test
        void createsOrderSuccessfully() {
            String cpf = "12345678900";
            String placa = "ABC1234";
            List<String> serviceTypes = List.of("Pintura", "Mecanica");

            when(createServiceUseCase.execute(anyString(), any(UUID.class))).thenReturn(WorkshopService.create("TROCA_OLEO", UUID.randomUUID(), BigDecimal.ZERO, null, java.time.LocalDateTime.now()));
            when(serviceOrderRepository.save(any(ServiceOrder.class))).thenAnswer(i -> i.getArgument(0));

            ServiceOrder result = useCase.execute(cpf, placa, serviceTypes);

            assertNotNull(result);
            assertNotNull(result.getId());
            assertEquals(cpf, result.getCpfCnpj());
            assertEquals("ABC1234", result.getLicensePlate());
            assertEquals(OrderServiceStatusEnum.RECEBIDA.getStatus(), result.getServiceStatus());
            assertEquals(serviceTypes, result.getListService());
            verify(createServiceUseCase, times(2)).execute(anyString(), any(UUID.class));
            verify(serviceOrderRepository).save(any(ServiceOrder.class));
            verify(notificationPort).notifyStatusChange(any(ServiceOrder.class));
            verify(metrics).created();
        }

        @Test
        void createsServiceForEachType() {
            String cpf = "12345678900";
            String placa = "ABC1234";
            List<String> serviceTypes = List.of("Pintura", "Mecanica", "Eletrica");

            when(createServiceUseCase.execute(anyString(), any(UUID.class))).thenReturn(WorkshopService.create("TROCA_OLEO", UUID.randomUUID(), BigDecimal.ZERO, null, java.time.LocalDateTime.now()));
            when(serviceOrderRepository.save(any(ServiceOrder.class))).thenAnswer(i -> i.getArgument(0));

            useCase.execute(cpf, placa, serviceTypes);

            verify(createServiceUseCase, times(3)).execute(anyString(), any(UUID.class));
            verify(createServiceUseCase).execute(eq("Pintura"), any(UUID.class));
            verify(createServiceUseCase).execute(eq("Mecanica"), any(UUID.class));
            verify(createServiceUseCase).execute(eq("Eletrica"), any(UUID.class));
        }

        @Test
        void recordsFailureMetricWhenServiceCreationFails() {
            // A OS é salva primeiro (FK); se a criação de um serviço falha, a
            // exceção propaga e a métrica de falha é registrada. A transação
            // (@Transactional no fluxo real) desfaz o insert da OS.
            when(serviceOrderRepository.save(any(ServiceOrder.class))).thenAnswer(i -> i.getArgument(0));
            when(createServiceUseCase.execute(anyString(), any(UUID.class)))
                    .thenThrow(new com.workbench.workshop.application.service.UnknownServiceTypeException("NAO_EXISTE"));

            assertThrows(RuntimeException.class,
                    () -> useCase.execute("12345678900", "ABC1234", List.of("X")));

            verify(metrics).processingFailed("create");
            verify(notificationPort, never()).notifyStatusChange(any());
        }

        @Test
        void validatesClientAndVehicleBeforeCreating() {
            when(createServiceUseCase.execute(anyString(), any(UUID.class))).thenReturn(WorkshopService.create("TROCA_OLEO", UUID.randomUUID(), BigDecimal.ZERO, null, java.time.LocalDateTime.now()));
            when(serviceOrderRepository.save(any(ServiceOrder.class))).thenAnswer(i -> i.getArgument(0));

            useCase.execute("12345678900", "ABC1234", List.of("Pintura"));

            verify(customerValidation).validateClientExists("12345678900");
            verify(customerValidation).validateVehicleExists("ABC1234");
        }

        @Test
        void propagatesWhenCustomerValidationFails() {
            doThrow(new com.workbench.workshop.application.serviceorder.port.out.CustomerValidationPort
                    .CustomerValidationException("cliente inexistente"))
                    .when(customerValidation).validateClientExists(anyString());

            assertThrows(RuntimeException.class,
                    () -> useCase.execute("00000000000", "ABC1234", List.of("Pintura")));

            verify(serviceOrderRepository, never()).save(any());
            verify(metrics).processingFailed("create");
        }

        @Test
        void throwsWhenServiceTypesEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> useCase.execute("12345678900", "ABC1234", List.of()));
            verify(metrics).processingFailed("create");
        }

        @Test
        void throwsWhenServiceTypesNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> useCase.execute("12345678900", "ABC1234", null));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class FindOrderByIdUseCaseTests {

        @Mock private ServiceOrderRepository serviceOrderRepository;
        @InjectMocks private FindOrderByIdUseCase useCase;

        @Test
        void returnsOrderWhenFound() {
            UUID id = UUID.randomUUID();
            ServiceOrder order = createTestOrder(id, "12345678900", "ABC1234", OrderServiceStatusEnum.RECEBIDA);
            when(serviceOrderRepository.findById(id)).thenReturn(Optional.of(order));

            ServiceOrder result = useCase.execute(id);

            assertEquals(id, result.getId());
            assertEquals("12345678900", result.getCpfCnpj());
        }

        @Test
        void throwsWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(serviceOrderRepository.findById(id)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class, () -> useCase.execute(id));

            assertTrue(ex.getMessage().contains("Ordem de serviço não encontrada"));
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class ListOrdersUseCaseTests {

        @Mock private ServiceOrderRepository serviceOrderRepository;
        @InjectMocks private ListOrdersUseCase useCase;

        @Test
        void returnsActiveOrdersSorted() {
            List<ServiceOrder> orders = List.of(
                    createTestOrder(UUID.randomUUID(), "111", "AAA1111", OrderServiceStatusEnum.RECEBIDA),
                    createTestOrder(UUID.randomUUID(), "222", "BBB2222", OrderServiceStatusEnum.EM_DIAGNOSTICO)
            );
            when(serviceOrderRepository.findActiveOrdersSorted()).thenReturn(orders);

            List<ServiceOrder> result = useCase.execute();

            assertEquals(2, result.size());
            verify(serviceOrderRepository).findActiveOrdersSorted();
        }

        @Test
        void returnsEmptyListWhenNoActiveOrders() {
            when(serviceOrderRepository.findActiveOrdersSorted()).thenReturn(List.of());

            assertTrue(useCase.execute().isEmpty());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class UpdateOrderUseCaseTests {

        @Mock private ServiceOrderRepository serviceOrderRepository;
        @Mock private OrderNotificationPort notificationPort;
        @Mock private com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort customerNotificationPort;
        @Mock private OrderMetricsPort metrics;
        @Mock private com.workbench.workshop.application.saga.SagaOrchestrator sagaOrchestrator;
        @Mock private com.workbench.workshop.application.service.port.out.ServiceRepository serviceRepository;
        @InjectMocks private UpdateOrderUseCase useCase;

        @Test
        void cannotFinalizeWhileAServiceIsPending() {
            UUID id = UUID.randomUUID();
            ServiceOrder order = createTestOrder(id, "12345678900", "ABC1234", OrderServiceStatusEnum.EM_EXECUCAO);
            when(serviceOrderRepository.findById(id)).thenReturn(Optional.of(order));
            when(serviceRepository.findByIdOS(id)).thenReturn(java.util.List.of(new com.workbench.workshop.domain.service.WorkshopService(
                    UUID.randomUUID(), "TROCA_OLEO", id, java.util.List.of())));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> useCase.execute(id, OrderServiceStatusEnum.FINALIZADA));

            assertTrue(ex.getMessage().contains("1 serviço(s) não concluído(s)"));
            verify(serviceOrderRepository, never()).save(any());
        }

        @Test
        void finalizesWhenEveryServiceIsDone() {
            UUID id = UUID.randomUUID();
            ServiceOrder order = createTestOrder(id, "12345678900", "ABC1234", OrderServiceStatusEnum.EM_EXECUCAO);
            var done = new com.workbench.workshop.domain.service.WorkshopService(UUID.randomUUID(), "TROCA_OLEO", id, java.util.List.of());
            done.moveTo(com.workbench.workshop.domain.service.ServiceStatusEnum.DOING, java.time.LocalDateTime.now());
            done.moveTo(com.workbench.workshop.domain.service.ServiceStatusEnum.DONE, java.time.LocalDateTime.now());
            when(serviceOrderRepository.findById(id)).thenReturn(Optional.of(order));
            when(serviceOrderRepository.save(any(ServiceOrder.class))).thenAnswer(i -> i.getArgument(0));
            when(serviceRepository.findByIdOS(id)).thenReturn(java.util.List.of(done));

            useCase.execute(id, OrderServiceStatusEnum.FINALIZADA);

            assertEquals(OrderServiceStatusEnum.FINALIZADA, order.getStatus());
        }

        @Test
        void updatesOrderStatusSuccessfully() {
            UUID id = UUID.randomUUID();
            ServiceOrder order = createTestOrder(id, "12345678900", "ABC1234", OrderServiceStatusEnum.RECEBIDA);

            when(serviceOrderRepository.findById(id)).thenReturn(Optional.of(order));
            when(serviceOrderRepository.save(any(ServiceOrder.class))).thenAnswer(i -> i.getArgument(0));

            useCase.execute(id, OrderServiceStatusEnum.EM_DIAGNOSTICO);

            assertEquals(OrderServiceStatusEnum.EM_DIAGNOSTICO.getStatus(), order.getServiceStatus());
            verify(serviceOrderRepository).save(order);
            verify(notificationPort).notifyStatusChange(order);
            verify(metrics).recordTimeInStatus(eq(OrderServiceStatusEnum.RECEBIDA), any());
        }

        @Test
        void sagaOwnedStatusIsBlockedWhenOrderHasSaga() {
            UUID id = UUID.randomUUID();
            ServiceOrder order = createTestOrder(id, "12345678900", "ABC1234", OrderServiceStatusEnum.PAGAMENTO_PENDENTE);
            when(serviceOrderRepository.findById(id)).thenReturn(Optional.of(order));
            when(sagaOrchestrator.hasSaga(id)).thenReturn(true);

            // Marcar PAGO à mão numa OS com Saga pularia o pagamento.
            assertThrows(IllegalStateException.class, () -> useCase.execute(id, OrderServiceStatusEnum.PAGO));
            verify(serviceOrderRepository, never()).save(any());
        }

        @Test
        void sagaOwnedStatusIsAllowedWithoutSaga() {
            UUID id = UUID.randomUUID();
            ServiceOrder order = createTestOrder(id, "12345678900", "ABC1234", OrderServiceStatusEnum.PAGAMENTO_PENDENTE);
            when(serviceOrderRepository.findById(id)).thenReturn(Optional.of(order));
            when(serviceOrderRepository.save(any(ServiceOrder.class))).thenAnswer(i -> i.getArgument(0));
            when(sagaOrchestrator.hasSaga(id)).thenReturn(false);

            useCase.execute(id, OrderServiceStatusEnum.PAGO); // modo manual (sem Saga)

            assertEquals(OrderServiceStatusEnum.PAGO, order.getStatus());
        }

        @Test
        void throwsWhenInvalidTransition() {
            UUID id = UUID.randomUUID();
            ServiceOrder order = createTestOrder(id, "12345678900", "ABC1234", OrderServiceStatusEnum.RECEBIDA);

            when(serviceOrderRepository.findById(id)).thenReturn(Optional.of(order));

            assertThrows(InvalidStatusTransitionException.class,
                    () -> useCase.execute(id, OrderServiceStatusEnum.FINALIZADA));

            verify(serviceOrderRepository, never()).save(any());
            verify(metrics).processingFailed("update_status");
        }

        @Test
        void throwsWhenOrderNotFound() {
            UUID id = UUID.randomUUID();
            when(serviceOrderRepository.findById(id)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class,
                    () -> useCase.execute(id, OrderServiceStatusEnum.FINALIZADA));
        }

        @Test
        void throwsWhenStatusIsNull() {
            UUID id = UUID.randomUUID();

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> useCase.execute(id, null));

            assertTrue(ex.getMessage().contains("Status da ordem de serviço é obrigatório"));
            verify(serviceOrderRepository, never()).findById(any());
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    class FindOrdersByDocumentUseCaseTests {

        @Mock private ServiceOrderRepository serviceOrderRepository;
        @InjectMocks private FindOrdersByDocumentUseCase useCase;

        @Test
        void returnsOrdersForDocument() {
            String doc = "52998224725";
            when(serviceOrderRepository.findByCpfCnpj(doc)).thenReturn(List.of(
                    createTestOrder(UUID.randomUUID(), doc, "ABC1D23", OrderServiceStatusEnum.RECEBIDA)));

            assertEquals(1, useCase.execute(doc, Requester.staff("admin@workbench.dev")).size());
            verify(serviceOrderRepository).findByCpfCnpj(doc);
        }

        @Test
        void normalizesFormattedDocument() {
            when(serviceOrderRepository.findByCpfCnpj("52998224725")).thenReturn(List.of());

            useCase.execute("529.982.247-25", Requester.staff("admin@workbench.dev"));

            verify(serviceOrderRepository).findByCpfCnpj("52998224725");
        }

        @Test
        void handlesNullDocument() {
            when(serviceOrderRepository.findByCpfCnpj("")).thenReturn(List.of());

            assertTrue(useCase.execute(null, Requester.staff("admin@workbench.dev")).isEmpty());
            verify(serviceOrderRepository).findByCpfCnpj("");
        }

        @Test
        void clientCannotQueryAnotherClientsDocument() {
            assertThrows(SecurityException.class,
                    () -> useCase.execute("11144477735", new Requester("52998224725", true)));
            verify(serviceOrderRepository, never()).findByCpfCnpj(any());
        }

        @Test
        void clientQueriesOwnDocument() {
            when(serviceOrderRepository.findByCpfCnpj("52998224725")).thenReturn(List.of());
            useCase.execute("529.982.247-25", new Requester("52998224725", true));
            verify(serviceOrderRepository).findByCpfCnpj("52998224725");
        }
    }
}
