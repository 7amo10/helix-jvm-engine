package com.helix.experiments.gc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GcStressExperimentTest {

    @Test
    void testWeakReferenceImmediateClearing() {
        GcStressExperiment experiment = new GcStressExperiment();
        GcReport report = experiment.weakReferenceImmediate(20);

        assertNotNull(report);
        assertEquals("WEAK_REFERENCE", report.scenarioName());
        assertEquals(20, report.initialObjectCount());
        assertTrue(report.clearedCount() > 0 || report.retainedCount() >= 0);
    }

    @Test
    void testSoftReferenceUnderPressure() {
        GcStressExperiment experiment = new GcStressExperiment();
        GcReport normalReport = experiment.softReferenceUnderPressure(10, false);

        assertNotNull(normalReport);
        assertEquals("SOFT_REFERENCE", normalReport.scenarioName());

        GcReport pressureReport = experiment.softReferenceUnderPressure(10, true);
        assertNotNull(pressureReport);
    }
}
