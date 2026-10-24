package com.workbench.workshop.infrastructure.customer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Fornece o {@link RestClient.Builder} usado pelos adapters REST do Customer
 * Service. No Spring Boot 4 o {@code RestClient.Builder} não é mais exposto
 * como bean por autoconfiguração, então o declaramos explicitamente aqui.
 *
 * <p>Ativo sob a mesma condição dos adapters ({@code customer.validation.enabled=true}),
 * de modo que o builder existe exatamente quando é necessário.
 */
@Configuration
@ConditionalOnProperty(prefix = "customer.validation", name = "enabled", havingValue = "true")
public class RestClientConfig {

    @Bean
    @ConditionalOnMissingBean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
