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

    /**
     * Notifica o cliente com o LINK de pagamento (checkout) quando a cobrança é
     * criada. Assim ele paga sem precisar chamar nenhuma API. Best-effort.
     */
    void notifyPaymentLink(ServiceOrder order, String checkoutUrl);

    /**
     * Avisa o cliente que o pagamento <b>não foi aprovado</b>. A OS não muda de status (segue em
     * {@code PAGAMENTO_PENDENTE}), então sem este aviso ele só descobriria ao tentar de novo.
     * Best-effort.
     */
    void notifyPaymentFailed(ServiceOrder order);
}
