package com.workbench.workshop.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.workshop.application.saga.port.out.SagaCommandPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;
import java.util.UUID;

/**
 * Publica os comandos da Saga no tópico {@code order-events} (ADR-0003). As filas
 * de comando do Billing e do Execution são inscritas nesse tópico e cada serviço
 * reage ao comando destinado a ele (pelo {@code type}).
 *
 * <p>Ativo apenas com {@code messaging.enabled=true}; o fallback no-op mantém o
 * orquestrador testável/executável sem broker.
 */
@Component
@ConditionalOnProperty(prefix = "messaging", name = "enabled", havingValue = "true")
@Slf4j
public class SagaCommandPublisher implements SagaCommandPort {

    private final SnsClient snsClient;
    private final MessagingProperties props;
    private final ObjectMapper objectMapper;

    public SagaCommandPublisher(SnsClient snsClient, MessagingProperties props, ObjectMapper objectMapper) {
        this.snsClient = snsClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public void send(String type, UUID sagaId, UUID serviceOrderId, Map<String, Object> payload) {
        MessageEnvelope envelope = MessageEnvelope.of(type, sagaId.toString(), serviceOrderId.toString(), payload);
        try {
            snsClient.publish(PublishRequest.builder()
                    .topicArn(props.getOrderEventsTopicArn())
                    .message(objectMapper.writeValueAsString(envelope))
                    .build());
            log.info("Saga command sent: type={}, serviceOrderId={}", type, serviceOrderId);
        } catch (Exception e) {
            log.error("Failed to send saga command {} for OS {}", type, serviceOrderId, e);
            throw new IllegalStateException("Failed to send saga command", e);
        }
    }
}
