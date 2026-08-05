package com.helix.api;

import com.helix.api.agent.AgentConfiguration;
import com.helix.api.agent.ClassLoaderInfo;
import com.helix.api.agent.MemoryAnalysisReport;
import com.helix.api.profiler.CacheEvent;
import com.helix.api.profiler.CompilationEvent;
import com.helix.api.profiler.ExecutionEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EngineApiCoverageTest {

    @Test
    void testAgentConfigurationAndInfo() {
        AgentConfiguration config = AgentConfiguration.builder()
                .withMemoryProfiling(true)
                .withClassTransform(true)
                .addTargetPackage("com.helix")
                .build();

        assertTrue(config.isMemoryProfilingEnabled());
        assertTrue(config.isClassTransformEnabled());
        assertTrue(config.getTargetPackages().contains("com.helix"));

        ClassLoaderInfo info = new ClassLoaderInfo("TestLoader", 10, 1024L, "System", Instant.now());
        assertEquals("TestLoader", info.name());
        assertEquals(10, info.loadedClassCount());
        assertEquals(1024L, info.totalMemoryBytes());
        assertEquals("System", info.parentName());

        MemoryAnalysisReport memReport = new MemoryAnalysisReport("SampleClass", 32L, 12L, 4, "details", Instant.now());
        assertEquals("SampleClass", memReport.className());
        assertEquals(32L, memReport.instanceSizeBytes());
        assertEquals(12L, memReport.headerSizeBytes());
        assertEquals(4, memReport.fieldCount());
        assertEquals("details", memReport.layoutDetails());
    }

    @Test
    void testProfilerEvents() {
        Instant now = Instant.now();
        CacheEvent cacheEvent = new CacheEvent("key1", CacheEvent.CacheOperation.HIT, "L1_STRONG", now);
        assertEquals("key1", cacheEvent.ruleKey());
        assertEquals(CacheEvent.CacheOperation.HIT, cacheEvent.operation());
        assertEquals("L1_STRONG", cacheEvent.cacheTier());
        assertEquals(now, cacheEvent.timestamp());

        CompilationEvent compEvent = new CompilationEvent("rule1", "1.0.0", 15000000L, true, "BYTE_BUDDY", now);
        assertEquals("rule1", compEvent.ruleName());
        assertEquals("1.0.0", compEvent.ruleVersion());
        assertEquals(15000000L, compEvent.compilationTimeNanos());
        assertTrue(compEvent.success());
        assertEquals("BYTE_BUDDY", compEvent.generatorType());

        ExecutionEvent execEvent = new ExecutionEvent("rule1", "1.0.0", 2500000L, true, now);
        assertEquals("rule1", execEvent.ruleName());
        assertEquals("1.0.0", execEvent.ruleVersion());
        assertEquals(2500000L, execEvent.executionTimeNanos());
        assertTrue(execEvent.success());
    }

    @Test
    void testExceptionsAndResultHelpers() {
        RuleCompilationException compEx = new RuleCompilationException("compilation failed", new RuntimeException("cause"));
        assertEquals("compilation failed", compEx.getMessage());
        assertNotNull(compEx.getCause());

        RuleExecutionException execEx = new RuleExecutionException("execution failed", new RuntimeException("cause"));
        assertEquals("execution failed", execEx.getMessage());
        assertNotNull(execEx.getCause());

        ExecutionResult failure = ExecutionResult.failure(new RuntimeException("error message"), 500L);
        assertFalse(failure.isSuccess());
        assertEquals("error message", failure.getError().map(Throwable::getMessage).orElse(null));

        ExecutionContext ctx = new ExecutionContext(Map.of("key", "val"));
        assertTrue(ctx.hasVariable("key"));
        assertEquals("val", ctx.getVariable("key").orElse(null));
        assertFalse(ctx.getVariable("nonexistent").isPresent());
    }
}
