package com.helix.experiments.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObjectLayoutExperimentTest {

    static class SampleRuleData {
        private final long id = 101L;
        private final String name = "TestRule";
        private final boolean active = true;
    }

    @Test
    void testAnalyzeLayout() {
        ObjectLayoutExperiment experiment = new ObjectLayoutExperiment();
        SampleRuleData sample = new SampleRuleData();

        LayoutReport report = experiment.analyzeLayout(sample);

        assertNotNull(report);
        assertEquals(SampleRuleData.class.getName(), report.className());
        assertTrue(report.instanceSizeBytes() > 0);
        assertTrue(report.headerSizeBytes() > 0);
        assertNotNull(report.printableLayout());
        assertTrue(report.printableLayout().contains("SampleRuleData"));
    }
}
