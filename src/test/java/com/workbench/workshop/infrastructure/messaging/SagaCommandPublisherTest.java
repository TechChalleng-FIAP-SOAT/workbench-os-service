package com.workbench.workshop.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SagaCommandPublisherTest {

    private SnsClient snsClient;
    private SagaCommandPublisher publisher;
    private final UUID osId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        snsClient = mock(SnsClient.class);
        MessagingProperties props = new MessagingProperties();
        props.setEnabled(true);
        props.setOrderEventsTopicArn("arn:aws:sns:us-east-1:000000000000:workbench-order-events");
        publisher = new SagaCommandPublisher(snsClient, props, new ObjectMapper());
    }

    @Test
    void publishesCommandEnvelopeToOrderEventsTopic() {
        when(snsClient.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().messageId("m1").build());

        publisher.send("ComandoReservarEstoque", osId, osId, Map.of("items", "x"));

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(snsClient).publish(captor.capture());
        PublishRequest req = captor.getValue();
        assertTrue(req.topicArn().endsWith("workbench-order-events"));
        assertTrue(req.message().contains("ComandoReservarEstoque"));
        assertTrue(req.message().contains(osId.toString()));
    }

    @Test
    void wrapsSnsFailureInIllegalState() {
        when(snsClient.publish(any(PublishRequest.class))).thenThrow(new RuntimeException("sns down"));

        assertThrows(IllegalStateException.class,
                () -> publisher.send("ComandoGerarOrcamento", osId, osId, Map.of()));
    }
}
