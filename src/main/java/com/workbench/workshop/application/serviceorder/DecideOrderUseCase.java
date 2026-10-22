package com.workbench.workshop.application.serviceorder;

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
 * A orquestração em si (comandos de estoque + avanço do SagaState + transição de
 * status) fica no {@link SagaOrchestrator}; este caso de uso apenas valida a
 * decisão e delega.
 */
@Service
@RequiredArgsConstructor
public class DecideOrderUseCase {

    private final SagaOrchestrator sagaOrchestrator;
    private final OrderMetricsPort metrics;
    private final ServiceOrderRepository serviceOrderRepository;

    /**
     * Decisão por staff (ADMIN/TECHNICIAN): pode decidir qualquer OS. Mantido para
     * uso interno/administrativo.
     */
    @Transactional
    public void execute(UUID orderId, Decision decision, String reason) {
        decide(orderId, decision, reason);
    }

    /**
     * Decisão a partir do principal autenticado. Quando o token é de cliente
     * (ROLE_CLIENT, subject = CPF/CNPJ), valida que a OS pertence a esse documento
     * antes de decidir — evita que um cliente decida a OS de outro (ownership).
     * Staff (ADMIN/TECHNICIAN) não sofre a restrição.
     *
     * @param requesterDocument documento do subject do token (CPF/CNPJ do cliente)
     * @param isClient          true quando o token carrega ROLE_CLIENT
     */
    @Transactional
    public void execute(UUID orderId, Decision decision, String reason,
                        String requesterDocument, boolean isClient) {
        if (isClient) {
            ServiceOrder order = serviceOrderRepository.findById(orderId)
                    .orElseThrow(() -> new NoSuchElementException("Ordem de servico nao encontrada: " + orderId));
            if (requesterDocument == null || !requesterDocument.equals(order.getCpfCnpj())) {
                throw new SecurityException("Cliente nao autorizado a decidir esta ordem de servico.");
            }
        }
        decide(orderId, decision, reason);
    }

    private void decide(UUID orderId, Decision decision, String reason) {
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
