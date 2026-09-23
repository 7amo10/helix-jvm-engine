package com.helix.core.ml;

import com.helix.api.ml.OnnxInferenceResult;
import com.helix.api.ml.OnnxModelDescriptor;
import com.helix.api.ExecutionContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Real-World Scenario: ONNX In-Process Inference Execution & Concurrency")
class OnnxRealScenarioIntegrationTest {

    private static LocalModelRegistry registry;
    private static OnnxSessionPool pool;

    private static final List<String> FRAUD_FEATURES = List.of(
            "amount", "hour_of_day", "day_of_week", "merchant_category", "transaction_currency",
            "velocity_1h", "velocity_24h", "velocity_7d", "amount_deviation_30d", "unique_merchants_24h",
            "is_new_device", "device_risk_score", "is_vpn_or_proxy", "country_mismatch",
            "account_age_days", "is_account_suspended", "previous_chargeback"
    );

    @BeforeAll
    static void init() {
        URL modelUrl = OnnxRealScenarioIntegrationTest.class.getResource("/models/fraud_model_v1.onnx");
        assertNotNull(modelUrl, "fraud_model_v1.onnx must exist in test resources");
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

        pool = new OnnxSessionPool(registry, 8); // Pool of 8 sessions for high concurrency
        OnnxModelExecutor.setSessionPool(pool);
    }

    @AfterAll
    static void cleanup() {
        OnnxModelExecutor.reset();
        if (pool != null) {
            pool.close();
        }
    }

    private ExecutionContext createLegitimateTransaction() {
        // Real benign retail purchase: $66.55, established device, account age 3511 days
        return new ExecutionContext(Map.ofEntries(
                Map.entry("amount", 66.55),
                Map.entry("hour_of_day", 10.0),
                Map.entry("day_of_week", 4.0),
                Map.entry("merchant_category", 0.0),
                Map.entry("transaction_currency", 0.0),
                Map.entry("velocity_1h", 1.0),
                Map.entry("velocity_24h", 5.0),
                Map.entry("velocity_7d", 21.0),
                Map.entry("amount_deviation_30d", -1.10),
                Map.entry("unique_merchants_24h", 5.0),
                Map.entry("is_new_device", 0.0),
                Map.entry("device_risk_score", 0.35),
                Map.entry("is_vpn_or_proxy", 0.0),
                Map.entry("country_mismatch", 0.0),
                Map.entry("account_age_days", 3511.0),
                Map.entry("is_account_suspended", 0.0),
                Map.entry("previous_chargeback", 1.0)
        ));
    }

    private ExecutionContext createFraudulentTransaction() {
        // Real high-risk burst fraud: $1728.70, new device, velocity 5/hr, country mismatch, young account
        return new ExecutionContext(Map.ofEntries(
                Map.entry("amount", 1728.70),
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
                Map.entry("is_vpn_or_proxy", 0.0),
                Map.entry("country_mismatch", 1.0),
                Map.entry("account_age_days", 86.0),
                Map.entry("is_account_suspended", 0.0),
                Map.entry("previous_chargeback", 0.0)
        ));
    }

    @Test
    @DisplayName("Real Scenario: Model accurately discriminates legitimate vs fraudulent transactions")
    void testFraudModelAccuracyDiscrimination() {
        ExecutionContext legitTx = createLegitimateTransaction();
        ExecutionContext fraudTx = createFraudulentTransaction();

        OnnxInferenceResult legitResult = pool.executeInference("fraud_model_v1", legitTx);
        OnnxInferenceResult fraudResult = pool.executeInference("fraud_model_v1", fraudTx);

        assertNotNull(legitResult);
        assertNotNull(fraudResult);

        System.out.printf("Real Scenario Legit Transaction Fraud Probability: %.4f%%%n", legitResult.probabilityPercent());
        System.out.printf("Real Scenario Fraud Transaction Fraud Probability: %.4f%%%n", fraudResult.probabilityPercent());

        // Legitimate transaction should have very low fraud probability (< 5%)
        assertTrue(legitResult.probabilityScore() < 0.05f,
                "Legitimate payment should yield low fraud probability, got: " + legitResult.probabilityScore());

        // Fraudulent transaction should have very high fraud probability (> 90%)
        assertTrue(fraudResult.probabilityScore() > 0.90f,
                "Account draining transfer should yield high fraud probability, got: " + fraudResult.probabilityScore());

        // Predicted class check
        assertEquals(0, legitResult.predictedClass(), "Legitimate transaction should be classified as 0");
        assertEquals(1, fraudResult.predictedClass(), "Fraudulent transaction should be classified as 1");
    }

