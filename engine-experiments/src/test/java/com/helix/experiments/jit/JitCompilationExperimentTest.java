package com.helix.experiments.jit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JitCompilationExperimentTest {

    @Test
    void testObserveCompilationTiers() {
        JitCompilationExperiment experiment = new JitCompilationExperiment();

        JitReport report100 = experiment.observeCompilationTiers("com.helix.Rule::eval", 100);
        assertEquals(3, report100.highestTierAchieved());

        JitReport report15000 = experiment.observeCompilationTiers("com.helix.Rule::eval", 15000);
        assertEquals(4, report15000.highestTierAchieved());
        assertTrue(report15000.isInlined());
    }

    @Test
    void testInliningLimits() {
        JitCompilationExperiment experiment = new JitCompilationExperiment();

        JitReport smallMethodReport = experiment.testInliningLimits("com.helix.Rule::small", 20, false);
        assertTrue(smallMethodReport.isInlined());

        JitReport hugeMethodReport = experiment.testInliningLimits("com.helix.Rule::huge", 500, true);
        assertFalse(hugeMethodReport.isInlined());
        assertTrue(hugeMethodReport.inliningReason().contains("rejected"));
    }
}
