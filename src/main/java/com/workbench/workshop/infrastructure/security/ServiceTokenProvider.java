package com.workbench.workshop.infrastructure.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Emite um JWT de SERVIÇO (service account) para as chamadas internas que o OS
 * Service faz ao Customer Service. Agora que todos os serviços validam o token
 * (defesa em profundidade), as chamadas servidor-a-servidor também precisam de um
 * Authorization válido — inclusive em threads assíncronas (consumer da Saga,
 * notificação), onde não há o token do usuário da request.
 *
 * <p>O token é de staff (sem claim {@code clientId}, roles ADMIN/TECHNICIAN),
 * assinado com o mesmo {@code JWT_SECRET} compartilhado. Curta duração; gerado sob
 * demanda a cada chamada (barato, evita lidar com expiração/cache).
 */
@Component
public class ServiceTokenProvider {

    private static final long TTL_MILLIS = 60_000L; // 1 min — suficiente p/ a chamada
    private final SecretKey key;

    public ServiceTokenProvider(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** Token "Bearer ..." de serviço (staff) para o header Authorization. */
    public String bearerToken() {
        long now = System.currentTimeMillis();
        String jwt = Jwts.builder()
                .subject("os-service")
                .claim("roles", List.of("ADMIN", "TECHNICIAN"))
                .issuedAt(new Date(now))
                .expiration(new Date(now + TTL_MILLIS))
                .signWith(key)
                .compact();
        return "Bearer " + jwt;
    }
}
