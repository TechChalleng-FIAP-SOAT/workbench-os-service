package com.workbench.workshop.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.workshop.application.saga.SagaOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SagaReplyConsumerTest {

    private static final String QUEUE = "http://localhost:4566/000000000000/workbench-os-saga-replies-queue";
    private static final String OS_ID = UUID.randomUUID().toString();

    private SqsClient sqsClient;
    private SagaOrchestrator orchestrator;
    private SagaReplyConsumer consumer;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        sqsClient = mock(SqsClient.class);
        orchestrator = mock(SagaOrchestrator.class);
        MessagingProperties props = new MessagingProperties();
        props.setEnabled(true);
        props.setSagaRepliesQueueUrl(QUEUE);
        consumer = new SagaReplyConsumer(sqsClient, props, mapper, orchestrator);
    }

    private Message msg(String body, String receipt) {
        return Message.builder().body(body).receiptHandle(receipt).build();
    }

    private String envelopeJson(String messageId) throws Exception {
        var env = new MessageEnvelope(messageId, "saga-1", OS_ID, "ReservaConfirmada",
                "2026-01-01T00:00:00Z", Map.of("ok", true));
        return mapper.writeValueAsString(env);
    }

    @Test
    void pollReceivesAndProcessesMessages() throws Exception {
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder()
                        .messages(msg(envelopeJson("m1"), "r1")).build());

        consumer.poll();

        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
        verify(orchestrator).onEvent(eq("ReservaConfirmada"), eq(UUID.fromString(OS_ID)), any());
    }

    @Test
    void deduplicatesByMessageId() throws Exception {
        String body = envelopeJson("dup-1");

        consumer.handle(QUEUE, msg(body, "r1"));
        consumer.handle(QUEUE, msg(body, "r2"));

        // Ambas são apagadas (a 2ª como duplicata), mas o processamento ocorre 1x.
        verify(sqsClient, times(2)).deleteMessage(any(DeleteMessageRequest.class));
        verify(orchestrator, times(1)).onEvent(any(), any(), any());
    }

    @Test
    void leavesMessageForDlqOnFailure() {
        // Corpo inválido → parse falha → NÃO apaga (vai para redrive/DLQ).
        consumer.handle(QUEUE, msg("{invalid json", "r1"));

        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void pollNoopWhenQueueUrlMissing() {
        MessagingProperties empty = new MessagingProperties();
        empty.setEnabled(true);
        new SagaReplyConsumer(sqsClient, empty, mapper, orchestrator).poll();

        verify(sqsClient, never()).receiveMessage(any(ReceiveMessageRequest.class));
    }
}
