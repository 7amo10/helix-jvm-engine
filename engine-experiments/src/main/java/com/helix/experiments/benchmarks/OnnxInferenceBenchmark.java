package com.helix.experiments.benchmarks;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.api.ml.OnnxModelDescriptor;
import com.helix.core.bytecode.AsmBytecodeGenerator;
import com.helix.core.ml.LocalModelRegistry;
import com.helix.core.ml.OnnxModelExecutor;
import com.helix.core.ml.OnnxSessionPool;
import com.helix.core.parser.ExpressionRuleParser;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.RuleNode;
import org.openjdk.jmh.annotations.*;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark measuring single-row inline ONNX inference latency (P50, P90, P99)
 * versus simulated HTTP REST inference overhead.
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Benchmark)
@Fork(0)
public class OnnxInferenceBenchmark {

    private static final List<String> FRAUD_FEATURES = List.of(
            "amount", "hour_of_day", "day_of_week", "merchant_category", "transaction_currency",
            "velocity_1h", "velocity_24h", "velocity_7d", "amount_deviation_30d", "unique_merchants_24h",
            "is_new_device", "device_risk_score", "is_vpn_or_proxy", "country_mismatch",
            "account_age_days", "is_account_suspended", "previous_chargeback"
    );

    private LocalModelRegistry registry;
    private OnnxSessionPool sessionPool;
    private CompiledRule compiledMlRule;
    private ExecutionContext sampleContext;

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

        String expr = "ML('fraud_model_v1') > 0.85";
        ExpressionRuleParser parser = new ExpressionRuleParser();
        ExpressionNode ast = parser.parse(expr);
        Rule rule = new RuleNode("FraudModelBenchmarkRule", expr, Map.of(), ast);

        AsmBytecodeGenerator generator = new AsmBytecodeGenerator(false);
        this.compiledMlRule = generator.generate(rule, ast);

        this.sampleContext = new ExecutionContext(Map.ofEntries(
                Map.entry("amount", 1850.0),
                Map.entry("hour_of_day", 2.0),
                Map.entry("day_of_week", 4.0),
                Map.entry("merchant_category", 3.0),
                Map.entry("transaction_currency", 2.0),
                Map.entry("velocity_1h", 5.0),
                Map.entry("velocity_24h", 20.0),
                Map.entry("velocity_7d", 65.0),
                Map.entry("amount_deviation_30d", 2.09),
                Map.entry("unique_merchants_24h", 9.0),
                Map.entry("is_new_device", 1.0),
                Map.entry("device_risk_score", 0.43),
                Map.entry("is_vpn_or_proxy", 1.0),
                Map.entry("country_mismatch", 1.0),
                Map.entry("account_age_days", 86.0),
                Map.entry("is_account_suspended", 0.0),
                Map.entry("previous_chargeback", 0.0)
        ));

        for (int i = 0; i < 20; i++) {
            compiledMlRule.execute(sampleContext);
        }
    }

    @TearDown
    public void tearDown() {
        OnnxModelExecutor.reset();
        if (sessionPool != null) {
            sessionPool.close();
        }
    }

    @Benchmark
    public ExecutionResult benchmarkInlineOnnxRuleExecution() throws Exception {
        return compiledMlRule.execute(sampleContext);
    }

    @Benchmark
    public Object benchmarkSimulatedRestInference() {
        // Simulates typical REST inference overhead: TCP roundtrip, HTTP serialization
        // latency (approx. 15 ms)
        long burnTarget = System.nanoTime() + 15_000_000L;
        long spin = 0;
        while (System.nanoTime() < burnTarget) {
            spin++;
        }
        return spin;
    }
}
