package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.AfterCommit;
import com.workbench.workshop.application.service.port.out.ServiceRepository;
import com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort;
import com.workbench.workshop.application.serviceorder.port.out.OrderMetricsPort;
import com.workbench.workshop.application.serviceorder.port.out.OrderNotificationPort;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.service.ServiceStatusEnum;
import com.workbench.workshop.domain.service.WorkshopService;
import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Execução de um serviço da OS pelo técnico: {@code TO_DO → DOING → DONE}. Só vale com
 * a OS em EM_EXECUCAO. Quando o último serviço termina, a OS vai sozinha para
 * FINALIZADA (alteração automática de status), com notificação ao cliente.
 *
 * <p>Os instantes registrados alimentam o relatório de tempo médio de execução
 * ({@code /monitoring}).
 */
@Service
@RequiredArgsConstructor
public class UpdateServiceStatusUseCase {

    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceRepository serviceRepository;
    private final OrderNotificationPort notificationPort;
    private final CustomerNotificationPort customerNotificationPort;
    private final OrderMetricsPort metrics;

    @Transactional
    public WorkshopService execute(UUID orderId, UUID serviceId, ServiceStatusEnum status) {
        try {
            if (status == null) {
                throw new IllegalArgumentException("Status do serviço é obrigatório (DOING ou DONE).");
            }
            ServiceOrder order = serviceOrderRepository.findById(orderId)
                    .orElseThrow(() -> new NoSuchElementException("Ordem de serviço não encontrada: " + orderId));
            if (order.getStatus() != OrderServiceStatusEnum.EM_EXECUCAO) {
                throw new IllegalStateException("Serviços só podem ser executados com a OS em EM_EXECUCAO (atual: "
                        + order.getServiceStatus() + ").");
            }
            List<WorkshopService> services = serviceRepository.findByIdOS(orderId);
            WorkshopService service = services.stream()
                    .filter(s -> s.getId().equals(serviceId))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException(
                            "Serviço " + serviceId + " não pertence à OS " + orderId));

            service.moveTo(status, LocalDateTime.now());
            WorkshopService saved = serviceRepository.save(service);

            if (services.stream().allMatch(WorkshopService::isDone)) {
                finish(order);
            }
            return saved;
        } catch (RuntimeException exception) {
            metrics.processingFailed("update_service_status");
            throw exception;
        }
    }

    // Todos os serviços concluídos → OS FINALIZADA (mesmos efeitos do PATCH manual).
    private void finish(ServiceOrder order) {
        var enteredAt = order.getUpdatedAt();
        order.advanceTo(OrderServiceStatusEnum.FINALIZADA);
        ServiceOrder saved = serviceOrderRepository.save(order);
        AfterCommit.run("notificações da OS finalizada " + saved.getId(), () -> {
            notificationPort.notifyStatusChange(saved);
            customerNotificationPort.notifyCustomer(saved);
        });
        metrics.recordTimeInStatus(OrderServiceStatusEnum.EM_EXECUCAO, enteredAt);
    }
}
