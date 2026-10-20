package com.workbench.workshop.infrastructure.notification;

import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LoggingOrderNotificationAdapterTest {

    private final LoggingOrderNotificationAdapter adapter = new LoggingOrderNotificationAdapter();

    @Test
    void notifyDoesNotThrow() {
        ServiceOrder order = ServiceOrder.create("52998224725", "ABC1234", List.of("TROCA_OLEO"));
        assertDoesNotThrow(() -> adapter.notifyStatusChange(order));
    }
}
