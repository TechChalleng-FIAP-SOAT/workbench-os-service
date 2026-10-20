package com.workbench.workshop.infrastructure.customer;

import com.workbench.workshop.application.serviceorder.port.out.CustomerValidationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback quando a validação no Customer Service está desligada
 * ({@code customer.validation.enabled=false}, default). Não valida nada — permite
 * rodar o OS Service e os testes sem o Customer Service no ar.
 */
@Component
@ConditionalOnProperty(prefix = "customer.validation", name = "enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class NoopCustomerValidationAdapter implements CustomerValidationPort {

    @Override
    public void validateClientExists(String cpfCnpj) {
        log.debug("[noop] validação de cliente desligada: {}", cpfCnpj);
    }

    @Override
    public void validateVehicleExists(String placa) {
        log.debug("[noop] validação de veículo desligada: {}", placa);
    }
}
