package com.workbench.workshop.infrastructure.security;

import com.workbench.workshop.support.TestKeys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.PrivateKey;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JwtFilterTest {
    private PrivateKey key;
    private JwtFilter filter;

    @BeforeEach
    void setUp() {
        key = TestKeys.authPrivateKey();
        filter = new JwtFilter(new JwtService(TestKeys.jwtProperties()));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String staffToken() {
        long now = System.currentTimeMillis();
        return TestKeys.authToken().subject("admin@system.com")
                .claim("roles", List.of("ADMIN"))
                .expiration(new Date(now + 3600000)).signWith(key).compact();
    }

    private String clientToken() {
        long now = System.currentTimeMillis();
        return TestKeys.authToken().subject("52998224725")
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
