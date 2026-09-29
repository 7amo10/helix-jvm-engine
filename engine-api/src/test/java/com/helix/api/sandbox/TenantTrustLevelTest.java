package com.helix.api.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TenantTrustLevel Enum Specification")
class TenantTrustLevelTest {

    @Test
    @DisplayName("Enum contains expected TRUSTED and UNTRUSTED constants")
    void testEnumConstants() {
        TenantTrustLevel[] levels = TenantTrustLevel.values();
        assertEquals(2, levels.length);
        assertNotNull(TenantTrustLevel.valueOf("TRUSTED"));
        assertNotNull(TenantTrustLevel.valueOf("UNTRUSTED"));
    }

    @Test
    @DisplayName("isSandboxed returns false for TRUSTED and true for UNTRUSTED")
    void testIsSandboxed() {
        assertFalse(TenantTrustLevel.TRUSTED.isSandboxed());
        assertTrue(TenantTrustLevel.UNTRUSTED.isSandboxed());
    }

    @Test
    @DisplayName("fromBoolean correctly maps sandboxed boolean flag")
    void testFromBoolean() {
        assertEquals(TenantTrustLevel.UNTRUSTED, TenantTrustLevel.fromBoolean(true));
        assertEquals(TenantTrustLevel.TRUSTED, TenantTrustLevel.fromBoolean(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRUSTED", "trusted", "Trusted", "  trusted  "})
    @DisplayName("fromString correctly parses valid TRUSTED variations")
    void testFromStringTrusted(String input) {
        assertEquals(TenantTrustLevel.TRUSTED, TenantTrustLevel.fromString(input));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNTRUSTED", "untrusted", "Untrusted", "  untrusted  "})
    @DisplayName("fromString correctly parses valid UNTRUSTED variations")
    void testFromStringUntrusted(String input) {
        assertEquals(TenantTrustLevel.UNTRUSTED, TenantTrustLevel.fromString(input));
    }

    @Test
    @DisplayName("fromString defaults to TRUSTED for null or empty input")
    void testFromStringDefault() {
        assertEquals(TenantTrustLevel.TRUSTED, TenantTrustLevel.fromString(null));
        assertEquals(TenantTrustLevel.TRUSTED, TenantTrustLevel.fromString(""));
        assertEquals(TenantTrustLevel.TRUSTED, TenantTrustLevel.fromString("   "));
    }

    @Test
    @DisplayName("fromString throws IllegalArgumentException for unrecognized values")
    void testFromStringInvalid() {
        assertThrows(IllegalArgumentException.class, () -> TenantTrustLevel.fromString("SUPER_ADMIN"));
    }
}
