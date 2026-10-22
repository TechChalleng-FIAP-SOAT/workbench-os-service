package com.workbench.workshop.infrastructure.notification;

import com.workbench.workshop.application.serviceorder.port.out.ClientContactPort;
import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import com.workbench.workshop.infrastructure.messaging.MessagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SnsCustomerNotificationPublisherTest {

    private SnsClient snsClient;
    private MessagingProperties props;
    private ClientContactPort clientContactPort;
    private SnsCustomerNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        snsClient = mock(SnsClient.class);
        props = new MessagingProperties();
        props.setNotificationsTopicArn("arn:aws:sns:us-east-1:000:os-notifications");
        clientContactPort = mock(ClientContactPort.class);
        publisher = new SnsCustomerNotificationPublisher(snsClient, props, clientContactPort);
    }

    private ServiceOrder order() {
        return ServiceOrder.reconstitute(UUID.randomUUID(), "52998224725", "ABC1234",
                List.of("TROCA_OLEO"), "[TROCA_OLEO]", OrderServiceStatusEnum.RECEBIDA, null, null, null);
    }

    @Test
    void publishesFormattedNotificationWithClientEmail() {
        when(clientContactPort.findEmailByDocument("52998224725"))
                .thenReturn(Optional.of("maria@example.com"));

        publisher.notifyCustomer(order());

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        PublishRequest req = captor.getValue();
        assertTrue(req.subject().contains("Recebida"));
        assertTrue(req.message().contains("recebida"));
        assertTrue(req.messageAttributes().containsKey("clientEmail"));
        assertEquals("maria@example.com", req.messageAttributes().get("clientEmail").stringValue());
    }

    @Test
    void publishesEvenWithoutClientEmail() {
        when(clientContactPort.findEmailByDocument(anyString())).thenReturn(Optional.empty());
        publisher.notifyCustomer(order());
        verify(snsClient).publish(any(PublishRequest.class));
    }

    @Test
    void skipsWhenTopicArnMissing() {
        props.setNotificationsTopicArn("");
        publisher.notifyCustomer(order());
        verifyNoInteractions(snsClient);
    }

    @Test
    void snsFailureIsSwallowed() {
        when(clientContactPort.findEmailByDocument(anyString())).thenReturn(Optional.of("a@b.com"));
        when(snsClient.publish(any(PublishRequest.class))).thenThrow(new RuntimeException("boom"));
        assertDoesNotThrow(() -> publisher.notifyCustomer(order()));
    }
}
