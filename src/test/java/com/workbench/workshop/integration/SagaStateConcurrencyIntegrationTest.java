package com.workbench.workshop.integration;

import com.workbench.workshop.application.saga.port.out.SagaStateRepository;
import com.workbench.workshop.domain.saga.SagaState;
import com.workbench.workshop.domain.saga.SagaStep;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Duas transações que leram o mesmo estado da Saga não gravam as duas (trava otimista):
 * é o que impede aprovar e recusar o mesmo orçamento ao mesmo tempo.
 */
class SagaStateConcurrencyIntegrationTest extends IntegrationTestBase {

    @Autowired private SagaStateRepository sagaStateRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void secondConcurrentWriteFails() {
        UUID osId = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        TransactionTemplate otherTx = new TransactionTemplate(transactionManager);
        otherTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        tx.executeWithoutResult(status -> {
            SagaState saga = SagaState.start(osId);
            saga.advanceTo(SagaStep.AGUARDANDO_APROVACAO, "ORCAMENTO");
            sagaStateRepository.save(saga);
        });

        assertThrows(OptimisticLockingFailureException.class, () -> tx.executeWithoutResult(status -> {
            // "Aprovação": lê o estado...
            SagaState approving = sagaStateRepository.findByServiceOrderId(osId).orElseThrow();

            // ...enquanto a "recusa", em outra transação, lê, grava e faz commit.
            otherTx.executeWithoutResult(inner -> {
                SagaState rejecting = sagaStateRepository.findByServiceOrderId(osId).orElseThrow();
                rejecting.markCompensating("orçamento recusado");
                sagaStateRepository.save(rejecting);
            });

            approving.advanceTo(SagaStep.AGUARDANDO_PAGAMENTO, "APROVACAO");
            sagaStateRepository.save(approving);
        }));

        SagaState stored = tx.execute(status -> sagaStateRepository.findByServiceOrderId(osId).orElseThrow());
        assertEquals(SagaStep.COMPENSANDO, stored.getStep());
    }
}
