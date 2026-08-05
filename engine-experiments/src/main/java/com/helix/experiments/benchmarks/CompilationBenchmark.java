package com.helix.experiments.benchmarks;

import com.helix.api.CompiledRule;
import com.helix.core.RuleCompiler;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark suite measuring rule compilation latency and throughput across simple vs complex rules.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Thread)
@Fork(0)
public class CompilationBenchmark {

    private RuleCompiler byteBuddyCompiler;
    private RuleCompiler asmCompiler;
    private String simpleRuleJson;
    private String complexRuleJson;

    @Setup
    public void setup() {
        this.byteBuddyCompiler = new RuleCompiler(RuleCompiler.GeneratorType.BYTE_BUDDY);
        this.asmCompiler = new RuleCompiler(RuleCompiler.GeneratorType.ASM);

        this.simpleRuleJson = """
                {
                    "name": "SimpleBenchmarkRule",
                    "expression": "score > 50",
                    "inputSchema": {
                        "score": "integer"
                    }
                }
                """;

        this.complexRuleJson = """
                {
                    "name": "ComplexBenchmarkRule",
                    "expression": "(age >= 18 && income > 50000) || status == 'VIP'",
                    "inputSchema": {
                        "age": "integer",
                        "income": "double",
                        "status": "string"
                    }
                }
                """;
    }

    @Benchmark
    public CompiledRule benchmarkSimpleRuleByteBuddy() throws Exception {
        return byteBuddyCompiler.compile(simpleRuleJson);
    }

    @Benchmark
    public CompiledRule benchmarkComplexRuleByteBuddy() throws Exception {
        return byteBuddyCompiler.compile(complexRuleJson);
    }

    @Benchmark
    public CompiledRule benchmarkSimpleRuleAsm() throws Exception {
        return asmCompiler.compile(simpleRuleJson);
    }

    @Benchmark
    public CompiledRule benchmarkComplexRuleAsm() throws Exception {
        return asmCompiler.compile(complexRuleJson);
    }
}
