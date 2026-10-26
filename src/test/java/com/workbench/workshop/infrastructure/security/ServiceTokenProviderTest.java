package com.workbench.workshop.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceTokenProviderTest {

    private static final String SECRET = "akmsdkmasfnjsgknakmsdkmasdkmsadfkmdsakmdkmsadsa";
    private final ServiceTokenProvider provider =
            new ServiceTokenProvider(new JwtProperties(SECRET, 3600000L));

    @Test
    void emitsSignedStaffServiceToken() {
        String header = provider.bearerToken();
        assertTrue(header.startsWith("Bearer "));

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(header.substring(7)).getPayload();

        assertEquals("os-service", claims.getSubject());
        assertNull(claims.get("clientId"), "token de serviço é staff (sem clientId)");
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles");
        assertTrue(roles.contains("ADMIN"));
    }
}
