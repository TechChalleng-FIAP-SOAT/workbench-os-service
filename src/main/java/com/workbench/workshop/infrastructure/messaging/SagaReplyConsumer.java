package com.workbench.workshop.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workbench.workshop.application.saga.SagaOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Consumidor da fila {@code os-saga-replies-queue} (Fase 4, Etapa 2).
 *
 * <p>Faz long-polling da fila e processa as respostas da Saga. Pontos de robustez
 * exigidos pela fase:
 * <ul>
 *   <li><b>Idempotência</b>: deduplica por {@code messageId} (SQS entrega
 *       at-least-once). Uma resposta já processada é apenas apagada da fila.</li>
 *   <li><b>DLQ</b>: se o processamento lança exceção, a mensagem <i>não</i> é
 *       apagada; o SQS a reentrega e, após {@code maxReceiveCount}, o redrive a
 *       move para a DLQ (configurado no Terraform).</li>
 * </ul>
 *
 * <p>O tratamento de negócio das respostas (avançar/compensar a Saga) é da Etapa 5;
 * aqui a mecânica de consumo/dedupe/DLQ já fica pronta e o payload é logado.
 */
@Component
@ConditionalOnProperty(prefix = "messaging", name = "enabled", havingValue = "true")
@Slf4j
public class SagaReplyConsumer {

    /** Cache simples de dedupe (LRU por tamanho). Em produção, usar store persistente. */
    private static final int DEDUP_CAPACITY = 1000;
    private final Map<String, Boolean> processed = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > DEDUP_CAPACITY;
                }
            });

    private final SqsClient sqsClient;
    private final MessagingProperties props;
    private final ObjectMapper objectMapper;
    private final SagaOrchestrator sagaOrchestrator;

    public SagaReplyConsumer(SqsClient sqsClient, MessagingProperties props,
                             ObjectMapper objectMapper, SagaOrchestrator sagaOrchestrator) {
        this.sqsClient = sqsClient;
        this.props = props;
        this.objectMapper = objectMapper;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    @Scheduled(fixedDelayString = "${messaging.poll-interval-ms:5000}")
    public void poll() {
        String queueUrl = props.getSagaRepliesQueueUrl();
        if (queueUrl == null || queueUrl.isBlank()) {
            return;
        }
        var request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(5)
                .build();

        for (Message message : sqsClient.receiveMessage(request).messages()) {
            handle(queueUrl, message);
        }
    }

    /** Processa uma mensagem; visível para testes. */
    void handle(String queueUrl, Message message) {
        try {
            MessageEnvelope envelope = objectMapper.readValue(message.body(), MessageEnvelope.class);

            if (isDuplicate(envelope)) {
                log.debug("Dropping duplicate saga reply: messageId={}", envelope.messageId());
                delete(queueUrl, message);
                return;
            }

            // Correlação da Saga no MDC: com o encoder JSON, saga_id e
            // service_order_id viram campos pesquisáveis no Datadog, permitindo
            // rastrear uma OS ponta a ponta (@saga_id) através dos microsserviços.
            MDC.put("saga_id", envelope.sagaId());
            MDC.put("service_order_id", envelope.serviceOrderId());
            try {
                log.info("Saga reply received: type={}, sagaId={}, serviceOrderId={}",
                        envelope.type(), envelope.sagaId(), envelope.serviceOrderId());

                sagaOrchestrator.onEvent(
                        envelope.type(),
                        UUID.fromString(envelope.serviceOrderId()),
                        envelope.payload() == null ? Map.of() : envelope.payload());

                markProcessed(envelope);
                delete(queueUrl, message);
            } finally {
                MDC.remove("saga_id");
                MDC.remove("service_order_id");
            }
        } catch (Exception e) {
            // Não apaga a mensagem: o SQS reentrega e, após maxReceiveCount, vai p/ DLQ.
            log.error("Failed to process saga reply; leaving it for redrive/DLQ. body={}", message.body(), e);
        }
    }

    private boolean isDuplicate(MessageEnvelope envelope) {
        return processed.containsKey(envelope.messageId());
    }

    private void markProcessed(MessageEnvelope envelope) {
        processed.put(envelope.messageId(), Boolean.TRUE);
    }

    private void delete(String queueUrl, Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
    }
}
