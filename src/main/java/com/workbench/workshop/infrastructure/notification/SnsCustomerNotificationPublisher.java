package com.workbench.workshop.infrastructure.notification;

import com.workbench.workshop.application.serviceorder.port.out.ClientContactPort;
import com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort;
import com.workbench.workshop.domain.serviceorder.OrderStatusLabel;
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
            String statusLabel = OrderStatusLabel.of(order.getStatus());
            Optional<String> email = clientContactPort.findEmailByDocument(order.getCpfCnpj());
            String subject = "OS Management - Atualizacao de Status: " + statusLabel;
            String message = buildMessage(order.getCpfCnpj(), order.getPlacaVeiculo(), statusLabel);

            PublishRequest.Builder request = PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject(subject)
                    .message(message);

            // Atributo com o e-mail do cliente: permite filtrar a entrega por
            // destinatário (subscription com filter policy) quando configurado.
            email.ifPresent(e -> request.messageAttributes(Map.of(
                    "clientEmail", MessageAttributeValue.builder()
                            .dataType("String").stringValue(e).build())));

            snsClient.publish(request.build());
            log.info("Notificação de status publicada: os={}, status={}, email={}",
                    order.getId(), statusLabel, email.orElse("(sem e-mail)"));
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
            String subject = "OS Management - Link de Pagamento da sua Ordem de Servico";
            String message = buildPaymentMessage(order.getCpfCnpj(), order.getPlacaVeiculo(), checkoutUrl);

            PublishRequest.Builder request = PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject(subject)
                    .message(message);
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

    private String buildPaymentMessage(String clientDocument, String vehicleInfo, String checkoutUrl) {
        return String.format("""
                ============================================
                OS Management - Pagamento da Ordem de Servico
                ============================================

                Ola,

                O orcamento da sua ordem de servico foi aprovado e o servico finalizado.
                Para concluir, realize o pagamento pelo link abaixo:

                %s

                Cliente (documento): %s
                Veiculo: %s

                --------------------------------------------
                Oficina Mecanica - OS Management
                Este e um email automatico.
                ============================================
                """, checkoutUrl, clientDocument, vehicleInfo);
    }

    // --- Mensagens formatadas (portadas do monólito SnsNotificationService) ---

    private String buildMessage(String clientDocument, String vehicleInfo, String status) {
        return String.format("""
                ============================================
                OS Management - Notificacao de Status
                ============================================

                Ola,

                %s

                Status atual: %s
                Cliente (documento): %s
                Veiculo: %s

                --------------------------------------------
                Oficina Mecanica - OS Management
                Este e um email automatico.
                ============================================
                """, getStatusMessage(status), status, clientDocument, vehicleInfo);
    }

    private String getStatusMessage(String status) {
        return switch (status) {
            case "Recebida" -> "Sua ordem de servico foi recebida e esta em nossa fila de atendimento.";
            case "Em Diagnostico" -> "Seu veiculo esta sendo avaliado pelo nosso mecanico.";
            case "Aguardando Aprovacao" -> "O diagnostico foi concluido. Por favor, avalie o orcamento para aprovar ou recusar.";
            case "Aprovado" -> "O orcamento foi aprovado! Nosso mecanico iniciara os servicos em breve.";
            case "Em Execucao" -> "Os servicos estao sendo realizados no seu veiculo.";
            case "Finalizada" -> "Todos os servicos foram concluidos! Seu veiculo esta pronto para retirada.";
            case "Pagamento Pendente" -> "Aguardando a confirmacao do pagamento da sua ordem de servico.";
            case "Pago" -> "Pagamento confirmado! Obrigado.";
            case "Entregue" -> "Seu veiculo foi entregue. Obrigado pela confianca!";
            case "Recusada" -> "O orcamento foi recusado. Entre em contato conosco caso deseje reavaliar.";
            default -> "O status da sua ordem de servico foi atualizado para: " + status;
        };
    }
}
