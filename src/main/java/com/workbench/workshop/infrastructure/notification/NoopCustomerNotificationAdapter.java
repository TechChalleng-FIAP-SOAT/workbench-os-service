package com.workbench.workshop.infrastructure.notification;

import com.workbench.workshop.application.serviceorder.port.out.CustomerNotificationPort;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback de notificação ao cliente quando a mensageria está desligada
 * ({@code messaging.enabled=false}, default). Apenas loga — permite rodar o
 * OS Service e os testes sem broker.
 */
@Component
@ConditionalOnProperty(prefix = "messaging", name = "enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class NoopCustomerNotificationAdapter implements CustomerNotificationPort {

    @Override
    public void notifyCustomer(ServiceOrder order) {
        log.debug("[noop] notificação ao cliente desligada: os={}, status={}",
                order.getId(), order.getStatus());
    }
}
