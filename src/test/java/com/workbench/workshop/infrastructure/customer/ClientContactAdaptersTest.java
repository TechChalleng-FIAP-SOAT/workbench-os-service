package com.workbench.workshop.infrastructure.customer;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cobre os adapters de contato do cliente (resolução de e-mail). O Noop sempre
 * retorna vazio; o REST lê o campo email do JSON do Customer Service.
 */
class ClientContactAdaptersTest {

    @Test
    void noopReturnsEmpty() {
        var adapter = new NoopClientContactAdapter();
        assertTrue(adapter.findEmailByDocument("52998224725").isEmpty());
    }

    @Test
    void restReturnsEmailFromCustomerResponse() {
        // RestClient apontando para um endpoint inexistente -> cai no catch e retorna vazio,
        // exercitando o tratamento best-effort do adapter.
        var props = new CustomerClientProperties();
        props.setBaseUrl("http://localhost:59999");
        var adapter = new CustomerRestContactAdapter(RestClient.builder(), props);

        Optional<String> email = adapter.findEmailByDocument("52998224725");

        assertTrue(email.isEmpty()); // falha de conexão -> vazio (não lança)
    }
}