    @Test
    @DisplayName("Real Scenario: Single-row inference completes in < 0.4 ms (400 µs) on CPU")
    void testInferenceLatencySlaBenchmark() {
        ExecutionContext ctx = createLegitimateTransaction();

        // Warm up (JIT compilation and JNI caches)
        for (int i = 0; i < 200; i++) {
            pool.executeInference("fraud_model_v1", ctx);
        }

        int iterations = 1000;
        List<Long> latencies = new ArrayList<>(iterations);

        for (int i = 0; i < iterations; i++) {
            OnnxInferenceResult result = pool.executeInference("fraud_model_v1", ctx);
            latencies.add(result.latencyNanos());
        }

        Collections.sort(latencies);
        long p50Nanos = latencies.get((int) (iterations * 0.50));
        long p95Nanos = latencies.get((int) (iterations * 0.95));
        long p99Nanos = latencies.get((int) (iterations * 0.99));
        double meanMicros = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1000.0;

        double p50Micros = p50Nanos / 1000.0;
        double p95Micros = p95Nanos / 1000.0;
        double p99Micros = p99Nanos / 1000.0;

        System.out.printf("ONNX Inference Latency SLA Benchmark: Mean = %.2f µs, P50 = %.2f µs, P95 = %.2f µs, P99 = %.2f µs%n",
                meanMicros, p50Micros, p95Micros, p99Micros);

        // Acceptance Criteria: Single-row inference execution completes in < 0.4 ms (400 µs)
        assertTrue(p50Micros < 400.0,
                String.format("P50 latency must be < 400 µs (0.4 ms), was: %.2f µs", p50Micros));
        assertTrue(meanMicros < 400.0,
                String.format("Mean latency must be < 400 µs (0.4 ms), was: %.2f µs", meanMicros));
    }

    @Test
    @DisplayName("Real Scenario: 100+ parallel virtual threads execute 10,000+ inferences without JNI leaks or JVM crashes")
    void testMassiveVirtualThreadConcurrency() throws InterruptedException {
        int threadCount = 100;
        int requestsPerThread = 100;
        int totalRequests = threadCount * requestsPerThread;

        ExecutorService vThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalRequests);
        AtomicInteger successCounter = new AtomicInteger(0);

        ExecutionContext legit = createLegitimateTransaction();
        ExecutionContext fraud = createFraudulentTransaction();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            vThreadExecutor.submit(() -> {
                try {
                    startGate.await();
                    ExecutionContext sample = (threadId % 2 == 0) ? legit : fraud;
                    for (int i = 0; i < requestsPerThread; i++) {
                        OnnxInferenceResult res = pool.executeInference("fraud_model_v1", sample);
                        if (res != null && res.latencyNanos() > 0) {
                            successCounter.incrementAndGet();
                        }
                        endGate.countDown();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        long startNanos = System.nanoTime();
        startGate.countDown();

        boolean finished = endGate.await(15, TimeUnit.SECONDS);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertTrue(finished, "All 10,000 virtual-thread requests must finish within 15 seconds");
        assertEquals(totalRequests, successCounter.get(), "Zero dropped requests across 100 parallel threads");

        double opsPerSec = (totalRequests * 1000.0) / elapsedMillis;
        System.out.printf("Massive Concurrency: %d inferences across %d virtual threads completed in %d ms (%.2f ops/sec)%n",
                totalRequests, threadCount, elapsedMillis, opsPerSec);

        vThreadExecutor.shutdownNow();
    }
}
