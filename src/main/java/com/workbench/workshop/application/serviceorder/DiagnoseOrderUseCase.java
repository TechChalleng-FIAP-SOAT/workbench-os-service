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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Diagnóstico da OS (ADR-0005): o técnico informa as peças a reservar e, por
 * serviço, o valor e as horas de mão de obra. É o ponto que:
 * <ol>
 *   <li>grava a MO em cada {@link WorkshopService} da OS (regra {@code useDefault});</li>
 *   <li>transita a OS {@code RECEBIDA → EM_DIAGNOSTICO};</li>
 *   <li>inicia a Saga, comandando a reserva de estoque no Execution Service.</li>
 * </ol>
 *
 * <p>Regra da mão de obra por serviço:
 * <ul>
 *   <li>campo preenchido (não nulo): o valor informado prevalece;</li>
 *   <li>{@code useDefault=true} e campo nulo: usa o default do catálogo
 *       ({@code ServiceType});</li>
 *   <li>{@code useDefault=false} e campo nulo: zero (custo) / nulo (horas).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DiagnoseOrderUseCase {

    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceRepository serviceRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final SagaOrchestrator sagaOrchestrator;
    private final OrderNotificationPort notificationPort;
    private final com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort customerNotificationPort;
    private final OrderMetricsPort metrics;

    /** Peça a reservar no diagnóstico. */
    public record PartItem(String productId, BigDecimal quantity) {}

    /** Mão de obra informada para um serviço da OS. */
    public record ServiceLabor(String serviceType, BigDecimal laborCost, BigDecimal laborHours, boolean useDefault) {}

    @Transactional
    public ServiceOrder execute(UUID orderId, List<PartItem> parts, List<ServiceLabor> services) {
        try {
            ServiceOrder order = serviceOrderRepository.findById(orderId)
                    .orElseThrow(() -> new NoSuchElementException("Ordem de servico nao encontrada: " + orderId));

            applyLabor(orderId, services);

            order.advanceTo(OrderServiceStatusEnum.EM_DIAGNOSTICO);
            ServiceOrder saved = serviceOrderRepository.save(order);

            List<SagaOrchestrator.ReserveItem> reserveItems = parts == null ? List.of()
                    : parts.stream()
                        .map(p -> new SagaOrchestrator.ReserveItem(p.productId(), p.quantity()))
                        .toList();
            sagaOrchestrator.start(saved, reserveItems);

            notificationPort.notifyStatusChange(saved);
            customerNotificationPort.notifyCustomer(saved);
            metrics.recordTimeInStatus(OrderServiceStatusEnum.RECEBIDA, order.getCreatedAt());
            return saved;
        } catch (RuntimeException exception) {
            metrics.processingFailed("diagnose");
            throw exception;
        }
    }

    /** Grava a MO (custo/horas) em cada serviço da OS, conforme a regra de default. */
    private void applyLabor(UUID orderId, List<ServiceLabor> services) {
        if (services == null || services.isEmpty()) {
            return;
        }
        List<WorkshopService> osServices = serviceRepository.findByIdOS(orderId);
        for (ServiceLabor labor : services) {
            WorkshopService target = osServices.stream()
                    .filter(s -> s.getServiceTypeName().equals(labor.serviceType()))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException(
                            "Servico '" + labor.serviceType() + "' nao pertence a OS " + orderId));

            ServiceType catalog = serviceTypeRepository.findByName(labor.serviceType()).orElse(null);
            BigDecimal catalogCost = catalog == null ? null : catalog.getLaborCost();
            BigDecimal catalogHours = catalog == null ? null : catalog.getLaborHours();

            target.setLaborCost(resolve(labor.laborCost(), catalogCost, labor.useDefault(), BigDecimal.ZERO));
            target.setLaborHours(resolve(labor.laborHours(), catalogHours, labor.useDefault(), null));
            serviceRepository.save(target);
        }
    }

    private BigDecimal resolve(BigDecimal field, BigDecimal catalogDefault, boolean useDefault, BigDecimal fallback) {
        if (field != null) {
            return field;
        }
        return useDefault ? catalogDefault : fallback;
    }
}
