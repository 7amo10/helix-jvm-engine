package com.helix.experiments.metaspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetaspaceLeakExperimentTest {

    @Test
    void testMetaspaceLeakAndFixedScenarios() {
        MetaspaceLeakExperiment experiment = new MetaspaceLeakExperiment();

        MetaspaceReport leakReport = experiment.runLeakScenario(10);
        assertNotNull(leakReport);
        assertEquals("LEAK", leakReport.scenarioName());
        assertEquals(10, leakReport.classesGenerated());
        assertEquals(10, leakReport.activeClassLoaders());

        MetaspaceReport fixedReport = experiment.runFixedScenario(10);
        assertNotNull(fixedReport);
        assertEquals("FIXED", fixedReport.scenarioName());
        assertEquals(10, fixedReport.classesGenerated());
        assertTrue(fixedReport.gcTriggered());
    }
}
