package com.helix.profiler.jit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TieredCompilationTrackerTest {

    private TieredCompilationTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new TieredCompilationTracker();
    }

    @Test
    void testTierTransitionsAndCurrentTier() {
        String method = "com.helix.core.RuleCompiler::compile";

        tracker.recordEvent(new CompilationEvent(100L, 1, 3, method, 50));
        assertEquals(3, tracker.getCurrentTier(method));
        assertFalse(tracker.isC2Compiled(method));

        tracker.recordEvent(new CompilationEvent(200L, 2, 4, method, 50));
        assertEquals(4, tracker.getCurrentTier(method));
        assertTrue(tracker.isC2Compiled(method));
        assertEquals(2, tracker.getTotalTransitions());
    }

    @Test
    void testDeoptimizationResetsTier() {
        String method = "com.helix.core.RuleCompiler::execute";

        tracker.recordEvent(new CompilationEvent(100L, 1, 4, method, 80));
        assertTrue(tracker.isC2Compiled(method));

        tracker.recordEvent(new CompilationEvent(150L, 1, 4, method, 80, false, false, false, "made not entrant", java.time.Instant.now()));
        assertEquals(0, tracker.getCurrentTier(method));
        assertFalse(tracker.isC2Compiled(method));
    }
}
