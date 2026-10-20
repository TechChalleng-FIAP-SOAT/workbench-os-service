package com.workbench.workshop.infrastructure.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra o UpperCaseDeserializer como módulo Jackson, de modo que campos String
 * anotados com @UpperCase sejam normalizados (trim + maiúsculas) na desserialização.
 * O Spring Boot detecta beans do tipo com.fasterxml.jackson.databind.Module e os
 * registra automaticamente no ObjectMapper.
 */
@Configuration
public class JacksonUpperCaseConfig {

    @Bean
    public SimpleModule upperCaseModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, new UpperCaseDeserializer());
        return module;
    }
}
