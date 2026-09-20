package com.helix.core.ml;

import com.helix.api.ml.OnnxInferenceResult;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OnnxSessionPool Tests")
class OnnxSessionPoolTest {

    private LocalModelRegistry registry;
    private OnnxSessionPool pool;
    private OnnxModelDescriptor fraudDescriptor;

    private static final List<String> FRAUD_FEATURES = List.of(
            "amount", "hour_of_day", "day_of_week", "merchant_category", "transaction_currency",
            "velocity_1h", "velocity_24h", "velocity_7d", "amount_deviation_30d", "unique_merchants_24h",
            "is_new_device", "device_risk_score", "is_vpn_or_proxy", "country_mismatch",
            "account_age_days", "is_account_suspended", "previous_chargeback"
    );

    @BeforeEach
    void setUp() {
        URL modelUrl = getClass().getResource("/models/fraud_model_v1.onnx");
        assertNotNull(modelUrl, "fraud_model_v1.onnx must exist in test resources");
        File modelFile = new File(modelUrl.getFile());

        fraudDescriptor = OnnxModelDescriptor.builder()
                .modelName("fraud_model_v1")
                .version("1.0.0")
                .modelPath(modelFile.getAbsolutePath())
                .inputFeatures(FRAUD_FEATURES)
                .outputTensorName("probabilities")
                .outputIndex(1)
                .fileSizeBytes(modelFile.length())
                .active(true)
                .build();

        registry = new LocalModelRegistry();
        registry.registerModel(fraudDescriptor);

        pool = new OnnxSessionPool(registry, 4); // pool size 4 per model
    }

    @AfterEach
    void tearDown() {
        if (pool != null) {
            pool.close();
        }
    }

    private ExecutionContext createSampleContext(double amount) {
        return new ExecutionContext(Map.ofEntries(
                Map.entry("amount", amount),
                Map.entry("hour_of_day", 14.0),
                Map.entry("day_of_week", 2.0),
                Map.entry("merchant_category", 1.0),
                Map.entry("transaction_currency", 0.0),
                Map.entry("velocity_1h", 2.0),
                Map.entry("velocity_24h", 6.0),
                Map.entry("velocity_7d", 18.0),
                Map.entry("amount_deviation_30d", 0.5),
                Map.entry("unique_merchants_24h", 3.0),
                Map.entry("is_new_device", 0.0),
                Map.entry("device_risk_score", 0.2),
                Map.entry("is_vpn_or_proxy", 0.0),
                Map.entry("country_mismatch", 0.0),
                Map.entry("account_age_days", 450.0),
                Map.entry("is_account_suspended", 0.0),
                Map.entry("previous_chargeback", 0.0)
        ));
    }

    @Test
    @DisplayName("Should execute single-row inference and return valid OnnxInferenceResult")
    void testExecuteInference() {
        ExecutionContext ctx = createSampleContext(500.0);
        OnnxInferenceResult result = pool.executeInference("fraud_model_v1", ctx);

        assertNotNull(result);
        assertEquals("fraud_model_v1", result.modelName());
        assertEquals("1.0.0", result.version());
        assertTrue(result.probabilityScore() >= 0.0f && result.probabilityScore() <= 1.0f);
        assertTrue(result.latencyNanos() > 0);
    }

    @Test
    @DisplayName("Should handle concurrent multi-threaded execution across virtual threads safely")
    void testConcurrentExecutionVirtualThreads() throws InterruptedException {
        int threads = 32;
        int requestsPerThread = 50;
        int totalRequests = threads * requestsPerThread;

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successCounter = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                ExecutionContext ctx = createSampleContext(100.0);
                for (int i = 0; i < requestsPerThread; i++) {
                    try {
                        OnnxInferenceResult res = pool.executeInference("fraud_model_v1", ctx);
                        if (res != null && res.probabilityScore() >= 0.0f) {
                            successCounter.incrementAndGet();
                        }
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        boolean finished = latch.await(10, TimeUnit.SECONDS);
        assertTrue(finished, "All virtual thread inferences must complete within 10 seconds");
        assertEquals(totalRequests, successCounter.get(), "Zero dropped or failed inferences under concurrency");

        executor.shutdownNow();
    }

    @Test
    @DisplayName("Should dynamically reload sessions and drain old sessions on model update")
    void testReloadAndDrainModel() {
        // Initial run
        ExecutionContext ctx = createSampleContext(500.0);
        OnnxInferenceResult res1 = pool.executeInference("fraud_model_v1", ctx);
        assertNotNull(res1);

        // Update descriptor to v1.1.0 with same file
        OnnxModelDescriptor updated = OnnxModelDescriptor.builder()
                .modelName("fraud_model_v1")
                .version("1.1.0")
                .modelPath(fraudDescriptor.modelPath())
                .inputFeatures(fraudDescriptor.inputFeatures())
                .outputTensorName("probabilities")
                .outputIndex(1)
                .active(true)
                .build();
        registry.registerModel(updated);

        pool.reloadModel("fraud_model_v1");

        OnnxInferenceResult res2 = pool.executeInference("fraud_model_v1", ctx);
        assertEquals("1.1.0", res2.version());
    }

    @Test
    @DisplayName("Should throw for unregistered model")
    void testUnregisteredModelThrows() {
        ExecutionContext ctx = new ExecutionContext(Map.of());
        assertThrows(IllegalArgumentException.class, () -> pool.executeInference("non_existent_model", ctx));
    }
}
