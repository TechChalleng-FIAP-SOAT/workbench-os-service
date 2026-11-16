package com.workbench.workshop.domain.saga;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Estado persistido da Saga de uma OS (orquestração — ADR-0006/0005).
 *
 * <p>Guarda o passo atual e os passos já concluídos com sucesso — é o
 * {@code completedSteps} que permite ao orquestrador saber o que compensar quando
 * uma etapa falha. Sobrevive a reinício do serviço (persistido no Postgres).
 */
@Getter
public class SagaState {

    private Long id;
    private UUID serviceOrderId;
    /**
     * Rodada da Saga: vai no {@code sagaId} de cada comando e volta na resposta. Um novo
     * diagnóstico abre uma rodada nova, e as respostas da anterior são descartadas.
     */
    private UUID sagaId;
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
        s.sagaId = UUID.randomUUID();
        s.step = SagaStep.INICIADA;
        return s;
    }

    public static SagaState reconstitute(Long id, UUID serviceOrderId, SagaStep step,
                                         List<String> completedSteps, String failureReason,
                                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        return reconstitute(id, serviceOrderId, serviceOrderId, step, completedSteps, failureReason,
                null, null, 0, null, createdAt, updatedAt);
    }

    /** Sem {@code sagaId} explícito, a rodada é o id da OS (como era antes da V9). */
    public static SagaState reconstitute(Long id, UUID serviceOrderId, SagaStep step,
                                         List<String> completedSteps, String failureReason,
                                         String pendingCommand, String pendingPayloadJson,
                                         int retryCount, LocalDateTime lastCommandAt,
                                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        return reconstitute(id, serviceOrderId, serviceOrderId, step, completedSteps, failureReason,
                pendingCommand, pendingPayloadJson, retryCount, lastCommandAt, createdAt, updatedAt);
    }

    public static SagaState reconstitute(Long id, UUID serviceOrderId, UUID sagaId, SagaStep step,
                                         List<String> completedSteps, String failureReason,
                                         String pendingCommand, String pendingPayloadJson,
                                         int retryCount, LocalDateTime lastCommandAt,
                                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        SagaState s = new SagaState();
        s.id = id;
        s.serviceOrderId = serviceOrderId;
        s.sagaId = sagaId != null ? sagaId : serviceOrderId;
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

    /**
     * Recomeça uma Saga que falhou (ex.: faltou estoque na reserva) para um novo
     * diagnóstico da mesma OS. Só a Saga encerrada em {@link SagaStep#FALHOU} pode
     * recomeçar: com ela em andamento, um segundo diagnóstico duplicaria reserva/orçamento.
     */
    public void restart() {
        if (step != SagaStep.FALHOU) {
            throw new IllegalStateException("A Saga desta OS está em andamento (passo " + step
                    + "); só é possível refazer o diagnóstico depois de uma falha.");
        }
        completedSteps.clear();
        failureReason = null;
        clearPendingCommand();
        sagaId = UUID.randomUUID();
        step = SagaStep.INICIADA;
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

    /** Encerra a Saga com falha. Sem comando pendente: o job de timeout não a reprocessa. */
    public void markFailed(String reason) {
        clearPendingCommand();
        this.step = SagaStep.FALHOU;
        this.failureReason = reason;
    }

    /** Registra uma falha sem mudar de passo (ex.: pagamento recusado — a Saga segue aguardando). */
    public void recordFailure(String reason) {
        this.failureReason = reason;
    }

    public void clearFailure() {
        this.failureReason = null;
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

    /** True se a resposta é de outra rodada (ex.: reentrega do SQS depois de um novo diagnóstico). */
    public boolean isFromAnotherRun(String replySagaId) {
        return replySagaId != null && !replySagaId.equals(sagaId.toString());
    }
}
