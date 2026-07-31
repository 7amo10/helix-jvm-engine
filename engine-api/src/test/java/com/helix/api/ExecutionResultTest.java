package com.helix.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionResultTest {

    @Test
    @DisplayName("Should create success result with value and execution time")
    void testSuccessResult() {
        ExecutionResult result = ExecutionResult.success("Passed", 500_000L);

        assertTrue(result.isSuccess());
        assertEquals("Passed", result.getResult().orElse(null));
        assertEquals("Passed", result.getResult(String.class).orElse(null));
        assertFalse(result.getError().isPresent());
        assertEquals(500_000L, result.getExecutionTimeNanos());
    }

    @Test
    @DisplayName("Should create failure result with error and execution time")
    void testFailureResult() {
        RuntimeException ex = new RuntimeException("Compilation error");
        ExecutionResult result = ExecutionResult.failure(ex, 100_000L);

        assertFalse(result.isSuccess());
        assertFalse(result.getResult().isPresent());
        assertTrue(result.getError().isPresent());
        assertEquals("Compilation error", result.getError().get().getMessage());
        assertEquals(100_000L, result.getExecutionTimeNanos());
    }
}
