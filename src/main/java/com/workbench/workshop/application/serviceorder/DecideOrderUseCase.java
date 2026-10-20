package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.saga.SagaOrchestrator;
import com.workbench.workshop.application.serviceorder.port.out.OrderMetricsPort;
import com.workbench.workshop.domain.serviceorder.Decision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 * A orquestração em si (comandos de estoque + avanço do SagaState + transição de
 * status) fica no {@link SagaOrchestrator}; este caso de uso apenas valida a
 * decisão e delega.
 */
@Service
@RequiredArgsConstructor
public class DecideOrderUseCase {

    private final SagaOrchestrator sagaOrchestrator;
    private final OrderMetricsPort metrics;

    @Transactional
    public void execute(UUID orderId, Decision decision, String reason) {
        if (decision == null) {
            throw new IllegalArgumentException("Decisao do orcamento e obrigatoria (APPROVED/REJECTED).");
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
