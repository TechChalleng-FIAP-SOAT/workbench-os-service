package com.workbench.workshop.infrastructure.notification;

import com.workbench.workshop.application.serviceorder.port.out.ClientContactPort;
import com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import com.workbench.workshop.infrastructure.messaging.MessagingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;
import java.util.Optional;

/**
 * Notifica o cliente a cada mudança de status da OS, preservando a funcionalidade
 * do monólito ({@code SnsNotificationService}): publica uma mensagem formatada
 * (subject + corpo legível por status) num tópico SNS dedicado
 * ({@code notifications-topic-arn}) que tem subscription de e-mail.
 *
 * <p>O e-mail de destino é resolvido no Customer Service via {@link ClientContactPort}
 * (opção escolhida: o OS Service busca o contato por REST). Ativo apenas quando a
 * mensageria está ligada ({@code messaging.enabled=true}); best-effort (falha loga
 * e não reverte a transição de status).
 */
@Component
@ConditionalOnProperty(prefix = "messaging", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "messaging", name = "notification-channel", havingValue = "sns", matchIfMissing = true)
@Slf4j
public class SnsCustomerNotificationPublisher implements CustomerNotificationPort {

    private final SnsClient snsClient;
    private final MessagingProperties props;
    private final ClientContactPort clientContactPort;

    // S9352: ClientContactPort tem dois beans mutuamente exclusivos via
    // @ConditionalOnProperty (Noop/REST) — só um existe em runtime; falso positivo.
    @SuppressWarnings("java:S9352")
    public SnsCustomerNotificationPublisher(SnsClient snsClient, MessagingProperties props,
                                            ClientContactPort clientContactPort) {
        this.snsClient = snsClient;
        this.props = props;
        this.clientContactPort = clientContactPort;
    }

    @Override
    public void notifyCustomer(ServiceOrder order) {
        String topicArn = props.getNotificationsTopicArn();
        if (topicArn == null || topicArn.isBlank()) {
            log.debug("notifications-topic-arn ausente — notificação ao cliente ignorada (os={})", order.getId());
            return;
        }
        try {
            Optional<String> email = clientContactPort.findEmailByDocument(order.getCpfCnpj());
            var content = CustomerNotificationMessages.statusUpdate(order);

            PublishRequest.Builder request = PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject(content.subject())
                    .message(content.body());

            // Atributo com o e-mail do cliente: permite filtrar a entrega por
            // destinatário (subscription com filter policy) quando configurado.
            email.ifPresent(e -> request.messageAttributes(Map.of(
                    "clientEmail", MessageAttributeValue.builder()
                            .dataType("String").stringValue(e).build())));

            snsClient.publish(request.build());
            log.info("Notificação de status publicada: os={}, status={}, email={}",
                    order.getId(), order.getStatus(), email.orElse("(sem e-mail)"));
        } catch (RuntimeException e) {
            // Best-effort: não reverte a transição de status da OS.
            log.error("Falha ao notificar cliente da OS {}: {}", order.getId(), e.getMessage(), e);
        }
    }

    @Override
    public void notifyPaymentLink(ServiceOrder order, String checkoutUrl) {
        String topicArn = props.getNotificationsTopicArn();
        if (topicArn == null || topicArn.isBlank()) {
            log.debug("notifications-topic-arn ausente — link de pagamento não enviado (os={})", order.getId());
            return;
        }
        if (checkoutUrl == null || checkoutUrl.isBlank()) {
            log.warn("checkoutUrl ausente — link de pagamento não enviado (os={})", order.getId());
            return;
        }
        try {
            Optional<String> email = clientContactPort.findEmailByDocument(order.getCpfCnpj());
            var content = CustomerNotificationMessages.paymentLink(order, checkoutUrl);

            PublishRequest.Builder request = PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject(content.subject())
                    .message(content.body());
            email.ifPresent(e -> request.messageAttributes(Map.of(
                    "clientEmail", MessageAttributeValue.builder()
                            .dataType("String").stringValue(e).build())));

            snsClient.publish(request.build());
            log.info("Link de pagamento publicado: os={}, email={}", order.getId(), email.orElse("(sem e-mail)"));
        } catch (RuntimeException e) {
            // Best-effort: não reverte o fluxo de negócio.
            log.error("Falha ao enviar link de pagamento da OS {}: {}", order.getId(), e.getMessage(), e);
        }
    }
}
