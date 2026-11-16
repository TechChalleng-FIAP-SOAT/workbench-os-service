package com.workbench.workshop.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Executa efeitos externos (comando da Saga no SNS, e-mail ao cliente) só DEPOIS do
 * commit: assim uma resposta rápida nunca encontra a Saga ainda não gravada, e um
 * rollback não deixa mensagem enviada sobre algo que não existe. Sem transação
 * ativa (ex.: testes unitários), executa na hora.
 *
 * <p>Falha no efeito é logada, não propagada: o dado já foi gravado, e o comando
 * pendente da Saga é reenviado pelo job de timeout.
 */
@Slf4j
public final class AfterCommit {

    private AfterCommit() {}

    public static void run(String description, Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runSafely(description, action);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runSafely(description, action);
            }
        });
    }

    private static void runSafely(String description, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.error("Falha ao executar após o commit: {}", description, e);
        }
    }
}
