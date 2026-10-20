package com.workbench.workshop.application.saga.port.out;

import java.util.Map;
import java.util.UUID;

/**
 * Porta de saída pela qual o orquestrador envia comandos aos participantes da Saga
 * (Execution, Billing). A implementação real publica no broker (SNS); o fallback
 * no-op permite rodar/testar o orquestrador sem mensageria.
 */
public interface SagaCommandPort {
    void send(String type, UUID sagaId, UUID serviceOrderId, Map<String, Object> payload);
}
