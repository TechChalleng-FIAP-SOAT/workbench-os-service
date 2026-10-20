package com.workbench.workshop.infrastructure.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtFilterTest {

    private static final String SECRET = "akmsdkmasfnjsgknakmsdkmasdkmsadfkmdsakmdkmsadsa";
    private SecretKey key;
    private JwtFilter filter;

    @BeforeEach
    void setUp() {
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        filter = new JwtFilter(new JwtService(new JwtProperties(SECRET, 86400000L)));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String staffToken() {
        long now = System.currentTimeMillis();
        return Jwts.builder().subject("admin@system.com")
                .claim("roles", List.of("ADMIN"))
                .expiration(new Date(now + 3600000)).signWith(key).compact();
    }

    private String clientToken() {
        long now = System.currentTimeMillis();
        return Jwts.builder().subject("52998224725")
                .claim("clientId", 1).claim("roles", List.of("CLIENT"))
                .expiration(new Date(now + 3600000)).signWith(key).compact();
    }

    @Test
    void authenticatesStaffWithRoleAuthorities() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + staffToken());
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("admin@system.com", auth.getName());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        verify(chain).doFilter(request, response);
    }

    @Test
    void authenticatesClientWithRoleClient() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + clientToken());
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth);
        assertEquals("52998224725", auth.getName());
        assertTrue(auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLIENT")));
    }

    @Test
    void ignoresRequestWithoutBearer() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void ignoresInvalidToken() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not.a.jwt");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }
}
