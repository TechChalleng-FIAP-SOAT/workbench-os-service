package com.workbench.workshop.domain.serviceorder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderStatusLabelTest {

    @Test
    void mapsEnumToFriendlyLabel() {
        assertEquals("Recebida", OrderStatusLabel.of(OrderServiceStatusEnum.RECEBIDA));
        assertEquals("Aguardando Aprovacao", OrderStatusLabel.of(OrderServiceStatusEnum.AGUARDANDO_APROVACAO));
        assertEquals("Entregue", OrderStatusLabel.of(OrderServiceStatusEnum.ENTREGUE));
    }

    @Test
    void mapsStringValueToLabel() {
        assertEquals("Em Execucao", OrderStatusLabel.of("EM_EXECUCAO"));
    }

    @Test
    void returnsRawValueForUnknownString() {
        assertEquals("DESCONHECIDO", OrderStatusLabel.of("DESCONHECIDO"));
    }

    @Test
    void allowedTransitionsAreEnforced() {
        assertTrue(OrderServiceStatusEnum.RECEBIDA.canTransitionTo(OrderServiceStatusEnum.EM_DIAGNOSTICO));
        assertFalse(OrderServiceStatusEnum.RECEBIDA.canTransitionTo(OrderServiceStatusEnum.ENTREGUE));
        assertTrue(OrderServiceStatusEnum.ENTREGUE.getAllowedTransitions().isEmpty());
    }

    @Test
    void paymentFlowTransitions() {
        // FINALIZADA → PAGAMENTO_PENDENTE → PAGO → ENTREGUE (ADR-0005)
        assertTrue(OrderServiceStatusEnum.FINALIZADA.canTransitionTo(OrderServiceStatusEnum.PAGAMENTO_PENDENTE));
        assertTrue(OrderServiceStatusEnum.PAGAMENTO_PENDENTE.canTransitionTo(OrderServiceStatusEnum.PAGO));
        assertTrue(OrderServiceStatusEnum.PAGO.canTransitionTo(OrderServiceStatusEnum.ENTREGUE));
        // Não pode pular o pagamento: FINALIZADA direto para ENTREGUE é inválido.
        assertFalse(OrderServiceStatusEnum.FINALIZADA.canTransitionTo(OrderServiceStatusEnum.ENTREGUE));
    }

    @Test
    void mapsNewPaymentLabels() {
        assertEquals("Pagamento Pendente", OrderStatusLabel.of(OrderServiceStatusEnum.PAGAMENTO_PENDENTE));
        assertEquals("Pago", OrderStatusLabel.of(OrderServiceStatusEnum.PAGO));
    }
}
