package com.workbench.workshop.domain.serviceorder;

import java.util.Map;

public final class OrderStatusLabel {

    private OrderStatusLabel() {}

    private static final Map<OrderServiceStatusEnum, String> LABELS = Map.ofEntries(
            Map.entry(OrderServiceStatusEnum.RECEBIDA, "Recebida"),
            Map.entry(OrderServiceStatusEnum.EM_DIAGNOSTICO, "Em Diagnostico"),
            Map.entry(OrderServiceStatusEnum.AGUARDANDO_APROVACAO, "Aguardando Aprovacao"),
            Map.entry(OrderServiceStatusEnum.APROVADO, "Aprovado"),
            Map.entry(OrderServiceStatusEnum.EM_EXECUCAO, "Em Execucao"),
            Map.entry(OrderServiceStatusEnum.FINALIZADA, "Finalizada"),
            Map.entry(OrderServiceStatusEnum.PAGAMENTO_PENDENTE, "Pagamento Pendente"),
            Map.entry(OrderServiceStatusEnum.PAGO, "Pago"),
            Map.entry(OrderServiceStatusEnum.ENTREGUE, "Entregue"),
            Map.entry(OrderServiceStatusEnum.RECUSADA, "Recusada")
    );

    public static String of(OrderServiceStatusEnum status) {
        return LABELS.getOrDefault(status, status.getStatus());
    }

    public static String of(String statusValue) {
        try {
            return of(OrderServiceStatusEnum.valueOf(statusValue));
        } catch (IllegalArgumentException e) {
            return statusValue;
        }
    }
}
