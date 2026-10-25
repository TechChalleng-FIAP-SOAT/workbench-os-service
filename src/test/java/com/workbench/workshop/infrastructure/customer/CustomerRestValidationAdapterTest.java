package com.workbench.workshop.infrastructure.customer;

import com.workbench.workshop.application.serviceorder.port.out.CustomerValidationPort.CustomerValidationException;
import com.workbench.workshop.infrastructure.security.JwtProperties;
import com.workbench.workshop.infrastructure.security.ServiceTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Cobre o adapter de validação REST (cliente/veículo) com MockRestServiceServer. */
class CustomerRestValidationAdapterTest {

    private final ServiceTokenProvider tokenProvider =
            new ServiceTokenProvider(new JwtProperties("akmsdkmasfnjsgknakmsdkmasdkmsadfkmdsakmdkmsadsa", 3600000L));

    private CustomerClientProperties props(String url) {
        var p = new CustomerClientProperties();
        p.setBaseUrl(url);
        return p;
    }

    private CustomerRestValidationAdapter adapter(RestClient.Builder b) {
        return new CustomerRestValidationAdapter(b, props("http://customer"), tokenProvider);
    }

    @Test
    void clientFound200DoesNotThrow() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://customer/api/clients/document/52998224725"))
                .andRespond(withSuccess());
        assertDoesNotThrow(() -> adapter(b).validateClientExists("52998224725"));
        server.verify();
    }

    @Test
    void client404ThrowsValidationException() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://customer/api/clients/document/00000000000"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThrows(CustomerValidationException.class,
                () -> adapter(b).validateClientExists("00000000000"));
    }

    @Test
    void vehicle404ThrowsValidationException() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://customer/api/vehicles/plate/ZZZ9999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThrows(CustomerValidationException.class,
                () -> adapter(b).validateVehicleExists("ZZZ9999"));
    }

    @Test
    void serverErrorThrowsIllegalState() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("http://customer/api/clients/document/52998224725"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThrows(IllegalStateException.class,
                () -> adapter(b).validateClientExists("52998224725"));
    }
}
