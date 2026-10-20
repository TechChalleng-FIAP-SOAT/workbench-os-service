package com.workbench.workshop.infrastructure.notification;

import com.workbench.workshop.application.serviceorder.port.out.OrderNotificationPort;
import com.workbench.workshop.domain.serviceorder.OrderStatusLabel;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback de notificação quando a mensageria está desligada
 * ({@code messaging.enabled=false}, o default) — registra a mudança de status em
 * log estruturado, permitindo rodar o serviço sem broker (dev/testes).
 *
 * <p>Quando {@code messaging.enabled=true}, o {@link SnsOrderEventPublisher} assume
 * e publica o evento no tópico SNS da Saga.
 */
@Component
@ConditionalOnProperty(prefix = "messaging", name = "enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class LoggingOrderNotificationAdapter implements OrderNotificationPort {

    @Override
    public void notifyStatusChange(ServiceOrder order) {
        log.info("OS status change: id={}, status={} ({}), document={}, plate={}",
                order.getId(),
                order.getServiceStatus(),
                OrderStatusLabel.of(order.getStatus()),
                order.getCpfCnpj(),
                order.getPlacaVeiculo());
    }
}
