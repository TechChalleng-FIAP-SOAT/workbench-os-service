package com.workbench.workshop.infrastructure.notification;

import com.workbench.workshop.application.serviceorder.port.out.ClientContactPort;
import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import com.workbench.workshop.infrastructure.messaging.MessagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SesCustomerNotificationPublisherTest {

    private SesV2Client sesClient;
    private ClientContactPort clientContactPort;
    private SesCustomerNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        sesClient = mock(SesV2Client.class);
        when(sesClient.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("msg-1").build());
        clientContactPort = mock(ClientContactPort.class);
        publisher = new SesCustomerNotificationPublisher(sesClient, props("oficina@exemplo.com"), clientContactPort);
    }

    private static MessagingProperties props(String sender) {
        MessagingProperties props = new MessagingProperties();
        props.setSesSenderEmail(sender);
        return props;
    }

    private ServiceOrder order() {
        return ServiceOrder.reconstitute(UUID.randomUUID(), "52998224725", "ABC1234",
                List.of("TROCA_OLEO"), "[TROCA_OLEO]", OrderServiceStatusEnum.RECEBIDA, null, null, null);
    }

    private SendEmailRequest sent() {
        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).sendEmail(captor.capture());
        return captor.getValue();
    }

    @Test
    void sendsTheStatusEmailToTheCustomerAddress() {
        when(clientContactPort.findEmailByDocument("52998224725")).thenReturn(Optional.of("maria@example.com"));

        publisher.notifyCustomer(order());

        SendEmailRequest request = sent();
        assertEquals("oficina@exemplo.com", request.fromEmailAddress());
        assertEquals(List.of("maria@example.com"), request.destination().toAddresses());
        assertTrue(request.content().simple().subject().data().contains("Recebida"));
        assertTrue(request.content().simple().body().text().data().contains("recebida"));
        assertEquals("UTF-8", request.content().simple().body().text().charset());
    }

    @Test
    void paymentLinkGoesToTheCustomerWithTheCheckoutUrl() {
        when(clientContactPort.findEmailByDocument("52998224725")).thenReturn(Optional.of("maria@example.com"));

        publisher.notifyPaymentLink(order(), "https://checkout/abc123");

        SendEmailRequest request = sent();
        assertEquals(List.of("maria@example.com"), request.destination().toAddresses());
        assertTrue(request.content().simple().subject().data().toLowerCase().contains("pagamento"));
        assertTrue(request.content().simple().body().text().data().contains("https://checkout/abc123"));
    }

    @Test
    void doesNotSendWhenTheCustomerHasNoEmail() {
        when(clientContactPort.findEmailByDocument(anyString())).thenReturn(Optional.empty());

        publisher.notifyCustomer(order());
        publisher.notifyPaymentLink(order(), "https://checkout/x");

        verifyNoInteractions(sesClient);
    }

    @Test
    void doesNotSendToABlankAddress() {
        when(clientContactPort.findEmailByDocument(anyString())).thenReturn(Optional.of("  "));

        publisher.notifyCustomer(order());

        verifyNoInteractions(sesClient);
    }

    @Test
    void paymentLinkWithoutUrlIsNotSent() {
        publisher.notifyPaymentLink(order(), "  ");
        publisher.notifyPaymentLink(order(), null);

        verifyNoInteractions(sesClient);
        verifyNoInteractions(clientContactPort);
    }

    @Test
    void sesFailureIsSwallowedSoTheBusinessFlowContinues() {
        when(clientContactPort.findEmailByDocument(anyString())).thenReturn(Optional.of("a@b.com"));
        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenThrow(new RuntimeException("MessageRejected"));

        assertDoesNotThrow(() -> publisher.notifyCustomer(order()));
        assertDoesNotThrow(() -> publisher.notifyPaymentLink(order(), "https://checkout/x"));
    }

    @Test
    void contactLookupFailureIsSwallowed() {
        when(clientContactPort.findEmailByDocument(anyString())).thenThrow(new RuntimeException("customer fora do ar"));

        assertDoesNotThrow(() -> publisher.notifyCustomer(order()));
        verifyNoInteractions(sesClient);
    }

    @Test
    void paymentFailureEmailGoesToTheCustomerWithoutTheTechnicalReason() {
        when(clientContactPort.findEmailByDocument("52998224725")).thenReturn(Optional.of("maria@example.com"));

        publisher.notifyPaymentFailed(order());

        SendEmailRequest request = sent();
        assertEquals(List.of("maria@example.com"), request.destination().toAddresses());
        assertTrue(request.content().simple().subject().data().toLowerCase().contains("pagamento"));
        String body = request.content().simple().body().text().data();
        assertTrue(body.contains("nao foi aprovado"));
        assertTrue(body.contains("novo link"));
        // Sem link: a recusa não traz o link antigo, e o motivo do gateway é interno.
        assertFalse(body.contains("http"));
    }

    @Test
    void paymentFailureEmailIsSkippedWithoutCustomerAddressOrOnSesError() {
        when(clientContactPort.findEmailByDocument(anyString())).thenReturn(Optional.empty());
        publisher.notifyPaymentFailed(order());
        verifyNoInteractions(sesClient);

        when(clientContactPort.findEmailByDocument(anyString())).thenReturn(Optional.of("a@b.com"));
        when(sesClient.sendEmail(any(SendEmailRequest.class))).thenThrow(new RuntimeException("MessageRejected"));
        assertDoesNotThrow(() -> publisher.notifyPaymentFailed(order()));
    }

    @Test
    void refusesToStartWithoutASender() {
        assertThrows(IllegalStateException.class,
                () -> new SesCustomerNotificationPublisher(sesClient, props(null), clientContactPort));
        assertThrows(IllegalStateException.class,
                () -> new SesCustomerNotificationPublisher(sesClient, props("  "), clientContactPort));
    }

    @Test
    void usesTheConfigurationSetSoBouncesReachTheAlertsTopic() {
        MessagingProperties withSet = props("oficina@exemplo.com");
        withSet.setSesConfigurationSet("workbench-customer-email");
        when(clientContactPort.findEmailByDocument(any())).thenReturn(java.util.Optional.of("cliente@exemplo.com"));
        new SesCustomerNotificationPublisher(sesClient, withSet, clientContactPort)
                .notifyPaymentFailed(order());

        assertEquals("workbench-customer-email", sent().configurationSetName());
    }
}
