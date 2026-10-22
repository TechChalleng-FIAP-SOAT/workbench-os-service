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
}
