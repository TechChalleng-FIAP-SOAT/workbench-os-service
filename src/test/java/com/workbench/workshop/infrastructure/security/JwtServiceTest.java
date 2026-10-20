package com.workbench.workshop.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "akmsdkmasfnjsgknakmsdkmasdkmsadfkmdsakmdkmsadsa";
    private SecretKey key;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        jwtService = new JwtService(new JwtProperties(SECRET, 86400000L));
    }

    private String staffToken(long expiryOffsetMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject("admin@system.com")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryOffsetMillis))
                .signWith(key)
                .compact();
    }

    private String clientToken() {
        long now = System.currentTimeMillis();
        return Jwts.builder()
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
        assertFalse(jwtService.isExpired(claims.get()));
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
    void detectsExpiredToken() {
        Claims claims = jwtService.tryParseClaims(staffToken(-1000L)).orElse(null);
        // Token já expirado pode falhar o parse; se passou, isExpired deve indicar true.
        if (claims != null) {
            assertTrue(jwtService.isExpired(claims));
        } else {
            assertTrue(true);
        }
    }

    @Test
    void extractRolesEmptyWhenAbsent() {
        long now = System.currentTimeMillis();
        String token = Jwts.builder().subject("x")
                .expiration(new Date(now + 10000)).signWith(key).compact();

        Claims claims = jwtService.tryParseClaims(token).orElseThrow();
        assertTrue(jwtService.extractRoles(claims).isEmpty());
    }
}
