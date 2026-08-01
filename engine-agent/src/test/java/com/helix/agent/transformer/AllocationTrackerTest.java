package com.helix.agent.transformer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AllocationTrackerTest {

    private AllocationTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = AllocationTracker.getInstance();
        tracker.reset();
    }

    @Test
    @DisplayName("Should track total and per-class allocations correctly")
    void testRecordAllocation() {
        tracker.recordAllocation("java/lang/String");
        tracker.recordAllocation("java/lang/String");
        tracker.recordAllocation("java/util/ArrayList");

        assertEquals(3, tracker.getTotalAllocations());
        assertEquals(2, tracker.getAllocationsForClass("java/lang/String"));
        assertEquals(1, tracker.getAllocationsForClass("java/util/ArrayList"));
        assertEquals(0, tracker.getAllocationsForClass("java/util/HashMap"));
    }

    @Test
    @DisplayName("Should invoke AllocationInterceptor callback properly")
    void testAllocationInterceptor() {
        AllocationInterceptor.onAllocation("com/helix/api/ExecutionContext");

        assertEquals(1, tracker.getTotalAllocations());
        assertEquals(1, tracker.getAllocationsForClass("com/helix/api/ExecutionContext"));
    }
}
