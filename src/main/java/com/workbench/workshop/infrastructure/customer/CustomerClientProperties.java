package com.workbench.workshop.infrastructure.customer;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
}
