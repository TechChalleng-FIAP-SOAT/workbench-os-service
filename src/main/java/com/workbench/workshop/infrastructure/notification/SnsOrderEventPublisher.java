package com.workbench.workshop.infrastructure.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.workshop.application.serviceorder.port.out.OrderNotificationPort;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import com.workbench.workshop.infrastructure.messaging.MessageEnvelope;
import com.workbench.workshop.infrastructure.messaging.MessagingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

/**
 * Publisher SNS da mudança de status da OS (Fase 4, Etapa 2).
 *
 * <p>Implementa a {@link OrderNotificationPort}. Ativo apenas quando
 * {@code messaging.enabled=true}; caso contrário, o
 * {@link LoggingOrderNotificationAdapter} assume (fallback sem broker).
 *
 * <p>Publica um evento {@code StatusAlterado} no tópico {@code order-events}, que
 * o fan-out SNS→SQS entrega aos serviços interessados. Falha de publicação é
 * best-effort: loga e não reverte a operação de negócio (consistente com o ADR
 * de notificação do monólito).
 */
@Component
@ConditionalOnProperty(prefix = "messaging", name = "enabled", havingValue = "true")
@Slf4j
public class SnsOrderEventPublisher implements OrderNotificationPort {

    private final SnsClient snsClient;
    private final MessagingProperties props;
    private final ObjectMapper objectMapper;

    public SnsOrderEventPublisher(SnsClient snsClient, MessagingProperties props, ObjectMapper objectMapper) {
        this.snsClient = snsClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public void notifyStatusChange(ServiceOrder order) {
        MessageEnvelope envelope = MessageEnvelope.of(
                "StatusAlterado",
                order.getId().toString(),            // sagaId = id da OS (Saga por OS)
                order.getId().toString(),
                Map.of(
                        "status", order.getServiceStatus(),
                        "cpfCnpj", order.getCpfCnpj(),
                        "placa", order.getPlacaVeiculo()
                )
        );
        try {
            String body = objectMapper.writeValueAsString(envelope);
            snsClient.publish(PublishRequest.builder()
                    .topicArn(props.getOrderEventsTopicArn())
                    .message(body)
                    .build());
            log.info("Published StatusAlterado to SNS: orderId={}, status={}",
                    order.getId(), order.getServiceStatus());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize StatusAlterado event for orderId={}", order.getId(), e);
        } catch (RuntimeException e) {
            // Best-effort: não reverte a transição de status da OS.
            log.error("Failed to publish StatusAlterado to SNS for orderId={}", order.getId(), e);
        }
    }
}
