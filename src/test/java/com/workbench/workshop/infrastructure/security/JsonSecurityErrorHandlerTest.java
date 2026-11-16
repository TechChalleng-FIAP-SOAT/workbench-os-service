package com.workbench.workshop.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.junit.jupiter.api.Assertions.*;

class JsonSecurityErrorHandlerTest {

    private final JsonSecurityErrorHandler handler = new JsonSecurityErrorHandler();

    @Test
    void unauthorizedIsJson() throws Exception {
        var response = new MockHttpServletResponse();
        handler.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("x"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(response.getContentAsString().contains(JsonSecurityErrorHandler.UNAUTHORIZED));
    }

    @Test
    void forbiddenIsJson() throws Exception {
        var response = new MockHttpServletResponse();
        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"status\":403"));
    }
}
