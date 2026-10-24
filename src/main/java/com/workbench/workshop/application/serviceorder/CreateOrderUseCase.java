package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.service.CreateServiceUseCase;
import com.workbench.workshop.application.serviceorder.port.out.CustomerValidationPort;
import com.workbench.workshop.application.serviceorder.port.out.OrderMetricsPort;
import com.workbench.workshop.application.serviceorder.port.out.OrderNotificationPort;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final CreateServiceUseCase createServiceUseCase;
    private final ServiceOrderRepository serviceOrderRepository;
    private final OrderNotificationPort notificationPort;
    private final com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort customerNotificationPort;
    private final OrderMetricsPort metrics;
    private final CustomerValidationPort customerValidation;

    @Transactional
    public ServiceOrder execute(String cpfCnpj, String placaVeiculo, List<String> serviceTypes) {
        try {
            // Valida cliente (CPF/CNPJ) e veículo (placa) no Customer Service por REST
            // (ADR-0003: sem acesso cruzado a banco). Com a validação desligada, o
            // adapter no-op deixa passar (dev/testes).
            customerValidation.validateClientExists(cpfCnpj);
            customerValidation.validateVehicleExists(placaVeiculo);

            ServiceOrder order = ServiceOrder.create(cpfCnpj, placaVeiculo, serviceTypes);

            // Persistir a OS ANTES dos serviços: a tabela `service` tem FK para
            // `service_order` (id_os), então a OS precisa existir primeiro. No
            // monólito não havia FK e a ordem inversa passava silenciosamente.
            ServiceOrder saved = serviceOrderRepository.save(order);

            serviceTypes.forEach(service -> createServiceUseCase.execute(service, saved.getId()));

            notificationPort.notifyStatusChange(saved);
            customerNotificationPort.notifyCustomer(saved);
            metrics.created();

            return saved;
        } catch (RuntimeException exception) {
            metrics.processingFailed("create");
            throw exception;
        }
    }
}
