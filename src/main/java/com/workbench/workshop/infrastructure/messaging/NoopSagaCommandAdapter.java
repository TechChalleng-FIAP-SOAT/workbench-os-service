package com.workbench.workshop.infrastructure.messaging;

import com.workbench.workshop.application.saga.port.out.SagaCommandPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Fallback no-op do envio de comandos da Saga quando a mensageria está desligada
 * ({@code messaging.enabled=false}, default). Apenas registra em log, permitindo
 * rodar o serviço e os testes sem broker. Com messaging ligado, o
 * {@link SagaCommandPublisher} (SNS) é o bean ativo.
 */
@Component
@ConditionalOnProperty(prefix = "messaging", name = "enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class NoopSagaCommandAdapter implements SagaCommandPort {

    @Override
    public void send(String type, UUID sagaId, UUID serviceOrderId, Map<String, Object> payload) {
        log.info("[noop] Saga command (messaging desligado): type={}, serviceOrderId={}", type, serviceOrderId);
    }
}
