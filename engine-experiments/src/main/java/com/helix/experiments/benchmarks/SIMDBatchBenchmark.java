package com.helix.experiments.benchmarks;

import com.helix.api.ExecutionContext;
import com.helix.core.batch.BatchDispatcher;
import com.helix.core.batch.ColumnExtractor;
import com.helix.core.batch.VectorizedBatchEvaluator;
import com.helix.core.parser.ast.AstBuilder;
import com.helix.core.parser.ast.ExpressionNode;
import org.openjdk.jmh.annotations.*;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark suite comparing SIMD vectorized batch evaluation against
 * scalar fallback execution across batch sizes [8, 64, 200, 1000].
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Thread)
@Fork(0)
public class SIMDBatchBenchmark {

    @Param({"8", "64", "200", "1000"})
    public int batchSize = 200;

    private BatchDispatcher simdDispatcher;
    private BatchDispatcher scalarDispatcher;
    private VectorizedBatchEvaluator vectorizedEvaluator;
    private ExpressionNode ast;
    private List<String> fields;
    private ExecutionContext[] contexts;
    private Arena preAllocatedArena;
    private Map<String, MemorySegment> preExtractedColumns;

    @Setup
    public void setup() throws Exception {
        this.simdDispatcher = new BatchDispatcher(false);
        this.scalarDispatcher = new BatchDispatcher(true);
        this.vectorizedEvaluator = new VectorizedBatchEvaluator();

        String expression = "(amount > 1000.0 && riskScore < 0.75) || (velocity * 2.0 + weight) >= 500.0";
        AstBuilder astBuilder = new AstBuilder();
        this.ast = astBuilder.buildAst(expression);
        this.fields = List.of("amount", "riskScore", "velocity", "weight");

        Random rng = new Random(42);
        this.contexts = new ExecutionContext[batchSize];
        for (int i = 0; i < batchSize; i++) {
            contexts[i] = new ExecutionContext(Map.of(
                    "amount", rng.nextDouble() * 2000.0,
                    "riskScore", rng.nextDouble(),
                    "velocity", rng.nextDouble() * 300.0,
                    "weight", rng.nextDouble() * 200.0
            ));
        }

        this.preAllocatedArena = Arena.ofConfined();
        this.preExtractedColumns = ColumnExtractor.extract(contexts, fields, preAllocatedArena);
    }

    @TearDown
    public void tearDown() {
        if (preAllocatedArena != null) {
            preAllocatedArena.close();
            preAllocatedArena = null;
        }
    }

    /**
     * Measures pure hardware SIMD vectorized kernel throughput on columnar off-heap segments.
     */
    @Benchmark
    public boolean[] benchmarkSimdVectorizedKernel() {
        return vectorizedEvaluator.evaluate(ast, preExtractedColumns, batchSize);
    }

    /**
     * Measures end-to-end SIMD-accelerated batch evaluation throughput via {@link BatchDispatcher}
     * (including Arena allocation and row-to-columnar transposition).
     */
    @Benchmark
    public BatchDispatcher.DispatchResult benchmarkSimdBatch() {
        return simdDispatcher.dispatch(ast, fields, contexts);
    }

    /**
     * Measures scalar fallback batch evaluation throughput via {@link BatchDispatcher}.
     */
    @Benchmark
    public BatchDispatcher.DispatchResult benchmarkScalarBatch() {
        return scalarDispatcher.dispatch(ast, fields, contexts);
    }
}
