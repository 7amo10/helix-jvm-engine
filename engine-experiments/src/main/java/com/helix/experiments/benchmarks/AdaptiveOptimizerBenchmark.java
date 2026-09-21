package com.helix.experiments.benchmarks;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.api.ml.OnnxModelDescriptor;
import com.helix.core.RuleCompiler;
import com.helix.core.bytecode.AdaptiveAstOptimizer;
import com.helix.core.bytecode.AsmBytecodeGenerator;
import com.helix.core.cache.CacheKey;
import com.helix.core.cache.TieredRuleCache;
import com.helix.core.ml.LocalModelRegistry;
import com.helix.core.ml.OnnxModelExecutor;
import com.helix.core.ml.OnnxSessionPool;
import com.helix.core.parser.ExpressionRuleParser;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.RuleNode;
import com.helix.core.reorder.RatioSortPolicy;
import com.helix.profiler.node.AstNodeProfiler;
import org.openjdk.jmh.annotations.*;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH Microbenchmark suite measuring unoptimized static AST vs adaptively reordered AST
 * execution throughput and latency under skewed traffic distributions, and verifying
 * non-ML baseline rule evaluation (< 10 ns).
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
@Fork(0)
public class AdaptiveOptimizerBenchmark {

    private static final List<String> FRAUD_FEATURES = List.of(
            "amount", "hour_of_day", "day_of_week", "merchant_category", "transaction_currency",
            "velocity_1h", "velocity_24h", "velocity_7d", "amount_deviation_30d", "unique_merchants_24h",
            "is_new_device", "device_risk_score", "is_vpn_or_proxy", "country_mismatch",
            "account_age_days", "is_account_suspended", "previous_chargeback"
    );

    private LocalModelRegistry registry;
    private OnnxSessionPool sessionPool;
    private TieredRuleCache cache;

    private CompiledRule unoptimizedRule;
    private CompiledRule adaptivelyOptimizedRule;
    private CompiledRule baselineNonMlRule;

    private ExecutionContext[] skewedWorkload;
    private ExecutionContext nonMlContext;
    private final AtomicInteger cursor = new AtomicInteger(0);

