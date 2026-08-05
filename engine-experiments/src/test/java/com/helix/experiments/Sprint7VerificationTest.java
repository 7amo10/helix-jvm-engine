package com.helix.experiments;

import com.helix.HelixApplication;
import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.cli.CompileCommand;
import com.helix.cli.ExecuteCommand;
import com.helix.cli.ExperimentCommand;
import com.helix.cli.ProfileCommand;
import com.helix.cli.output.OutputFormatter;
import com.helix.cli.output.JsonFormatter;
import com.helix.cli.ui.TerminalRenderer;
import com.helix.core.RuleCompiler;
import com.helix.core.cache.CacheKey;
import com.helix.core.cache.TieredRuleCache;
import com.helix.core.classloader.ClassLoaderManager;
import com.helix.core.classloader.IsolationMode;
import com.helix.core.executor.AsyncExecutor;
import com.helix.core.executor.BatchExecutor;
import com.helix.core.executor.SyncExecutor;
import com.helix.experiments.gc.GcStressExperiment;
import com.helix.experiments.jit.JitCompilationExperiment;
import com.helix.experiments.layout.ObjectLayoutExperiment;
import com.helix.experiments.metaspace.MetaspaceLeakExperiment;
import com.helix.experiments.safepoint.SafepointExperiment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Sprint 7 Final Verification & Production Readiness Test Suite")
class Sprint7VerificationTest {

    @Test
    @DisplayName("1. Verification: End-to-End Dynamic Compilation & Multi-Execution Pipeline")
    void testEndToEndPipeline() throws Exception {
        RuleCompiler compiler = new RuleCompiler();
        String json = """
                {
                    "name": "Sprint7VerificationRule",
                    "version": "1.0.0",
                    "expression": "score >= 80 && active == true",
                    "inputSchema": { "score": "int", "active": "boolean" }
                }
                """;

        CompiledRule rule = compiler.compile(json);
        assertNotNull(rule);
        assertEquals("Sprint7VerificationRule", rule.getName());

        // Cache verification
        TieredRuleCache cache = new TieredRuleCache(10, 5, java.util.concurrent.TimeUnit.MINUTES);
        CacheKey key = new CacheKey("Sprint7VerificationRule", "1.0.0", Map.of());
        cache.put(key, rule);
        assertTrue(cache.get(key).isPresent());

        // Sync Execution
        SyncExecutor syncExec = new SyncExecutor();
        ExecutionResult syncRes = syncExec.execute(rule, new ExecutionContext(Map.of("score", 90, "active", true)));
        assertTrue(syncRes.isSuccess());
        assertEquals(true, syncRes.getResult().orElse(null));

        // Async Execution
        try (AsyncExecutor asyncExec = new AsyncExecutor()) {
            CompletableFuture<ExecutionResult> future = asyncExec.executeAsync(rule, new ExecutionContext(Map.of("score", 90, "active", true)));
            ExecutionResult asyncRes = future.get();
            assertTrue(asyncRes.isSuccess());
        }

        // Batch Execution
        BatchExecutor batchExec = new BatchExecutor(2);
        List<ExecutionResult> batchRes = batchExec.executeBatch(rule, List.of(
                new ExecutionContext(Map.of("score", 95, "active", true)),
                new ExecutionContext(Map.of("score", 50, "active", true))
        ));
        assertEquals(2, batchRes.size());
        assertEquals(true, batchRes.get(0).getResult().orElse(null));
        assertEquals(false, batchRes.get(1).getResult().orElse(null));

        cache.close();
    }

    @Test
    @DisplayName("2. Verification: CLI Commands & Help Menu Execution")
    void testCliCommandsExecution() throws Exception {
        CommandLine appCmd = new CommandLine(new HelixApplication());
        assertEquals(0, appCmd.execute("--help"));

        CommandLine expCmd = new CommandLine(new ExperimentCommand());
        assertEquals(0, expCmd.execute("--name", "jit", "--output", "json"));
        assertEquals(0, expCmd.execute("--name", "metaspace", "--output", "csv"));
        assertEquals(0, expCmd.execute("--name", "all", "--output", "text", "--quiet"));

        CommandLine profileCmd = new CommandLine(new ProfileCommand());
        assertEquals(0, profileCmd.execute("--mode", "cpu", "--seconds", "1", "--output", "json"));
    }

    @Test
    @DisplayName("3. Verification: Output Formatter Formats")
    void testOutputFormatters() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "SUCCESS");
        data.put("ops", 125000);

        String json = JsonFormatter.format(data);
        assertTrue(json.contains("\"status\" : \"SUCCESS\""));

        OutputFormatter formatter = new OutputFormatter("csv", false);
        String csv = formatter.formatResult("Summary", data);
        assertTrue(csv.contains("Key,Value"));

        TerminalRenderer.renderInfo("Verified TerminalRenderer ANSI Output");
        TerminalRenderer.renderSuccess("Verified TerminalRenderer Success Output");
    }

    @Test
    @DisplayName("4. Verification: JVM Behavior Experiments Suite Execution")
    void testAllJvmExperiments() {
        MetaspaceLeakExperiment metaspace = new MetaspaceLeakExperiment();
        var metaspaceReport = metaspace.runFixedScenario(10);
        assertNotNull(metaspaceReport);

        JitCompilationExperiment jit = new JitCompilationExperiment();
        var jitReport = jit.observeCompilationTiers("com.helix.Rule::eval", 100);
        assertNotNull(jitReport);

        GcStressExperiment gc = new GcStressExperiment();
        var gcReport = gc.weakReferenceImmediate(10);
        assertNotNull(gcReport);

        ObjectLayoutExperiment layout = new ObjectLayoutExperiment();
        var layoutReport = layout.analyzeLayout(String.class);
        assertNotNull(layoutReport);

        SafepointExperiment safepoint = new SafepointExperiment();
        var safepointReport = safepoint.monitorSafepoints("G1CollectForAllocation", 100);
        assertNotNull(safepointReport);
    }

    @Test
    @DisplayName("5. Verification: ClassLoader Manager Isolation Modes")
    void testClassLoaderIsolationModes() {
        ClassLoaderManager mgr = new ClassLoaderManager(IsolationMode.HIERARCHICAL);
        var loader = mgr.getOrCreateClassLoader("FINANCE", "RuleA");
        assertNotNull(loader);
        assertEquals(IsolationMode.HIERARCHICAL, mgr.getIsolationMode());
        mgr.close();
    }
}
