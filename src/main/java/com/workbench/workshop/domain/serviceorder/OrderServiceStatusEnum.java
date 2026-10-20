package com.workbench.workshop.domain.serviceorder;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum OrderServiceStatusEnum {
    RECEBIDA("RECEBIDA"),
    EM_DIAGNOSTICO("EM_DIAGNOSTICO"),
    AGUARDANDO_APROVACAO("AGUARDANDO_APROVACAO"),
    APROVADO("APROVADO"),
    EM_EXECUCAO("EM_EXECUCAO"),
    FINALIZADA("FINALIZADA"),
    PAGAMENTO_PENDENTE("PAGAMENTO_PENDENTE"),
    PAGO("PAGO"),
    ENTREGUE("ENTREGUE"),
    RECUSADA("RECUSADA");

    private final String status;

    // Fluxo (ADR-0005): pagamento ocorre no fim; ENTREGUE só após PAGO.
    private static final Map<OrderServiceStatusEnum, List<OrderServiceStatusEnum>> ALLOWED_TRANSITIONS = Map.ofEntries(
            Map.entry(RECEBIDA, List.of(EM_DIAGNOSTICO)),
            Map.entry(EM_DIAGNOSTICO, List.of(AGUARDANDO_APROVACAO)),
            Map.entry(AGUARDANDO_APROVACAO, List.of(APROVADO, RECUSADA)),
            Map.entry(APROVADO, List.of(EM_EXECUCAO)),
            Map.entry(EM_EXECUCAO, List.of(FINALIZADA)),
            Map.entry(FINALIZADA, List.of(PAGAMENTO_PENDENTE)),
            Map.entry(PAGAMENTO_PENDENTE, List.of(PAGO)),
            Map.entry(PAGO, List.of(ENTREGUE)),
            Map.entry(RECUSADA, List.of(EM_DIAGNOSTICO))
    );

    public List<OrderServiceStatusEnum> getAllowedTransitions() {
        return ALLOWED_TRANSITIONS.getOrDefault(this, List.of());
    }

    public boolean canTransitionTo(OrderServiceStatusEnum target) {
        return getAllowedTransitions().contains(target);
    }
}
