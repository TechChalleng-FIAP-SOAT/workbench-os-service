package com.workbench.workshop.domain.serviceorder;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceOrderTest {

    private ServiceOrder newOrder() {
        return ServiceOrder.create("52998224725", "abc1234", List.of("TROCA_OLEO"));
    }

    @Test
    void create_startsInRecebida_andNormalizesFields() {
        ServiceOrder order = newOrder();

        assertNotNull(order.getId());
        assertEquals(OrderServiceStatusEnum.RECEBIDA, order.getStatus());
        assertEquals("ABC1234", order.getPlacaVeiculo());
        assertEquals("52998224725", order.getCpfCnpj());
        assertEquals(List.of("TROCA_OLEO"), order.getListService());
    }

    @Test
    void create_requiresAtLeastOneServiceType() {
        assertThrows(IllegalArgumentException.class,
                () -> ServiceOrder.create("529", "ABC1234", List.of()));
    }

    @Test
    void advanceTo_followsAllowedTransition() {
        ServiceOrder order = newOrder();

        order.advanceTo(OrderServiceStatusEnum.EM_DIAGNOSTICO);

        assertEquals(OrderServiceStatusEnum.EM_DIAGNOSTICO, order.getStatus());
    }

    @Test
    void advanceTo_rejectsInvalidTransition() {
        ServiceOrder order = newOrder();

        assertThrows(InvalidStatusTransitionException.class,
                () -> order.advanceTo(OrderServiceStatusEnum.ENTREGUE));
    }

    @Test
    void reject_setsStatusAndReason() {
        ServiceOrder order = newOrder();
        order.advanceTo(OrderServiceStatusEnum.EM_DIAGNOSTICO);
        order.advanceTo(OrderServiceStatusEnum.AGUARDANDO_APROVACAO);

        order.reject("Preço acima do esperado");

        assertEquals(OrderServiceStatusEnum.RECUSADA, order.getStatus());
        assertEquals("Preço acima do esperado", order.getRejectionReason());
    }

    @Test
    void reopen_fromRecusadaGoesBackToDiagnostico() {
        ServiceOrder order = newOrder();
        order.advanceTo(OrderServiceStatusEnum.EM_DIAGNOSTICO);
        order.advanceTo(OrderServiceStatusEnum.AGUARDANDO_APROVACAO);
        order.reject("motivo");

        order.reopen();

        assertEquals(OrderServiceStatusEnum.EM_DIAGNOSTICO, order.getStatus());
    }
}
