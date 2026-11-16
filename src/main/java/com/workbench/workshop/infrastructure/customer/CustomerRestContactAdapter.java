package com.workbench.workshop.infrastructure.customer;

import com.workbench.workshop.application.serviceorder.port.out.ClientContactPort;
import com.workbench.workshop.infrastructure.security.ServiceTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.Optional;

/**
 * Resolve o e-mail do cliente chamando o Customer Service por REST
 * ({@code GET /api/clients/document/{cpfCnpj}}), reaproveitando a mesma config
 * ({@code customer.validation.*}) do adapter de validação. Ativo quando
 * {@code customer.validation.enabled=true}.
 *
 * <p>Best-effort: qualquer falha de comunicação retorna vazio (a notificação é
 * um efeito colateral que não deve quebrar o fluxo de negócio).
 */
@Component
@ConditionalOnProperty(prefix = "customer.validation", name = "enabled", havingValue = "true")
@Slf4j
public class CustomerRestContactAdapter implements ClientContactPort {

    private final RestClient restClient;
    private final ServiceTokenProvider serviceTokenProvider;

    public CustomerRestContactAdapter(RestClient.Builder builder, CustomerClientProperties props,
                                      ServiceTokenProvider serviceTokenProvider) {
        this.restClient = builder.baseUrl(props.getBaseUrl()).build();
        this.serviceTokenProvider = serviceTokenProvider;
    }

    @Override
    public Optional<String> findEmailByDocument(String cpfCnpj) {
        try {
            Map<?, ?> body = restClient.get()
                    .uri("/api/clients/document/{document}", cpfCnpj)
                    .header(HttpHeaders.AUTHORIZATION, serviceTokenProvider.bearerToken())
                    .retrieve()
                    .body(Map.class);
            if (body == null) {
                return Optional.empty();
            }
            Object email = body.get("email");
            return Optional.ofNullable(email).map(Object::toString).filter(s -> !s.isBlank());
        } catch (RestClientException e) {
            log.warn("Não foi possível resolver e-mail do cliente {} no Customer Service: {}",
                    cpfCnpj, e.getMessage());
            return Optional.empty();
        }
    }
}
