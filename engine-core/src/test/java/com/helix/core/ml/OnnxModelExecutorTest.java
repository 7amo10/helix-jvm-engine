package com.helix.core.ml;

import com.helix.api.ml.OnnxModelDescriptor;
import com.helix.api.ExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OnnxModelExecutor Tests")
class OnnxModelExecutorTest {

    private LocalModelRegistry registry;
    private OnnxSessionPool pool;

    private static final List<String> FRAUD_FEATURES = List.of(
            "amount", "hour_of_day", "day_of_week", "merchant_category", "transaction_currency",
            "velocity_1h", "velocity_24h", "velocity_7d", "amount_deviation_30d", "unique_merchants_24h",
            "is_new_device", "device_risk_score", "is_vpn_or_proxy", "country_mismatch",
            "account_age_days", "is_account_suspended", "previous_chargeback"
    );

    @BeforeEach
    void setUp() {
        URL modelUrl = getClass().getResource("/models/fraud_model_v1.onnx");
        assertNotNull(modelUrl);
        File modelFile = new File(modelUrl.getFile());

        OnnxModelDescriptor descriptor = OnnxModelDescriptor.builder()
                .modelName("fraud_model_v1")
                .version("1.0.0")
                .modelPath(modelFile.getAbsolutePath())
                .inputFeatures(FRAUD_FEATURES)
                .outputTensorName("probabilities")
                .outputIndex(1)
                .active(true)
                .build();

        registry = new LocalModelRegistry();
        registry.registerModel(descriptor);

        pool = new OnnxSessionPool(registry, 2);
        OnnxModelExecutor.setSessionPool(pool);
    }

    @AfterEach
    void tearDown() {
        OnnxModelExecutor.reset();
        if (pool != null) {
            pool.close();
        }
    }

    @Test
    @DisplayName("Should evaluate model through static OnnxModelExecutor bridge")
    void testEvaluateStaticBridge() {
        ExecutionContext ctx = new ExecutionContext(Map.ofEntries(
                Map.entry("amount", 250.0),
                Map.entry("hour_of_day", 10.0),
                Map.entry("day_of_week", 1.0),
                Map.entry("merchant_category", 1.0),
                Map.entry("transaction_currency", 0.0),
                Map.entry("velocity_1h", 1.0),
                Map.entry("velocity_24h", 3.0),
                Map.entry("velocity_7d", 10.0),
                Map.entry("amount_deviation_30d", 0.1),
                Map.entry("unique_merchants_24h", 2.0),
                Map.entry("is_new_device", 0.0),
                Map.entry("device_risk_score", 0.15),
                Map.entry("is_vpn_or_proxy", 0.0),
                Map.entry("country_mismatch", 0.0),
                Map.entry("account_age_days", 1200.0),
                Map.entry("is_account_suspended", 0.0),
                Map.entry("previous_chargeback", 0.0)
        ));

        double score = OnnxModelExecutor.evaluate("fraud_model_v1", ctx);
        assertTrue(score >= 0.0 && score <= 1.0, "Score must be normalized probability");

        double customScore = OnnxModelExecutor.evaluate("fraud_model_v1", "probabilities", 1, ctx);
        assertEquals(score, customScore, 0.0001);
    }
}
