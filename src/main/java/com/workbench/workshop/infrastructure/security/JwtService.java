package com.workbench.workshop.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.UnsupportedJwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Validação de JWT no OS Service. Os tokens de usuário vêm da Lambda de auth; este serviço só verifica a assinatura e lê as claims (subject, roles, clientId). O token de serviço que ele próprio emite para o customer-service fica no {@link ServiceTokenProvider}.
 *
 * <p>Tokens RS256: quem assina tem a chave privada, e este serviço só tem as públicas — não
 * consegue emitir token, só verificar. O cabeçalho {@code kid} diz qual chave usar:
 * {@value #AUTH_KEY_ID} (Lambda de login, aceito sempre) e {@value #SERVICE_KEY_ID} (token
 * de serviço do os-service, aceito só onde {@code jwt.service-public-key} está configurada).
 * {@code kid} desconhecido é recusado.
 */
@Service
@Slf4j
public class JwtService {

    /** Chave da Lambda de autenticação (tokens de cliente e de staff). */
    public static final String AUTH_KEY_ID = "workbench-auth";
    /** Chave do os-service (token de serviço nas chamadas ao customer-service). */
    public static final String SERVICE_KEY_ID = "os-service";

    private final JwtParser parser;

    public JwtService(JwtProperties props) {
        Map<String, Key> trusted = new HashMap<>();
        trusted.put(AUTH_KEY_ID, RsaKeys.publicKey(props.getPublicKey()));
        if (props.getServicePublicKey() != null && !props.getServicePublicKey().isBlank()) {
            trusted.put(SERVICE_KEY_ID, RsaKeys.publicKey(props.getServicePublicKey()));
        }
        Map<String, Key> keys = Map.copyOf(trusted);
        this.parser = Jwts.parser()
                .keyLocator(header -> {
                    String kid = header instanceof ProtectedHeader protectedHeader ? protectedHeader.getKeyId() : null;
                    Key key = kid == null ? null : keys.get(kid);
                    if (key == null) {
                        throw new UnsupportedJwtException("Chave do token não é confiável aqui: kid=" + kid);
                    }
                    return key;
                })
                .build();
    }

    /** Faz o parse e verifica o token; vazio quando inválido/expirado. */
    public Optional<Claims> tryParseClaims(String token) {
        try {
            return Optional.of(parser.parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Um token de cliente (CPF), emitido pela Lambda de auth, carrega a claim clientId. */
    public boolean isClientToken(Claims claims) {
        return claims.get("clientId") != null;
    }

    /** Lê as roles das claims para popular as authorities do contexto de segurança. */
    public List<String> extractRoles(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
