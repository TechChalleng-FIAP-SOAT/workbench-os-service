package com.workbench.workshop.infrastructure.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import com.workbench.workshop.infrastructure.messaging.MessagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SnsOrderEventPublisherTest {

    private SnsClient snsClient;
    private SnsOrderEventPublisher publisher;

    @BeforeEach
    void setUp() {
        snsClient = mock(SnsClient.class);
        MessagingProperties props = new MessagingProperties();
        props.setEnabled(true);
        props.setOrderEventsTopicArn("arn:aws:sns:us-east-1:000000000000:workbench-order-events");
        publisher = new SnsOrderEventPublisher(snsClient, props, new ObjectMapper());
    }

    private ServiceOrder order() {
        return ServiceOrder.create("52998224725", "ABC1234", List.of("TROCA_OLEO"));
    }

    @Test
    void publishesEnvelopeToConfiguredTopic() {
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(PublishResponse.builder().messageId("m1").build());

        publisher.notifyStatusChange(order());

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        PublishRequest req = captor.getValue();
        assertTrue(req.topicArn().endsWith("workbench-order-events"));
        assertTrue(req.message().contains("StatusAlterado"));
        assertTrue(req.message().contains("RECEBIDA"));
        assertTrue(req.message().contains("ABC1234"));
    }

    @Test
    void swallowsSnsFailureAsBestEffort() {
        when(snsClient.publish(any(PublishRequest.class))).thenThrow(new RuntimeException("sns down"));

        // Não deve propagar: notificação é best-effort e não reverte a OS.
        assertDoesNotThrow(() -> publisher.notifyStatusChange(order()));
        verify(snsClient).publish(any(PublishRequest.class));
    }
}
