package com.workbench.workshop.infrastructure.security;

import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.util.Date;
import java.util.List;

/**
 * Emite um JWT de SERVIÇO (service account) para as chamadas internas que o OS
 * Service faz ao Customer Service. Agora que todos os serviços validam o token
 * (defesa em profundidade), as chamadas servidor-a-servidor também precisam de um
 * Authorization válido — inclusive em threads assíncronas (consumer da Saga,
 * notificação), onde não há o token do usuário da request.
 *
 * <p>O token é de staff (sem claim {@code clientId}, roles ADMIN/TECHNICIAN), assinado em RS256
 * com a chave privada <b>do os-service</b> ({@code kid=os-service}), diferente da chave da Lambda:
 * só o customer-service confia nela, então este serviço não consegue emitir token aceito pelos
 * outros. Curta duração; gerado sob demanda a cada chamada.
 */
@Component
public class ServiceTokenProvider {

    private static final long TTL_MILLIS = 60_000L; // 1 min — suficiente p/ a chamada
    private final PrivateKey key;

    public ServiceTokenProvider(JwtProperties props) {
        this.key = RsaKeys.privateKey(props.getServicePrivateKey());
    }

    /** Token "Bearer ..." de serviço (staff) para o header Authorization. */
    public String bearerToken() {
        long now = System.currentTimeMillis();
        String jwt = Jwts.builder()
                .header().keyId(JwtService.SERVICE_KEY_ID).and()
                .subject("os-service")
                .claim("roles", List.of("ADMIN", "TECHNICIAN"))
                .issuedAt(new Date(now))
                .expiration(new Date(now + TTL_MILLIS))
                .signWith(key)
                .compact();
        return "Bearer " + jwt;
    }
}
