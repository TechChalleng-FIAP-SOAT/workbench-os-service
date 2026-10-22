package com.workbench.workshop.application.serviceorder.port.out;

import com.workbench.workshop.domain.serviceorder.ServiceOrder;

/**
 * Porta de notificação ao CLIENTE sobre a mudança de status da OS (paridade com o
 * monólito, que enviava e-mail formatado por status via SNS). Distinta da
 * {@link OrderNotificationPort}, que publica o evento técnico da Saga: esta
 * notifica a pessoa (e-mail legível). Best-effort — falhas não revertem o negócio.
 */
public interface CustomerNotificationPort {
    void notifyCustomer(ServiceOrder order);
}