    @Setup
    public void setup() throws Exception {
        URL modelUrl = getClass().getResource("/models/fraud_model_v1.onnx");
        File modelFile;
        if (modelUrl != null && "file".equals(modelUrl.getProtocol())) {
            modelFile = new File(modelUrl.getFile());
        } else {
            modelFile = File.createTempFile("fraud_model_v1", ".onnx");
            modelFile.deleteOnExit();
            try (InputStream in = getClass().getResourceAsStream("/models/fraud_model_v1.onnx")) {
                if (in != null) {
                    Files.copy(in, modelFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        OnnxModelDescriptor descriptor = OnnxModelDescriptor.builder()
                .modelName("fraud_model_v1")
                .version("1.0.0")
                .modelPath(modelFile.getAbsolutePath())
                .inputFeatures(FRAUD_FEATURES)
                .outputTensorName("probabilities")
                .outputIndex(1)
                .active(true)
                .build();

        this.registry = new LocalModelRegistry();
        this.registry.registerModel(descriptor);
        this.sessionPool = new OnnxSessionPool(registry, 8);
        OnnxModelExecutor.setSessionPool(sessionPool);

        this.cache = new TieredRuleCache();

        // Build unoptimized expression: ML runs first on all transactions
        String unoptExpr = "ML('fraud_model_v1') > 0.85 && amount > 1000 && is_vpn_or_proxy == 1.0";
        ExpressionRuleParser parser = new ExpressionRuleParser();
        ExpressionNode unoptAst = parser.parse(unoptExpr);
        Rule unoptRule = new RuleNode("AdaptiveBenchmarkRule", unoptExpr, Map.of(), unoptAst);

        AsmBytecodeGenerator generator = new AsmBytecodeGenerator(true);
        this.unoptimizedRule = generator.generate(unoptRule, unoptAst);

        // Build 100-transaction skewed workload (95 benign, 5 fraud)
        this.skewedWorkload = new ExecutionContext[100];
        for (int i = 0; i < 95; i++) {
            skewedWorkload[i] = createTransactionContext(50.0, 0.0, false);
        }
        for (int i = 95; i < 100; i++) {
            skewedWorkload[i] = createTransactionContext(2000.0, 1.0, true);
        }

        // Gather live profiler telemetry by running baseline workload
        AstNodeProfiler.reset();
        AstNodeProfiler.setEnabled(true);
        CacheKey cacheKey = new CacheKey(unoptRule);
        cache.put(cacheKey, unoptimizedRule);
        for (ExecutionContext ctx : skewedWorkload) {
            unoptimizedRule.execute(ctx);
        }

        // Optimize AST using AdaptiveAstOptimizer
        AdaptiveAstOptimizer optimizer = new AdaptiveAstOptimizer(new RatioSortPolicy());
        ExpressionNode optAst = optimizer.optimize("AdaptiveBenchmarkRule", unoptAst);
        Rule optRule = new RuleNode("AdaptiveBenchmarkRule", unoptExpr, Map.of(), optAst);
        this.adaptivelyOptimizedRule = generator.generate(optRule, optAst);

        // Compile baseline non-ML rule for regression check (< 10 ns baseline)
        RuleCompiler compiler = new RuleCompiler();
        String nonMlJson = """
                {
                    "name": "NonMlBaselineRule",
                    "expression": "(score >= 70 && active == true) || priority == 10",
                    "inputSchema": {
                        "score": "integer",
                        "active": "boolean",
                        "priority": "integer"
                    }
                }
                """;
        this.baselineNonMlRule = compiler.compile(nonMlJson);
        this.nonMlContext = new ExecutionContext(Map.of("score", 85, "active", true, "priority", 5));

        // Warm up rules
        for (int i = 0; i < 50; i++) {
            unoptimizedRule.execute(skewedWorkload[i % 100]);
            adaptivelyOptimizedRule.execute(skewedWorkload[i % 100]);
            baselineNonMlRule.execute(nonMlContext);
        }
    }

    @TearDown
    public void tearDown() {
        AstNodeProfiler.reset();
        OnnxModelExecutor.reset();
        if (sessionPool != null) {
            sessionPool.close();
        }
        if (cache != null) {
            cache.close();
        }
    }

    private ExecutionContext createTransactionContext(double amount, double vpn, boolean isFraud) {
        return new ExecutionContext(Map.ofEntries(
                Map.entry("amount", amount),
                Map.entry("hour_of_day", isFraud ? 2.0 : 10.0),
                Map.entry("day_of_week", 4.0),
                Map.entry("merchant_category", isFraud ? 3.0 : 0.0),
                Map.entry("transaction_currency", isFraud ? 2.0 : 0.0),
                Map.entry("velocity_1h", isFraud ? 5.0 : 1.0),
                Map.entry("velocity_24h", isFraud ? 20.0 : 5.0),
                Map.entry("velocity_7d", isFraud ? 65.0 : 21.0),
                Map.entry("amount_deviation_30d", isFraud ? 2.09 : -1.10),
                Map.entry("unique_merchants_24h", isFraud ? 9.0 : 5.0),
                Map.entry("is_new_device", isFraud ? 1.0 : 0.0),
                Map.entry("device_risk_score", isFraud ? 0.43 : 0.35),
                Map.entry("is_vpn_or_proxy", vpn),
                Map.entry("country_mismatch", isFraud ? 1.0 : 0.0),
                Map.entry("account_age_days", isFraud ? 86.0 : 3511.0),
                Map.entry("is_account_suspended", 0.0),
                Map.entry("previous_chargeback", isFraud ? 0.0 : 1.0)
        ));
    }

    private ExecutionContext nextContext() {
        int idx = (cursor.getAndIncrement() & 0x7FFFFFFF) % skewedWorkload.length;
        return skewedWorkload[idx];
    }

    @Benchmark
    public ExecutionResult benchmarkUnoptimizedStaticAst() throws Exception {
        return unoptimizedRule.execute(nextContext());
    }

    @Benchmark
    public ExecutionResult benchmarkAdaptivelyOptimizedAst() throws Exception {
        return adaptivelyOptimizedRule.execute(nextContext());
    }

    @Benchmark
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public ExecutionResult benchmarkBaselineNonMlRule() throws Exception {
        return baselineNonMlRule.execute(nonMlContext);
    }
}
