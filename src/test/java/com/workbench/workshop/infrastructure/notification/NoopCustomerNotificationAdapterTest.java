package com.workbench.workshop.infrastructure.notification;

import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class NoopCustomerNotificationAdapterTest {

    @Test
    void notifyCustomerIsNoOpAndDoesNotThrow() {
        var adapter = new NoopCustomerNotificationAdapter();
        ServiceOrder order = ServiceOrder.reconstitute(UUID.randomUUID(), "52998224725", "ABC1234",
                List.of("TROCA_OLEO"), "[TROCA_OLEO]", OrderServiceStatusEnum.RECEBIDA, null, null, null);
        assertDoesNotThrow(() -> adapter.notifyCustomer(order));
    }
}
