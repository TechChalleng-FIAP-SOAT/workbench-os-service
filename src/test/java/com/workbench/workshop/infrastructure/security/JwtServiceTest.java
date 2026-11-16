package com.workbench.workshop.infrastructure.security;

import com.workbench.workshop.support.TestKeys;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private PrivateKey key;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        key = TestKeys.authPrivateKey();
        jwtService = new JwtService(TestKeys.jwtProperties());
    }

    private String staffToken(long expiryOffsetMillis) {
        long now = System.currentTimeMillis();
        return TestKeys.authToken()
                .subject("admin@system.com")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryOffsetMillis))
                .signWith(key)
                .compact();
    }

    private String clientToken() {
        long now = System.currentTimeMillis();
        return TestKeys.authToken()
                .subject("52998224725")
                .claim("clientId", 1)
                .claim("roles", List.of("CLIENT"))
                .issuedAt(new Date(now))
                .expiration(new Date(now + 86400000L))
                .signWith(key)
                .compact();
    }

    @Test
    void parsesValidStaffToken() {
        Optional<Claims> claims = jwtService.tryParseClaims(staffToken(86400000L));

        assertTrue(claims.isPresent());
        assertEquals("admin@system.com", claims.get().getSubject());
        assertFalse(jwtService.isClientToken(claims.get()));
        assertEquals(List.of("ADMIN"), jwtService.extractRoles(claims.get()));
    }

    @Test
    void detectsClientToken() {
        Claims claims = jwtService.tryParseClaims(clientToken()).orElseThrow();

        assertTrue(jwtService.isClientToken(claims));
        assertEquals("52998224725", claims.getSubject());
    }

    @Test
    void rejectsTamperedToken() {
        assertTrue(jwtService.tryParseClaims("not.a.jwt").isEmpty());
    }

    @Test
    void rejectsExpiredToken() {
        // O parser já valida a expiração: token vencido não passa.
        assertTrue(jwtService.tryParseClaims(staffToken(-1000L)).isEmpty());
    }

    @Test
    void extractRolesEmptyWhenAbsent() {
        long now = System.currentTimeMillis();
        String token = TestKeys.authToken().subject("x")
                .expiration(new Date(now + 10000)).signWith(key).compact();

        Claims claims = jwtService.tryParseClaims(token).orElseThrow();
        assertTrue(jwtService.extractRoles(claims).isEmpty());
    }

    @Test
    void rejectsTokenSignedWithTheOldSharedSecret() {
        // HS256 com o antigo JWT_SECRET: quem tinha o segredo emitia token de ADMIN. Não vale mais.
        javax.crypto.SecretKey oldSecret = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                "akmsdkmasfnjsgknakmsdkmasdkmsadfkmdsakmdkmsadsa".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String hs256 = io.jsonwebtoken.Jwts.builder().header().keyId(JwtService.AUTH_KEY_ID).and()
                .subject("admin@workbench.dev").claim("roles", List.of("ADMIN"))
                .expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(oldSecret).compact();
        assertTrue(new JwtService(TestKeys.jwtProperties()).tryParseClaims(hs256).isEmpty());
    }

    @Test
    void rejectsUnknownKeyIdAndServiceTokenWhereNotTrusted() {
        JwtService onlyAuthKey = new JwtService(new JwtProperties(TestKeys.AUTH_PUBLIC, "", ""));
        String unknownKid = io.jsonwebtoken.Jwts.builder().header().keyId("outra-chave").and()
                .subject("x").expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(TestKeys.authPrivateKey()).compact();
        String serviceToken = io.jsonwebtoken.Jwts.builder().header().keyId(JwtService.SERVICE_KEY_ID).and()
                .subject("os-service").expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(TestKeys.privateKey(TestKeys.SERVICE_PRIVATE)).compact();

        assertTrue(onlyAuthKey.tryParseClaims(unknownKid).isEmpty());
        assertTrue(onlyAuthKey.tryParseClaims(serviceToken).isEmpty());
    }
}
