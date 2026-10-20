package com.workbench.workshop.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Validação de JWT no OS Service.
 *
 * <p>Diferente do monólito, este serviço NÃO emite tokens (o login de staff e de
 * cliente vivem no Customer Service / Lambda de auth). Ele apenas verifica a
 * assinatura HS256 com o JWT_SECRET compartilhado e lê as claims (subject, roles,
 * clientId) para autorizar as requisições que chegam pelo gateway.
 */
@Service
@Slf4j
public class JwtService {

    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** Faz o parse e verifica o token; vazio quando inválido/expirado. */
    public Optional<Claims> tryParseClaims(String token) {
        try {
            return Optional.of(getClaims(token));
        } catch (Exception e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Um token de cliente (CPF), emitido pela Lambda de auth, carrega a claim clientId. */
    public boolean isClientToken(Claims claims) {
        return claims.get("clientId") != null;
    }

    /** Lê as roles das claims; usado para popular as authorities do contexto de segurança. */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public boolean isExpired(Claims claims) {
        return claims.getExpiration() != null && claims.getExpiration().before(new Date());
    }

    private Claims getClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
