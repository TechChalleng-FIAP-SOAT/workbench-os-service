package com.workbench.workshop.infrastructure.monitoring;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @Test
    void propagatesMdcToWorker() {
        MDC.put("request_id", "abc-123");
        AtomicReference<String> seen = new AtomicReference<>();

        Runnable decorated = decorator.decorate(() -> seen.set(MDC.get("request_id")));
        decorated.run();

        assertEquals("abc-123", seen.get());
        MDC.clear();
    }

    @Test
    void clearsWhenNoContext() {
        MDC.clear();
        AtomicReference<String> seen = new AtomicReference<>("initial");

        Runnable decorated = decorator.decorate(() -> seen.set(MDC.get("request_id")));
        decorated.run();

        assertNull(seen.get());
    }
}
