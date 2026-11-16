package com.workbench.workshop.infrastructure.scheduling;

import org.springframework.beans.factory.annotation.Value;
import com.workbench.workshop.application.saga.SagaOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Reenvia comandos pendentes da Saga que passaram do timeout (comando/resposta
 * perdido), delegando ao {@link SagaOrchestrator}. ShedLock garante uma única
 * execução por janela entre réplicas (HPA). Ativo só com a mensageria ligada.
 */
@Component
@ConditionalOnProperty(prefix = "messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SagaTimeoutScheduler {

    private final SagaOrchestrator sagaOrchestrator;

    /** Timeout de etapa em segundos (padrão 60s): comando sem resposta é reenviado. */
    @Value("${saga.step-timeout-seconds:60}")
    private long stepTimeoutSeconds;

    @Scheduled(fixedDelayString = "${saga.timeout-poll-ms:30000}")
    @SchedulerLock(name = "saga-timeout-resend", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
    public void resendTimedOutCommands() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(stepTimeoutSeconds);
        for (UUID osId : sagaOrchestrator.findTimedOut(threshold)) {
            try {
                sagaOrchestrator.resendTimedOut(osId, threshold);
            } catch (RuntimeException e) {
                // Falha (ex.: conflito com uma resposta chegando agora) só desta OS: as demais
                // seguem; esta volta na próxima janela.
                log.warn("Falha ao reenviar comando por timeout: os={}: {}", osId, e.getMessage());
            }
        }
    }
}
