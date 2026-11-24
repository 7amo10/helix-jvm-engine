package com.helix.experiments.benchmarks;

import com.helix.api.ExecutionContext;
import com.helix.core.batch.ColumnExtractor;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark suite measuring row-to-columnar off-heap transposition latency
 * via {@link ColumnExtractor} for 200 contexts and 5 double fields.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Thread)
@Fork(0)
public class ColumnExtractorBenchmark {

    private static final int CONTEXT_COUNT = 200;

    private ExecutionContext[] contexts;
    private List<String> fields;

    @Setup
    public void setup() {
        this.fields = List.of("amount", "riskScore", "velocity", "weight", "balance");
        this.contexts = new ExecutionContext[CONTEXT_COUNT];

        Random rng = new Random(42);
        for (int i = 0; i < CONTEXT_COUNT; i++) {
            contexts[i] = new ExecutionContext(Map.of(
                    "amount", rng.nextDouble() * 5000.0,
                    "riskScore", rng.nextDouble(),
                    "velocity", rng.nextDouble() * 100.0,
                    "weight", rng.nextDouble() * 50.0,
                    "balance", rng.nextDouble() * 10000.0
            ));
        }
    }

    /**
     * Measures off-heap {@link Arena} allocation and row-to-columnar transposition
     * for 200 {@link ExecutionContext} instances across 5 double fields.
     */
    @Benchmark
    public int benchmarkExtract200Contexts5Fields() {
        try (Arena arena = Arena.ofConfined()) {
            Map<String, MemorySegment> columns = ColumnExtractor.extract(contexts, fields, arena);
            return columns.size();
        }
    }
}
