package com.workbench.workshop.infrastructure.customer;

import com.workbench.workshop.application.serviceorder.port.out.ClientContactPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Fallback quando a integração com o Customer Service está desligada
 * ({@code customer.validation.enabled=false}, default). Não resolve e-mail —
 * permite rodar o OS Service e os testes sem o Customer Service no ar.
 */
@Component
@ConditionalOnProperty(prefix = "customer.validation", name = "enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class NoopClientContactAdapter implements ClientContactPort {

    @Override
    public Optional<String> findEmailByDocument(String cpfCnpj) {
        log.debug("[noop] resolução de e-mail do cliente desligada: {}", cpfCnpj);
        return Optional.empty();
    }
}
