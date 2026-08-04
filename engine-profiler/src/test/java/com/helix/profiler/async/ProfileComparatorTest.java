package com.helix.profiler.async;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProfileComparatorTest {

    @Test
    void testCompareProfiles() {
        ProfileComparator comparator = new ProfileComparator();

        String profileA = """
               com.helix.core.RuleCompiler;parse 100
               com.helix.core.executor.SyncExecutor;execute 200
            """;

        String profileB = """
               com.helix.core.RuleCompiler;parse 150
               com.helix.core.executor.SyncExecutor;execute 100
            """;

        ProfileComparator.ComparisonReport report = comparator.compareProfiles(profileA, profileB);

        assertEquals(300L, report.totalSamplesA());
        assertEquals(250L, report.totalSamplesB());
        assertEquals(-50L, report.netSampleDelta());

        assertEquals(1, report.regressions().size());
        assertEquals("com.helix.core.RuleCompiler;parse", report.regressions().get(0).stackTrace());
        assertEquals(50L, report.regressions().get(0).delta());

        assertEquals(1, report.optimizations().size());
        assertEquals("com.helix.core.executor.SyncExecutor;execute", report.optimizations().get(0).stackTrace());
        assertEquals(-100L, report.optimizations().get(0).delta());
    }
}
