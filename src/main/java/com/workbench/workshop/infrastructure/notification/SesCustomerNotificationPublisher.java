package com.workbench.workshop.infrastructure.notification;

import com.workbench.workshop.application.serviceorder.port.out.ClientContactPort;
import com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import com.workbench.workshop.infrastructure.messaging.MessagingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import java.util.Optional;

/**
 * Envia o e-mail ao <b>próprio cliente</b> pelo Amazon SES, no lugar do tópico SNS (cuja única
 * assinatura recebia tudo). O destinatário é o e-mail cadastrado no Customer Service, resolvido
 * por {@link ClientContactPort}. Ativo com {@code messaging.notification-channel=ses}.
 *
 * <p>Sem e-mail cadastrado para o cliente, nada é enviado (e fica no log): não há um endereço
 * "padrão" para onde mandar o que é do cliente. Best-effort, como o canal SNS: falha de envio
 * loga e não reverte o fluxo de negócio.
 *
 * <p><b>SES em sandbox:</b> uma conta nova só envia de remetente verificado e para destinatário
 * verificado. Para mandar a qualquer cliente é preciso pedir o acesso à produção ao SES.
 */
@Component
@ConditionalOnProperty(prefix = "messaging", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "messaging", name = "notification-channel", havingValue = "ses")
@Slf4j
public class SesCustomerNotificationPublisher implements CustomerNotificationPort {

    private final SesV2Client sesClient;
    private final ClientContactPort clientContactPort;
    private final String sender;

    // S9352: ClientContactPort tem dois beans mutuamente exclusivos via @ConditionalOnProperty
    // (Noop/REST); só um existe em runtime, então o alerta de ambiguidade é falso positivo.
    @SuppressWarnings("java:S9352")
    public SesCustomerNotificationPublisher(SesV2Client sesClient, MessagingProperties props,
                                            ClientContactPort clientContactPort) {
        this.sesClient = sesClient;
        this.clientContactPort = clientContactPort;
        this.sender = props.getSesSenderEmail();
        // Falha fechada: sem remetente, nenhum e-mail sairia. Melhor não subir do que perder tudo em silêncio.
        if (sender == null || sender.isBlank()) {
            throw new IllegalStateException(
                    "messaging.notification-channel=ses exige SES_SENDER_EMAIL (remetente verificado no SES).");
        }
    }

    @Override
    public void notifyCustomer(ServiceOrder order) {
        send(order, CustomerNotificationMessages.statusUpdate(order), "status");
    }

    @Override
    public void notifyPaymentLink(ServiceOrder order, String checkoutUrl) {
        if (checkoutUrl == null || checkoutUrl.isBlank()) {
            log.warn("checkoutUrl ausente — link de pagamento não enviado (os={})", order.getId());
            return;
        }
        send(order, CustomerNotificationMessages.paymentLink(order, checkoutUrl), "link de pagamento");
    }

    private void send(ServiceOrder order, CustomerNotificationMessages.Email content, String kind) {
        try {
            Optional<String> recipient = clientContactPort.findEmailByDocument(order.getCpfCnpj());
            if (recipient.isEmpty() || recipient.get().isBlank()) {
                log.warn("Cliente sem e-mail cadastrado — {} não enviado: os={}", kind, order.getId());
                return;
            }
            String to = recipient.get();
            var response = sesClient.sendEmail(SendEmailRequest.builder()
                    .fromEmailAddress(sender)
                    .destination(Destination.builder().toAddresses(to).build())
                    .content(EmailContent.builder().simple(Message.builder()
                            .subject(utf8(content.subject()))
                            .body(Body.builder().text(utf8(content.body())).build())
                            .build()).build())
                    .build());
            // O endereço do cliente é dado pessoal: o log leva o id da OS e o id da mensagem, não o e-mail.
            log.info("E-mail ao cliente enviado pelo SES ({}): os={}, messageId={}",
                    kind, order.getId(), response.messageId());
        } catch (RuntimeException e) {
            // Best-effort: não reverte a transição de status nem a Saga.
            log.error("Falha ao enviar e-mail ({}) da OS {} pelo SES: {}", kind, order.getId(), e.getMessage(), e);
        }
    }

    private static Content utf8(String text) {
        return Content.builder().data(text).charset("UTF-8").build();
    }
}
