package com.workbench.workshop.infrastructure.customer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Fornece o {@link RestClient.Builder} usado pelos adapters REST do Customer
 * Service. No Spring Boot 4 o {@code RestClient.Builder} não é mais exposto
 * como bean por autoconfiguração, então o declaramos explicitamente aqui.
 *
 * <p>Ativo sob a mesma condição dos adapters ({@code customer.validation.enabled=true}),
 * de modo que o builder existe exatamente quando é necessário. Já vem com timeout de
 * conexão e de leitura ({@code customer.validation.connect-timeout}/{@code read-timeout}).
 */
@Configuration
@ConditionalOnProperty(prefix = "customer.validation", name = "enabled", havingValue = "true")
public class RestClientConfig {

    @Bean
    @ConditionalOnMissingBean
    public RestClient.Builder restClientBuilder(CustomerClientProperties props) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(props.getConnectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(props.getReadTimeout());
        return RestClient.builder().requestFactory(requestFactory);
    }
}
