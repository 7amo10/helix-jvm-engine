package com.helix.experiments.benchmarks;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.core.RuleCompiler;
import com.helix.core.executor.AsyncExecutor;
import com.helix.core.executor.SyncExecutor;
import com.helix.core.executor.VirtualThreadRuleExecutor;
import org.openjdk.jmh.annotations.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark suite comparing SyncExecutor, ThreadPoolExecutor (AsyncExecutor),
 * and VirtualThreadRuleExecutor under concurrent evaluation load and structured fan-out.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
@Fork(0)
public class VirtualThreadExecutionBenchmark {

    private CompiledRule compiledRule;
    private ExecutionContext sampleContext;
    private List<ExecutionContext> batchContexts;

    private SyncExecutor syncExecutor;
    private AsyncExecutor threadPoolExecutor;
    private VirtualThreadRuleExecutor virtualThreadExecutor;

    @Setup
    public void setup() throws Exception {
        RuleCompiler compiler = new RuleCompiler();
        String ruleJson = """
                {
                    "name": "VTBenchmarkRule",
                    "expression": "(score >= 70 && active == true) || priority == 10",
                    "inputSchema": {
                        "score": "integer",
                        "active": "boolean",
                        "priority": "integer"
                    }
                }
                """;

        this.compiledRule = compiler.compile(ruleJson);
        this.sampleContext = new ExecutionContext(Map.of("score", 85, "active", true, "priority", 5));

        this.batchContexts = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            batchContexts.add(new ExecutionContext(Map.of("score", 70 + (i % 30), "active", (i % 2 == 0), "priority", i % 11)));
        }

        this.syncExecutor = new SyncExecutor();
        this.threadPoolExecutor = new AsyncExecutor();
        this.virtualThreadExecutor = new VirtualThreadRuleExecutor(Duration.ofSeconds(10));

        // Warm up
        for (int i = 0; i < 1000; i++) {
            compiledRule.execute(sampleContext);
        }
    }

    @TearDown
    public void tearDown() {
        if (threadPoolExecutor != null) {
            threadPoolExecutor.close();
        }
        if (virtualThreadExecutor != null) {
            virtualThreadExecutor.close();
        }
    }

    @Benchmark
    public ExecutionResult benchmarkSyncExecutor() throws Exception {
        return syncExecutor.execute(compiledRule, sampleContext);
    }

    @Benchmark
    public ExecutionResult benchmarkThreadPoolExecutor() throws Exception {
        CompletableFuture<ExecutionResult> future = threadPoolExecutor.executeAsync(compiledRule, sampleContext);
        return future.get(5, TimeUnit.SECONDS);
    }

    @Benchmark
    public ExecutionResult benchmarkVirtualThreadExecutor() throws Exception {
        CompletableFuture<ExecutionResult> future = virtualThreadExecutor.executeAsync(compiledRule, sampleContext);
        return future.get(5, TimeUnit.SECONDS);
    }

    @Benchmark
    public List<ExecutionResult> benchmarkVirtualThreadStructuredFanOut() throws Exception {
        return virtualThreadExecutor.executeAll(compiledRule, batchContexts);
    }
}
