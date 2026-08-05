package com.helix.experiments;

import com.helix.experiments.benchmarks.CacheBenchmark;
import com.helix.experiments.benchmarks.CompilationBenchmark;
import com.helix.experiments.benchmarks.ExecutionBenchmark;
import com.helix.experiments.gc.GcReport;
import com.helix.experiments.gc.GcStressExperiment;
import com.helix.experiments.jit.JitCompilationExperiment;
import com.helix.experiments.jit.JitReport;
import com.helix.experiments.layout.LayoutReport;
import com.helix.experiments.layout.ObjectLayoutExperiment;
import com.helix.experiments.metaspace.MetaspaceLeakExperiment;
import com.helix.experiments.metaspace.MetaspaceReport;
import com.helix.experiments.safepoint.SafepointExperiment;
import com.helix.experiments.safepoint.SafepointReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Sprint 6 Verification - Experiments & JMH Benchmarks Suite")
class Sprint6VerificationTest {

    @Test
    @DisplayName("1. Verify Metaspace Leak & Fix Experiments")
    void testMetaspaceExperiment() {
        MetaspaceLeakExperiment experiment = new MetaspaceLeakExperiment();

        MetaspaceReport leakReport = experiment.runLeakScenario(5);
        assertNotNull(leakReport);
        assertEquals("LEAK", leakReport.scenarioName());

        MetaspaceReport fixedReport = experiment.runFixedScenario(5);
        assertNotNull(fixedReport);
        assertEquals("FIXED", fixedReport.scenarioName());
        assertTrue(fixedReport.gcTriggered());
    }

    @Test
    @DisplayName("2. Verify JIT Tiered Compilation Experiment")
    void testJitExperiment() {
        JitCompilationExperiment experiment = new JitCompilationExperiment();

        JitReport report = experiment.observeCompilationTiers("com.helix.RuleEngine::eval", 15000);
        assertNotNull(report);
        assertEquals(4, report.highestTierAchieved());
        assertTrue(report.isInlined());
    }

    @Test
    @DisplayName("3. Verify GC Reference Stress Experiment")
    void testGcExperiment() {
        GcStressExperiment experiment = new GcStressExperiment();

        GcReport weakReport = experiment.weakReferenceImmediate(10);
        assertNotNull(weakReport);
        assertEquals("WEAK_REFERENCE", weakReport.scenarioName());

        GcReport softReport = experiment.softReferenceUnderPressure(5, false);
        assertNotNull(softReport);
        assertEquals("SOFT_REFERENCE", softReport.scenarioName());
    }

    @Test
    @DisplayName("4. Verify JOL Object Layout Analysis")
    void testLayoutExperiment() {
        ObjectLayoutExperiment experiment = new ObjectLayoutExperiment();

        LayoutReport report = experiment.analyzeLayout("Helix Verification String");
        assertNotNull(report);
        assertTrue(report.instanceSizeBytes() > 0);
    }

    @Test
    @DisplayName("5. Verify Safepoint Latency Experiment")
    void testSafepointExperiment() {
        SafepointExperiment experiment = new SafepointExperiment();

        SafepointReport report = experiment.monitorSafepoints("VerificationPause", 1000);
        assertNotNull(report);
        assertTrue(report.totalPauseMs() > 0);
    }

    @Test
    @DisplayName("6. Verify JMH Benchmarks Setup")
    void testJmhBenchmarks() throws Exception {
        CompilationBenchmark compilationBenchmark = new CompilationBenchmark();
        compilationBenchmark.setup();
        assertNotNull(compilationBenchmark.benchmarkSimpleRuleByteBuddy());

        ExecutionBenchmark executionBenchmark = new ExecutionBenchmark();
        executionBenchmark.setup();
        assertNotNull(executionBenchmark.benchmarkHotRuleExecution());

        CacheBenchmark cacheBenchmark = new CacheBenchmark();
        cacheBenchmark.setup();
        assertTrue(cacheBenchmark.benchmarkCacheHit().isPresent());
        cacheBenchmark.tearDown();
    }
}
