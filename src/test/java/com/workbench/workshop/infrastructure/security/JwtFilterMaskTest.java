package com.workbench.workshop.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtFilterMaskTest {

    @Test
    void masksCpfKeepingExtremes() {
        // 52998224725 -> 529******25 (OWASP A09: não logar o documento inteiro)
        assertEquals("529******25", JwtFilter.mask("52998224725"));
    }

    @Test
    void keepsEmailUnmasked() {
        assertEquals("admin@workbench.dev", JwtFilter.mask("admin@workbench.dev"));
    }

    @Test
    void shortValueAndNullPassThrough() {
        assertEquals("abc", JwtFilter.mask("abc"));
        assertNull(JwtFilter.mask(null));
    }
}
