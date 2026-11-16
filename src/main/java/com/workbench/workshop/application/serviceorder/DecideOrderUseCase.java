package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.Requester;
import com.workbench.workshop.application.saga.SagaOrchestrator;
import com.workbench.workshop.application.serviceorder.port.out.OrderMetricsPort;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.serviceorder.Decision;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Decisão do cliente sobre o orçamento (pausa humana da Saga — ADR-0005).
 *
 * <ul>
 *   <li>{@link Decision#APPROVED}: confirma a reserva (baixa de estoque) e a OS
 *       segue para APROVADO.</li>
 *   <li>{@link Decision#REJECTED}: libera a reserva (estoque volta ao disponível,
 *       único estorno do fluxo) e a OS vai para RECUSADA.</li>
 * </ul>
 *
 * A orquestração em si fica no {@link SagaOrchestrator}; este caso de uso valida a
 * decisão e quem decide (cliente só decide a própria OS; staff decide qualquer uma).
 */
@Service
@RequiredArgsConstructor
public class DecideOrderUseCase {

    private final SagaOrchestrator sagaOrchestrator;
    private final OrderMetricsPort metrics;
    private final ServiceOrderRepository serviceOrderRepository;

    @Transactional
    public void execute(UUID orderId, Decision decision, String reason, Requester requester) {
        if (decision == null) {
            throw new IllegalArgumentException("Decisão do orçamento é obrigatória (APPROVED/REJECTED).");
        }
        if (requester.client()) {
            ServiceOrder order = serviceOrderRepository.findById(orderId)
                    .orElseThrow(() -> new NoSuchElementException("Ordem de serviço não encontrada: " + orderId));
            requester.requireAccessTo(order.getCpfCnpj());
        }
        try {
            switch (decision) {
                case APPROVED -> sagaOrchestrator.onBudgetApproved(orderId);
                case REJECTED -> sagaOrchestrator.onBudgetRejected(orderId, reason);
            }
        } catch (RuntimeException exception) {
            metrics.processingFailed("decide_order");
            throw exception;
        }
    }
}
