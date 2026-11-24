package com.helix.experiments.benchmarks;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.core.batch.VectorizabilityClassifier;
import com.helix.core.batch.VectorizableRule;
import com.helix.core.parser.ast.AstBuilder;
import com.helix.core.parser.ast.ExpressionNode;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark suite measuring rule AST vectorizability classification overhead
 * and cached compiled-rule inspection latency (target &lt; 1 microsecond per rule).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Thread)
@Fork(0)
public class VectorizabilityClassifierBenchmark {

    private VectorizabilityClassifier classifier;
    private ExpressionNode vectorizableAst;
    private ExpressionNode nonVectorizableAst;
    private CompiledRule annotatedCompiledRule;

    @VectorizableRule(true)
    private static class BenchmarkVectorizableRule implements CompiledRule {
        @Override
        public String getName() {
            return "BenchmarkVectorizableRule";
        }

        @Override
        public String getVersion() {
            return "1.0.0";
        }

        @Override
        public ExecutionResult execute(ExecutionContext context) {
            return ExecutionResult.success(Boolean.TRUE, 0L);
        }
    }

    @Setup
    public void setup() throws Exception {
        this.classifier = new VectorizabilityClassifier();
        AstBuilder astBuilder = new AstBuilder();

        this.vectorizableAst = astBuilder.buildAst(
                "(amount > 1000.0 && riskScore < 0.75) || (velocity * 2.0 + weight) >= 500.0");
        this.nonVectorizableAst = astBuilder.buildAst(
                "ML('models/fraud_detector.onnx', amount, velocity) > 0.8");
        this.annotatedCompiledRule = new BenchmarkVectorizableRule();

        // Warm up compiled-rule cache
        this.classifier.classify(annotatedCompiledRule);
    }

    /**
     * Measures AST traversal classification latency for a vectorizable numeric rule.
     */
    @Benchmark
    public boolean benchmarkClassifyVectorizableAst() {
        return classifier.classify(vectorizableAst);
    }

    /**
     * Measures AST traversal classification latency for a non-vectorizable ML rule.
     */
    @Benchmark
    public boolean benchmarkClassifyNonVectorizableAst() {
        return classifier.classify(nonVectorizableAst);
    }

    /**
     * Measures O(1) cached classification lookup for a {@link CompiledRule}.
     */
    @Benchmark
    public boolean benchmarkClassifyCompiledRuleCached() {
        return classifier.classify(annotatedCompiledRule);
    }

    /**
     * Measures referenced field extraction latency on a vectorizable AST.
     */
    @Benchmark
    public List<String> benchmarkExtractReferencedFields() {
        return classifier.extractReferencedFields(vectorizableAst);
    }
}
