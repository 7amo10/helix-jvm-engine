package com.helix.agent.jol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryAnalyzerTest {

    private MemoryAnalyzer memoryAnalyzer;

    public static class SampleData {
        private int id = 100;
        private double price = 99.99;
        private String name = "Sample";
    }

    @BeforeEach
    void setUp() {
        memoryAnalyzer = new MemoryAnalyzer();
    }

    @Test
    @DisplayName("Should inspect instance shallow and deep memory layout correctly")
    void testInstanceAnalysis() {
        SampleData data = new SampleData();
        MemoryAnalysisReport report = memoryAnalyzer.analyze(data);

        assertNotNull(report);
        assertEquals(SampleData.class.getName(), report.className());
        assertTrue(report.instanceSizeShallow() > 0);
        assertTrue(report.instanceSizeDeep() >= report.instanceSizeShallow());
        assertTrue(report.headerSize() > 0);
        assertNotNull(report.printableLayout());
    }

    @Test
    @DisplayName("Should analyze deep size of object graphs with collections")
    void testGraphLayoutDeepSize() {
        List<SampleData> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            list.add(new SampleData());
        }

        MemoryAnalysisReport report = memoryAnalyzer.analyze(list);
        assertTrue(report.instanceSizeDeep() > report.instanceSizeShallow());
    }

    @Test
    @DisplayName("Should inspect class layout without instance")
    void testClassAnalysis() {
        MemoryAnalysisReport report = memoryAnalyzer.analyzeClass(SampleData.class);
        assertNotNull(report);
        assertEquals(SampleData.class.getName(), report.className());
        assertTrue(report.instanceSizeShallow() > 0);
    }
}
