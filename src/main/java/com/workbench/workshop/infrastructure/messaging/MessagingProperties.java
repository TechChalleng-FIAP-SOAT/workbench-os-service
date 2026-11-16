package com.workbench.workshop.infrastructure.messaging;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades de mensageria (prefixo {@code messaging}).
 *
 * <p>{@code enabled} alterna entre o publisher SNS real e o adapter de log;
 * {@code endpoint} vazio = AWS real, preenchido = LocalStack.
 */
@Component
@ConfigurationProperties(prefix = "messaging")
@Getter
@Setter
public class MessagingProperties {
    private boolean enabled;
    private String region = "us-east-1";
    private String endpoint;
    private String orderEventsTopicArn;
    private String sagaRepliesQueueUrl;
    /** Tópico dedicado às notificações de status ao cliente (e-mail via subscription). */
    private String notificationsTopicArn;

    /**
     * Canal do e-mail ao cliente: {@code sns} (padrão) publica no tópico e o e-mail vai para
     * quem estiver inscrito nele; {@code ses} envia direto ao endereço do cliente.
     */
    private String notificationChannel = "sns";

    /**
     * Remetente verificado no SES (obrigatório com {@code notification-channel=ses}). No modo
     * sandbox do SES, os destinatários também precisam estar verificados.
     */
    private String sesSenderEmail;

    /**
     * Configuration set do SES que envia bounces e reclamações ao tópico de alertas (criado no
     * workbench-messaging). Vazio = envia sem configuration set.
     */
    private String sesConfigurationSet;
}
