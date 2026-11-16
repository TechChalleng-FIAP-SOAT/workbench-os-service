package com.workbench.workshop.infrastructure.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Chaves do JWT (RS256). {@code jwt.public-key}: chave pública da Lambda de autenticação (a
 * mesma em todos os serviços e no authorizer). {@code jwt.service-private-key} (só no os-service): assina o token de serviço.
 */
@Configuration
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class JwtProperties {
    @Value("${jwt.public-key}") private String publicKey;
    /** Chave pública do token de serviço do os-service; vazia = não aceito neste serviço. */
    @Value("${jwt.service-public-key:}") private String servicePublicKey;
    /** Chave privada do token de serviço (só o os-service assina; o customer verifica). */
    @Value("${jwt.service-private-key}") private String servicePrivateKey;
}
