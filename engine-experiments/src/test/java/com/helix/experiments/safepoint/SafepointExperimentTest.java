package com.helix.experiments.safepoint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafepointExperimentTest {

    @Test
    void testMonitorSafepoints() {
        SafepointExperiment experiment = new SafepointExperiment();
        SafepointReport report = experiment.monitorSafepoints("G1CollectForAllocation", 5000);

        assertNotNull(report);
        assertEquals("G1CollectForAllocation", report.scenarioName());
        assertTrue(report.timeToReachSafepointMs() >= 0);
        assertTrue(report.totalPauseMs() > 0);
    }
}
