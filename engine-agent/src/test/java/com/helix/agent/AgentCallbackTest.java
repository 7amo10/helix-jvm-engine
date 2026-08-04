package com.helix.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentCallbackTest {

    @BeforeEach
    void setUp() {
        AgentCallback.clearThreadStats();
    }

    @Test
    @DisplayName("Should track method entry and exit in thread-local ExecutionStats")
    void testMethodEntryAndExitTracking() {
        AgentCallback.onMethodEntry("TestClass", "execute");
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }
        AgentCallback.onMethodExit("TestClass", "execute");

        ExecutionStats stats = AgentCallback.getThreadStats();
        assertEquals(1, stats.getTotalExecutions());
        assertTrue(stats.getTotalDurationNanos() > 0);
    }

    @Test
    @DisplayName("Should track allocations in thread-local ExecutionStats")
    void testAllocationTracking() {
        AgentCallback.recordAllocation("java/lang/String", 24);
        AgentCallback.recordAllocation("java/util/HashMap", 48);

        ExecutionStats stats = AgentCallback.getThreadStats();
        assertEquals(2, stats.getTotalAllocations());
    }

    @Test
    @DisplayName("Should reset thread-local ExecutionStats on clear")
    void testClearThreadStats() {
        AgentCallback.onMethodEntry("Sample", "run");
        AgentCallback.recordAllocation("Sample", 16);
        AgentCallback.onMethodExit("Sample", "run");

        AgentCallback.clearThreadStats();
        ExecutionStats stats = AgentCallback.getThreadStats();

        assertEquals(0, stats.getTotalExecutions());
        assertEquals(0, stats.getTotalAllocations());
        assertEquals(0, stats.getTotalDurationNanos());
    }
}
