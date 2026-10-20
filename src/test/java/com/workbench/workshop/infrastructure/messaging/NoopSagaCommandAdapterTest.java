package com.workbench.workshop.infrastructure.messaging;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class NoopSagaCommandAdapterTest {

    @Test
    void sendIsNoopAndNeverThrows() {
        var adapter = new NoopSagaCommandAdapter();
        UUID id = UUID.randomUUID();
        assertDoesNotThrow(() -> adapter.send("ComandoReservarEstoque", id, id, Map.of("k", "v")));
    }
}
