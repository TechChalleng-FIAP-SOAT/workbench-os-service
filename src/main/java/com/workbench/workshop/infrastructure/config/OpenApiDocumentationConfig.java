package com.workbench.workshop.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiDocumentationConfig {

    @Bean
    public OpenAPI osServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("OS Service API")
                .description("Ciclo de vida da Ordem de Servico e orquestracao da Saga (Tech Challenge Fase 4)")
                .version("1.0.0"));
    }
}
