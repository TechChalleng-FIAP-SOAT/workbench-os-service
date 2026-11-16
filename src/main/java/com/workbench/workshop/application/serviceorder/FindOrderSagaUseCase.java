package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.saga.SagaOrchestrator;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.saga.SagaState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Estado da Saga de uma OS: em que passo está e, se falhou, por quê (ex.: estoque
 * insuficiente na reserva). É o que o atendente consulta antes de refazer o diagnóstico.
 */
@Service
@RequiredArgsConstructor
public class FindOrderSagaUseCase {

    private final ServiceOrderRepository serviceOrderRepository;
    private final SagaOrchestrator sagaOrchestrator;

    public SagaState execute(UUID orderId) {
        if (serviceOrderRepository.findById(orderId).isEmpty()) {
            throw new NoSuchElementException("Ordem de serviço não encontrada: " + orderId);
        }
        return sagaOrchestrator.findSaga(orderId)
                .orElseThrow(() -> new NoSuchElementException(
                        "A OS " + orderId + " ainda não tem Saga (o diagnóstico não foi registrado)."));
    }
}
