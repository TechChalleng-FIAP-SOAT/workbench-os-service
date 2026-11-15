package com.workbench.workshop.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.CrossOriginEmbedderPolicyHeaderWriter.CrossOriginEmbedderPolicy;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final JsonSecurityErrorHandler errorHandler;

    public SecurityConfig(JwtFilter jwtFilter, JsonSecurityErrorHandler errorHandler) {
        this.jwtFilter = jwtFilter;
        this.errorHandler = errorHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(h -> h
                        .frameOptions(fo -> fo.deny())                 // anti-clickjacking (OWASP A05)
                        .contentTypeOptions(c -> {})                  // X-Content-Type-Options: nosniff
                        // Isolamento cross-origin (ZAP 90004 / Spectre): a API não é embutida por outros sites.
                        .crossOriginResourcePolicy(c -> c.policy(CrossOriginResourcePolicy.SAME_ORIGIN))
                        .crossOriginOpenerPolicy(c -> c.policy(CrossOriginOpenerPolicy.SAME_ORIGIN))
                        .crossOriginEmbedderPolicy(c -> c.policy(CrossOriginEmbedderPolicy.REQUIRE_CORP)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 401/403 em JSON (mesmo formato dos demais erros), não resposta vazia.
                .exceptionHandling(e -> e.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Rotas do cliente autenticado (CPF) sobre as próprias OS — o dono é
                        // conferido no caso de uso (Requester): consultar e decidir o orçamento.
                        .requestMatchers(HttpMethod.GET, "/order/document/**").hasAnyRole("CLIENT", "USER", "ADMIN", "TECHNICIAN")
                        .requestMatchers(HttpMethod.POST, "/order/*/decision", "/order/*/payment-retry")
                            .hasAnyRole("CLIENT", "ADMIN", "TECHNICIAN")
                        .anyRequest().hasAnyRole("ADMIN", "TECHNICIAN"))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
