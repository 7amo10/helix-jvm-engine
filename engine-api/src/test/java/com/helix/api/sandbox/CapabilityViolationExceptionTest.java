package com.helix.api.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CapabilityViolationException Specification")
class CapabilityViolationExceptionTest {

    @Test
    @DisplayName("Two-argument constructor initializes fields and creates descriptive message")
    void testTwoArgumentConstructor() {
        CapabilityViolationException exception = new CapabilityViolationException("java.lang.System", "exit");

        assertEquals("java.lang.System", exception.getViolatingClass());
        assertEquals("exit", exception.getViolatingMethod());
        assertNotNull(exception.getMessage());
        assertTrue(exception.getMessage().contains("java.lang.System.exit"));
        assertTrue(exception.getMessage().contains("restricted"));
    }

    @Test
    @DisplayName("Custom message constructor retains class, method, and overrides detail message")
    void testCustomMessageConstructor() {
        String customMsg = "Custom policy violation: Direct OS process execution blocked.";
        CapabilityViolationException exception = new CapabilityViolationException(
                "java.lang.Runtime", "exec", customMsg);

        assertEquals("java.lang.Runtime", exception.getViolatingClass());
        assertEquals("exec", exception.getViolatingMethod());
        assertEquals(customMsg, exception.getMessage());
    }

    @Test
    @DisplayName("Cause constructor preserves root cause exception")
    void testCauseConstructor() {
        SecurityException cause = new SecurityException("Permission denied by security manager");
        CapabilityViolationException exception = new CapabilityViolationException(
                "java.lang.reflect.Method", "invoke", cause);

        assertEquals("java.lang.reflect.Method", exception.getViolatingClass());
        assertEquals("invoke", exception.getViolatingMethod());
        assertSame(cause, exception.getCause());
        assertTrue(exception.getMessage().contains("java.lang.reflect.Method.invoke"));
    }

    @Test
    @DisplayName("Constructors handle null or blank class and method gracefully")
    void testNullOrBlankParameters() {
        CapabilityViolationException exception = new CapabilityViolationException(null, null);

        assertEquals("unknown", exception.getViolatingClass());
        assertEquals("unknown", exception.getViolatingMethod());
        assertTrue(exception.getMessage().contains("unknown.unknown"));
    }

    @Test
    @DisplayName("Exception is an unchecked RuntimeException")
    void testIsRuntimeException() {
        CapabilityViolationException exception = new CapabilityViolationException("java.io.File", "delete");
        assertTrue(exception instanceof RuntimeException);
    }
}
