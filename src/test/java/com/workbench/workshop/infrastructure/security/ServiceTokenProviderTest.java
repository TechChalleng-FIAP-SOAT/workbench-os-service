package com.workbench.workshop.infrastructure.security;

import com.workbench.workshop.support.TestKeys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.ProtectedHeader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceTokenProviderTest {

    private final ServiceTokenProvider provider = new ServiceTokenProvider(TestKeys.jwtProperties());

    @Test
    void emitsServiceTokenSignedWithTheServiceKey() {
        String header = provider.bearerToken();
        assertTrue(header.startsWith("Bearer "));

        var jws = Jwts.parser().verifyWith(RsaKeys.publicKey(TestKeys.SERVICE_PUBLIC)).build()
                .parseSignedClaims(header.substring(7));
        Claims claims = jws.getPayload();

        assertEquals(JwtService.SERVICE_KEY_ID, ((ProtectedHeader) jws.getHeader()).getKeyId());
        assertEquals("RS256", jws.getHeader().getAlgorithm());
        assertEquals("os-service", claims.getSubject());
        assertNull(claims.get("clientId"), "token de serviço é staff (sem clientId)");
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles");
        assertTrue(roles.contains("ADMIN"));
    }

    @Test
    void serviceTokenIsRejectedWhereTheServiceKeyIsNotTrusted() {
        // Só o customer-service confia na chave do os-service: billing/execution recusam.
        JwtService withoutServiceKey = new JwtService(new JwtProperties(TestKeys.AUTH_PUBLIC, "", ""));
        JwtService withServiceKey = new JwtService(TestKeys.jwtProperties());
        String token = provider.bearerToken().substring(7);

        assertTrue(withoutServiceKey.tryParseClaims(token).isEmpty());
        assertTrue(withServiceKey.tryParseClaims(token).isPresent());
    }
}
