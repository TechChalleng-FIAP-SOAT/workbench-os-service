package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.AfterCommit;
import com.workbench.workshop.application.saga.SagaOrchestrator;
import com.workbench.workshop.application.service.port.out.ServiceRepository;
import com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort;
import com.workbench.workshop.application.serviceorder.port.out.OrderMetricsPort;
import com.workbench.workshop.application.serviceorder.port.out.OrderNotificationPort;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import static com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum.*;

@Service
@RequiredArgsConstructor
public class UpdateOrderUseCase {

    /**
     * Status que a Saga decide (aprovação/recusa do orçamento e confirmação do
     * pagamento). Numa OS conduzida pela Saga, avançá-los à mão pularia a baixa de
     * estoque ou o pagamento; sem Saga (modo manual, como no monólito) seguem livres.
     */
    private static final Set<OrderServiceStatusEnum> SAGA_OWNED =
            Set.of(AGUARDANDO_APROVACAO, APROVADO, RECUSADA, PAGO, ENTREGUE);

    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceRepository serviceRepository;
    private final OrderNotificationPort notificationPort;
    private final CustomerNotificationPort customerNotificationPort;
    private final OrderMetricsPort metrics;
    private final SagaOrchestrator sagaOrchestrator;

    @Transactional
    public void execute(UUID id, OrderServiceStatusEnum status) {
        try {
            if (status == null) {
                throw new IllegalArgumentException("Status da ordem de serviço é obrigatório.");
            }

            ServiceOrder order = serviceOrderRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Ordem de serviço não encontrada: " + id));

            if (SAGA_OWNED.contains(status) && sagaOrchestrator.hasSaga(id)) {
                throw new IllegalStateException("O status " + status.getStatus()
                        + " é controlado pela Saga desta OS (use /order/{id}/decision ou aguarde o pagamento).");
            }

            if (status == FINALIZADA) {
                requireAllServicesDone(id);
            }

            OrderServiceStatusEnum previousStatus = order.getStatus();
            var enteredAt = order.getUpdatedAt();
            order.advanceTo(status);
            ServiceOrder saved = serviceOrderRepository.save(order);

            AfterCommit.run("notificações da OS " + id, () -> {
                notificationPort.notifyStatusChange(saved);
                customerNotificationPort.notifyCustomer(saved);
            });
            metrics.recordTimeInStatus(previousStatus, enteredAt);

            // Ao entrar em PAGAMENTO_PENDENTE (serviço finalizado), a cobrança é criada
            // automaticamente pela Saga (ComandoCobrarPagamento → Billing).
            if (status == PAGAMENTO_PENDENTE) {
                sagaOrchestrator.onPaymentPending(id);
            }
        } catch (RuntimeException exception) {
            metrics.processingFailed("update_status");
            throw exception;
        }
    }

    /**
     * A OS só termina quando cada serviço dela foi executado (TO_DO → DOING → DONE).
     * O caminho normal é {@code PATCH /order/{id}/services/{serviceId}/status}, que
     * finaliza a OS sozinho no último DONE; aqui só barramos o atalho manual.
     */
    private void requireAllServicesDone(UUID orderId) {
        long pending = serviceRepository.findByIdOS(orderId).stream()
                .filter(service -> !service.isDone())
                .count();
        if (pending > 0) {
            throw new IllegalStateException("A OS ainda tem " + pending + " serviço(s) não concluído(s). "
                    + "Conclua cada um em /order/{id}/services/{serviceId}/status antes de finalizá-la.");
        }
    }
}
