package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.saga.SagaOrchestrator;
import com.workbench.workshop.application.serviceorder.port.out.OrderMetricsPort;
import com.workbench.workshop.application.serviceorder.port.out.OrderNotificationPort;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateOrderUseCase {

    private final ServiceOrderRepository serviceOrderRepository;
    private final OrderNotificationPort notificationPort;
    private final com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort customerNotificationPort;
    private final OrderMetricsPort metrics;
    private final SagaOrchestrator sagaOrchestrator;

    @Transactional
    public void execute(UUID id, OrderServiceStatusEnum status) {
        try {
            if (status == null) {
                throw new IllegalArgumentException("Status da ordem de servico e obrigatorio.");
            }

            ServiceOrder order = serviceOrderRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Ordem de servico nao encontrada com id: " + id));

            OrderServiceStatusEnum previousStatus = order.getStatus();
            var enteredAt = order.getUpdatedAt();
            order.advanceTo(status);
            ServiceOrder saved = serviceOrderRepository.save(order);

            notificationPort.notifyStatusChange(saved);
            customerNotificationPort.notifyCustomer(saved);
            metrics.recordTimeInStatus(previousStatus, enteredAt);

            // Ao entrar em PAGAMENTO_PENDENTE (serviço finalizado), a cobrança é criada
            // automaticamente pela Saga (ComandoCobrarPagamento → Billing). A confirmação
            // vem depois pelo webhook do gateway.
            if (status == OrderServiceStatusEnum.PAGAMENTO_PENDENTE) {
                sagaOrchestrator.onPaymentPending(id);
            }
        } catch (RuntimeException exception) {
            metrics.processingFailed("update_status");
            throw exception;
        }
    }
}
