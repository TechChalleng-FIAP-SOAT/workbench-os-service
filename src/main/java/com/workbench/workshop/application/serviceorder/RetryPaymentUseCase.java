package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.Requester;
import com.workbench.workshop.application.saga.SagaOrchestrator;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Pede uma nova cobrança para a OS cujo pagamento foi recusado (novo link enviado por
 * e-mail). Cliente só pode pedir para a própria OS; staff para qualquer uma.
 */
@Service
@RequiredArgsConstructor
public class RetryPaymentUseCase {

    private final ServiceOrderRepository serviceOrderRepository;
    private final SagaOrchestrator sagaOrchestrator;

    @Transactional
    public void execute(UUID orderId, Requester requester) {
        ServiceOrder order = serviceOrderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Ordem de serviço não encontrada: " + orderId));
        requester.requireAccessTo(order.getCpfCnpj());
        if (order.getStatus() != OrderServiceStatusEnum.PAGAMENTO_PENDENTE) {
            throw new IllegalStateException("A OS não está aguardando pagamento.");
        }
        sagaOrchestrator.retryPayment(orderId);
    }
}
