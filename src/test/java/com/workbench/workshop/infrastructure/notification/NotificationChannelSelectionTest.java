package com.workbench.workshop.infrastructure.notification;

import com.workbench.workshop.application.serviceorder.port.out.ClientContactPort;
import com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort;
import com.workbench.workshop.infrastructure.messaging.MessagingConfig;
import com.workbench.workshop.infrastructure.messaging.MessagingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sns.SnsClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Qual canal de e-mail ao cliente o Spring monta, conforme {@code messaging.notification-channel}.
 * Garante que SNS e SES nunca coexistem e que o SNS continua sendo o padrão.
 */
class NotificationChannelSelectionTest {

    @Configuration
    @EnableConfigurationProperties(MessagingProperties.class)
    @Import({SnsCustomerNotificationPublisher.class, SesCustomerNotificationPublisher.class, MessagingConfig.class})
    static class Wiring {
        @Bean
        ClientContactPort clientContactPort() {
            return mock(ClientContactPort.class);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Wiring.class)
            // Com endpoint, os clients usam credenciais fixas e não precisam de AWS de verdade.
            .withPropertyValues("messaging.enabled=true", "messaging.endpoint=http://localhost:4566");

    @Test
    void snsIsTheDefaultWhenNoChannelIsConfigured() {
        runner.run(ctx -> {
            assertNull(ctx.getStartupFailure());
            assertInstanceOf(SnsCustomerNotificationPublisher.class, ctx.getBean(CustomerNotificationPort.class));
            assertEquals(0, ctx.getBeansOfType(SesCustomerNotificationPublisher.class).size());
            assertEquals(0, ctx.getBeansOfType(SesV2Client.class).size());
            assertEquals(1, ctx.getBeansOfType(SnsClient.class).size());
        });
    }

    @Test
    void snsWhenExplicitlyConfigured() {
        runner.withPropertyValues("messaging.notification-channel=sns").run(ctx -> {
            assertInstanceOf(SnsCustomerNotificationPublisher.class, ctx.getBean(CustomerNotificationPort.class));
            assertEquals(0, ctx.getBeansOfType(SesCustomerNotificationPublisher.class).size());
        });
    }

    @Test
    void sesReplacesSnsWhenConfiguredWithASender() {
        runner.withPropertyValues("messaging.notification-channel=ses", "messaging.ses-sender-email=oficina@exemplo.com")
                .run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    assertInstanceOf(SesCustomerNotificationPublisher.class, ctx.getBean(CustomerNotificationPort.class));
                    assertEquals(1, ctx.getBeansOfType(CustomerNotificationPort.class).size());
                    assertEquals(0, ctx.getBeansOfType(SnsCustomerNotificationPublisher.class).size());
                    assertEquals(1, ctx.getBeansOfType(SesV2Client.class).size());
                });
    }

    @Test
    void sesWithoutASenderPreventsStartup() {
        runner.withPropertyValues("messaging.notification-channel=ses").run(ctx -> {
            assertNotNull(ctx.getStartupFailure());
            assertTrue(rootMessage(ctx.getStartupFailure()).contains("SES_SENDER_EMAIL"));
        });
    }

    /**
     * Com a mensageria desligada nenhum dos dois canais existe (quem vale é o adapter Noop),
     * mesmo que o canal peça SES: o adapter e o client SES vivem sob a mesma condição.
     */
    @Test
    void withMessagingOffNoChannelBeanIsCreated() {
        new ApplicationContextRunner().withUserConfiguration(Wiring.class)
                .withPropertyValues("messaging.enabled=false", "messaging.notification-channel=ses",
                        "messaging.ses-sender-email=oficina@exemplo.com")
                .run(ctx -> {
                    assertNull(ctx.getStartupFailure());
                    assertEquals(0, ctx.getBeansOfType(SesCustomerNotificationPublisher.class).size());
                    assertEquals(0, ctx.getBeansOfType(SnsCustomerNotificationPublisher.class).size());
                    assertEquals(0, ctx.getBeansOfType(SesV2Client.class).size());
                });
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return String.valueOf(t.getMessage());
    }
}
