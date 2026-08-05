package com.helix.agent;

import com.helix.agent.jol.CompressedOopsDetector;
import com.helix.agent.transformer.AllocationTracker;
import com.helix.agent.transformer.TransformationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EngineAgentCoverageTest {

    @Test
    void testExceptions() {
        AgentInitializationException initEx = new AgentInitializationException("Init failed", new RuntimeException("cause"));
        assertEquals("Init failed", initEx.getMessage());
        assertNotNull(initEx.getCause());

        TransformationException transEx = new TransformationException("Transform failed", new RuntimeException("cause"));
        assertEquals("Transform failed", transEx.getMessage());
        assertNotNull(transEx.getCause());
    }

    @Test
    void testCompressedOopsDetectorDetails() {
        String details = CompressedOopsDetector.getVMDetails();
        assertNotNull(details);
        assertFalse(details.isEmpty());
    }

    @Test
    void testAllocationTrackerReset() {
        AllocationTracker tracker = AllocationTracker.getInstance();
        tracker.recordAllocation("com.helix.TestRule");
        assertTrue(tracker.getTotalAllocations() > 0);
        assertEquals(1L, tracker.getAllocationsForClass("com.helix.TestRule"));
        assertNotNull(tracker.getAllocationsByClass());

        tracker.reset();
        assertEquals(0, tracker.getTotalAllocations());
    }
}
