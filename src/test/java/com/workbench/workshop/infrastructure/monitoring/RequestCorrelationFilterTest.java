package com.workbench.workshop.infrastructure.monitoring;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void generatesRequestIdWhenAbsent() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNotNull(response.getHeader("X-Request-Id"));
        verify(chain).doFilter(request, response);
    }

    @Test
    void reusesProvidedRequestId() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "fixed-id-123");
        var response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals("fixed-id-123", response.getHeader("X-Request-Id"));
    }
}
