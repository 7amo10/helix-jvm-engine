package com.helix.experiments.benchmarks;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.core.RuleCompiler;
import org.openjdk.jmh.annotations.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark suite measuring rule execution throughput (cold vs hot rules, concurrent execution).
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
@Fork(0)
public class ExecutionBenchmark {

    private CompiledRule compiledRule;
    private ExecutionContext sampleContext;
    private String ruleJson;
    private RuleCompiler compiler;

    @Setup
    public void setup() throws Exception {
        this.compiler = new RuleCompiler();
        this.ruleJson = """
                {
                    "name": "ExecutionBenchmarkRule",
                    "expression": "(score >= 70 && active == true) || priority == 10",
                    "inputSchema": {
                        "score": "integer",
                        "active": "boolean",
                        "priority": "integer"
                    }
                }
                """;

        this.compiledRule = compiler.compile(ruleJson);

        // Pre-warm JIT with 20,000 iterations for hot rule benchmark
        this.sampleContext = new ExecutionContext(Map.of("score", 85, "active", true, "priority", 5));
        for (int i = 0; i < 20000; i++) {
            compiledRule.execute(sampleContext);
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    public ExecutionResult benchmarkColdRuleExecution() throws Exception {
        RuleCompiler freshCompiler = new RuleCompiler();
        CompiledRule freshRule = freshCompiler.compile(ruleJson);
        return freshRule.execute(sampleContext);
    }

    @Benchmark
    public ExecutionResult benchmarkHotRuleExecution() throws Exception {
        return compiledRule.execute(sampleContext);
    }

    @Benchmark
    @Threads(4)
    public ExecutionResult benchmarkConcurrentRuleExecution() throws Exception {
        return compiledRule.execute(sampleContext);
    }
}
