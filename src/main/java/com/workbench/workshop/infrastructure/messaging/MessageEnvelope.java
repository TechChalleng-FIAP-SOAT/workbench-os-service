package com.workbench.workshop.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope comum das mensagens da Saga (ver doc "Contratos de Mensagem").
 *
 * @param messageId      id único da mensagem (dedupe no consumidor)
 * @param sagaId         correlaciona todos os passos de uma Saga
 * @param serviceOrderId OS alvo
 * @param type           tipo do comando/evento (ex.: StatusAlterado)
 * @param occurredAt     instante do fato
 * @param payload        dados específicos do tipo
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageEnvelope(
        String messageId,
        String sagaId,
        String serviceOrderId,
        String type,
        String occurredAt,
        Map<String, Object> payload
) {
    public static MessageEnvelope of(String type, String sagaId, String serviceOrderId, Map<String, Object> payload) {
        return new MessageEnvelope(
                UUID.randomUUID().toString(),
                sagaId,
                serviceOrderId,
                type,
                Instant.now().toString(),
                payload
        );
    }
}
