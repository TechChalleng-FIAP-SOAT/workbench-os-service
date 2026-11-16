package com.workbench.workshop.infrastructure.customer;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Config do cliente REST para o Customer Service. */
@Component
@ConfigurationProperties(prefix = "customer.validation")
@Getter
@Setter
public class CustomerClientProperties {
    /** Liga a validação REST (false = usa o fallback no-op). */
    private boolean enabled = false;
    /** URL base do Customer Service (ex.: http://customer-service:8080). */
    private String baseUrl = "";
    /** Tempo máximo para abrir a conexão com o Customer Service. */
    private Duration connectTimeout = Duration.ofSeconds(2);
    /** Tempo máximo esperando a resposta (sem ele, um Customer travado prende a requisição). */
    private Duration readTimeout = Duration.ofSeconds(5);
}
