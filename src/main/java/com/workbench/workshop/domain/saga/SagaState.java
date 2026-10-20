package com.workbench.workshop.domain.saga;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Estado persistido da Saga de uma OS (orquestração — ADR-0003/0005).
 *
 * <p>Guarda o passo atual e os passos já concluídos com sucesso — é o
 * {@code completedSteps} que permite ao orquestrador saber o que compensar quando
 * uma etapa falha. Sobrevive a reinício do serviço (persistido no Postgres).
 */
@Getter
public class SagaState {

    private Long id;
    private UUID serviceOrderId;
    private SagaStep step;
    private final List<String> completedSteps = new ArrayList<>();
    private String failureReason;

    /** Comando enviado a um participante que aguarda resposta (null = sem pendência). */
    private String pendingCommand;
    /** Payload do comando pendente (JSON), guardado para reenvio em caso de timeout. */
    private String pendingPayloadJson;
    /** Tentativas já feitas da etapa atual (reenvios do comando pendente). */
    private int retryCount;
    /** Instante do último envio do comando pendente (base do timeout). */
    private LocalDateTime lastCommandAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected SagaState() {}

    public static SagaState start(UUID serviceOrderId) {
        SagaState s = new SagaState();
        s.serviceOrderId = serviceOrderId;
        s.step = SagaStep.INICIADA;
        return s;
    }

    public static SagaState reconstitute(Long id, UUID serviceOrderId, SagaStep step,
                                         List<String> completedSteps, String failureReason,
                                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        return reconstitute(id, serviceOrderId, step, completedSteps, failureReason,
                null, null, 0, null, createdAt, updatedAt);
    }

    public static SagaState reconstitute(Long id, UUID serviceOrderId, SagaStep step,
                                         List<String> completedSteps, String failureReason,
                                         String pendingCommand, String pendingPayloadJson,
                                         int retryCount, LocalDateTime lastCommandAt,
                                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        SagaState s = new SagaState();
        s.id = id;
        s.serviceOrderId = serviceOrderId;
        s.step = step;
        if (completedSteps != null) s.completedSteps.addAll(completedSteps);
        s.failureReason = failureReason;
        s.pendingCommand = pendingCommand;
        s.pendingPayloadJson = pendingPayloadJson;
        s.retryCount = retryCount;
        s.lastCommandAt = lastCommandAt;
        s.createdAt = createdAt;
        s.updatedAt = updatedAt;
        return s;
    }

    /** Avança para o próximo passo, registrando o anterior como concluído. */
    public void advanceTo(SagaStep next, String completedStepLabel) {
        if (completedStepLabel != null) {
            this.completedSteps.add(completedStepLabel);
        }
        this.step = next;
    }

    public void markCompensating(String reason) {
        this.step = SagaStep.COMPENSANDO;
        this.failureReason = reason;
    }

    public void markFailed(String reason) {
        this.step = SagaStep.FALHOU;
        this.failureReason = reason;
    }

    public void markCompleted() {
        this.step = SagaStep.CONCLUIDA;
    }

    public boolean hasCompleted(String stepLabel) {
        return completedSteps.contains(stepLabel);
    }

    /**
     * Registra o comando enviado a um participante que aguarda resposta. Zera o
     * contador de tentativas e marca o instante do envio (base do timeout/reenvio).
     */
    public void awaitCommand(String command, String payloadJson) {
        this.pendingCommand = command;
        this.pendingPayloadJson = payloadJson;
        this.retryCount = 0;
        this.lastCommandAt = LocalDateTime.now();
    }

    /** Marca um reenvio do comando pendente (incrementa tentativa e atualiza o relógio). */
    public void markRetry() {
        this.retryCount++;
        this.lastCommandAt = LocalDateTime.now();
    }

    /** Limpa a pendência de comando (resposta recebida, pausa humana ou saga encerrada). */
    public void clearPendingCommand() {
        this.pendingCommand = null;
        this.pendingPayloadJson = null;
        this.retryCount = 0;
        this.lastCommandAt = null;
    }

    public boolean hasPendingCommand() {
        return pendingCommand != null;
    }
}
