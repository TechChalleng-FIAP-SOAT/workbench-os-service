package com.workbench.workshop.application.serviceorder.port.out;

import com.workbench.workshop.domain.serviceorder.ServiceOrder;

/**
 * Porta de notificação de mudança de status da OS.
 *
 * <p>No monólito a notificação (OrderStatusNotificationService) resolvia nome do
 * cliente e dados do veículo consultando os módulos client/vehicle. Como esses
 * contextos migram para o Customer Service (Fase 4, Etapa 6), aqui a porta recebe
 * apenas a própria OS; o enriquecimento com dados de cliente/veículo passará a ser
 * responsabilidade do consumidor da mensagem de notificação quando a mensageria
 * (Etapa 2) estiver disponível.
 */
public interface OrderNotificationPort {
    void notifyStatusChange(ServiceOrder order);
}
