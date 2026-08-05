package com.helix.experiments.benchmarks;

import com.helix.api.CompiledRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompilationBenchmarkTest {

    @Test
    void testCompilationBenchmarkExecution() throws Exception {
        CompilationBenchmark benchmark = new CompilationBenchmark();
        benchmark.setup();

        CompiledRule simpleBuddy = benchmark.benchmarkSimpleRuleByteBuddy();
        assertNotNull(simpleBuddy);

        CompiledRule complexBuddy = benchmark.benchmarkComplexRuleByteBuddy();
        assertNotNull(complexBuddy);

        CompiledRule simpleAsm = benchmark.benchmarkSimpleRuleAsm();
        assertNotNull(simpleAsm);

        CompiledRule complexAsm = benchmark.benchmarkComplexRuleAsm();
        assertNotNull(complexAsm);
    }
}
