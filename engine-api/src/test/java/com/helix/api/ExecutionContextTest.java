package com.helix.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionContextTest {

    @Test
    @DisplayName("Should create empty context and set/get variables")
    void testSetAndGetVariable() {
        ExecutionContext context = new ExecutionContext();
        assertFalse(context.hasVariable("x"));

        context.setVariable("x", 42);
        assertTrue(context.hasVariable("x"));
        assertEquals(42, context.getVariable("x").orElse(null));
        assertEquals(42, context.getVariable("x", Integer.class).orElse(null));
    }

    @Test
    @DisplayName("Should initialize context from map")
    void testMapConstructor() {
        ExecutionContext context = new ExecutionContext(Map.of("a", "hello", "b", 100L));
        assertEquals("hello", context.getVariable("a").orElse(null));
        assertEquals(100L, context.getVariable("b", Long.class).orElse(null));
        assertEquals(2, context.getVariables().size());
    }

    @Test
    @DisplayName("Should throw exception when retrieving variable with wrong type")
    void testInvalidTypeCast() {
        ExecutionContext context = new ExecutionContext();
        context.setVariable("num", 100);

        assertThrows(ClassCastException.class, () -> context.getVariable("num", String.class));
    }
}
