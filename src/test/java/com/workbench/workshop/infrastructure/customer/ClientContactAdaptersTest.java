package com.workbench.workshop.infrastructure.customer;

import com.workbench.workshop.infrastructure.security.JwtProperties;
import com.workbench.workshop.infrastructure.security.ServiceTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Cobre os adapters de contato do cliente. Usa MockRestServiceServer para simular
 * as respostas do Customer Service sem subir servidor.
 */
class ClientContactAdaptersTest {

    private final CustomerClientProperties props = props("http://customer");
    private final ServiceTokenProvider serviceTokenProvider =
            new ServiceTokenProvider(new JwtProperties("akmsdkmasfnjsgknakmsdkmasdkmsadfkmdsakmdkmsadsa", 3600000L));

    private static CustomerClientProperties props(String url) {
        var p = new CustomerClientProperties();
        p.setBaseUrl(url);
        return p;
    }

    private CustomerRestContactAdapter adapterWith(RestClient.Builder builder) {
        return new CustomerRestContactAdapter(builder, props, serviceTokenProvider);
    }

    @Test
    void noopReturnsEmpty() {
        assertTrue(new NoopClientContactAdapter().findEmailByDocument("52998224725").isEmpty());
    }

    @Test
    void restReturnsEmailWhenPresent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://customer/api/clients/document/52998224725"))
                .andRespond(withSuccess("{\"id\":1,\"name\":\"MARIA\",\"email\":\"maria@x.com\",\"active\":true}",
                        MediaType.APPLICATION_JSON));

        Optional<String> email = adapterWith(builder).findEmailByDocument("52998224725");

        assertEquals("maria@x.com", email.orElse(null));
        server.verify();
    }

    @Test
    void restReturnsEmptyWhenNoEmailField() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://customer/api/clients/document/52998224725"))
                .andRespond(withSuccess("{\"id\":1,\"name\":\"MARIA\",\"active\":true}",
                        MediaType.APPLICATION_JSON));

        assertTrue(adapterWith(builder).findEmailByDocument("52998224725").isEmpty());
    }

    @Test
    void restReturnsEmptyOn404() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://customer/api/clients/document/00000000000"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertTrue(adapterWith(builder).findEmailByDocument("00000000000").isEmpty());
    }

    @Test
    void restReturnsEmptyOnConnectionFailure() {
        var adapter = new CustomerRestContactAdapter(RestClient.builder(), props("http://localhost:59999"),
                serviceTokenProvider);
        assertTrue(adapter.findEmailByDocument("52998224725").isEmpty());
    }
}
