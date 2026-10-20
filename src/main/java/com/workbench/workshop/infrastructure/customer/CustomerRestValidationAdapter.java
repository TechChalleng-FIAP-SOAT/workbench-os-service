package com.workbench.workshop.infrastructure.customer;

import com.workbench.workshop.application.serviceorder.port.out.CustomerValidationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Valida cliente e veículo chamando o Customer Service por REST (ADR-0003: nenhum
 * serviço acessa o banco de outro). Ativo com {@code customer.validation.enabled=true}.
 *
 * <ul>
 *   <li>cliente: {@code GET /api/clients/document/{cpfCnpj}}</li>
 *   <li>veículo: {@code GET /api/vehicles/plate/{placa}}</li>
 * </ul>
 * 404 vira {@link CustomerValidationException} (erro de negócio). Outras falhas de
 * comunicação sobem como {@link IllegalStateException} (indisponibilidade).
 */
@Component
@ConditionalOnProperty(prefix = "customer.validation", name = "enabled", havingValue = "true")
@Slf4j
public class CustomerRestValidationAdapter implements CustomerValidationPort {

    private final RestClient restClient;

    public CustomerRestValidationAdapter(RestClient.Builder builder, CustomerClientProperties props) {
        this.restClient = builder.baseUrl(props.getBaseUrl()).build();
    }

    @Override
    public void validateClientExists(String cpfCnpj) {
        get("/api/clients/document/{document}", cpfCnpj,
                "Cliente não encontrado para o documento: " + cpfCnpj);
    }

    @Override
    public void validateVehicleExists(String placa) {
        get("/api/vehicles/plate/{plate}", placa,
                "Veículo não encontrado para a placa: " + placa);
    }

    private void get(String uriTemplate, String pathVar, String notFoundMessage) {
        try {
            restClient.get().uri(uriTemplate, pathVar).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new CustomerValidationException(notFoundMessage);
            }
            throw new IllegalStateException("Falha ao validar no Customer Service: " + e.getMessage(), e);
        }
    }
}
