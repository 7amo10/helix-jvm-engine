package com.helix.api.ml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 3 ML Real-World Enterprise Scenario Test")
class MlRealScenarioIntegrationTest {

    static class ProductionSimulatedRegistry implements ModelRegistry {
        private final Map<String, Map<String, OnnxModelDescriptor>> store = new ConcurrentHashMap<>();

        @Override
        public Optional<OnnxModelDescriptor> resolveModel(String modelName) {
            Map<String, OnnxModelDescriptor> versions = store.get(modelName);
            if (versions == null || versions.isEmpty()) return Optional.empty();
            return versions.values().stream().filter(OnnxModelDescriptor::active).findFirst();
        }

        @Override
        public Optional<OnnxModelDescriptor> resolveModel(String modelName, String version) {
            Map<String, OnnxModelDescriptor> versions = store.get(modelName);
            if (versions == null) return Optional.empty();
            return Optional.ofNullable(versions.get(version));
        }

        @Override
        public void registerModel(OnnxModelDescriptor descriptor) {
            Objects.requireNonNull(descriptor);
            store.computeIfAbsent(descriptor.modelName(), k -> new ConcurrentHashMap<>())
                    .put(descriptor.version(), descriptor);
        }

        @Override
        public void activateModelVersion(String modelName, String version) {
            Map<String, OnnxModelDescriptor> versions = store.get(modelName);
            if (versions == null || !versions.containsKey(version)) {
                throw new IllegalArgumentException("Unknown version " + version + " for model " + modelName);
            }
            for (Map.Entry<String, OnnxModelDescriptor> entry : versions.entrySet()) {
                boolean active = entry.getKey().equals(version);
                versions.put(entry.getKey(), entry.getValue().withActive(active));
            }
        }

        @Override
        public void invalidateModel(String modelName) {
            store.remove(modelName);
        }

        @Override
        public void invalidateModelVersion(String modelName, String version) {
            Map<String, OnnxModelDescriptor> versions = store.get(modelName);
            if (versions != null) {
                versions.remove(version);
                if (versions.isEmpty()) store.remove(modelName);
            }
        }

        @Override
        public List<OnnxModelDescriptor> listModels() {
            List<OnnxModelDescriptor> list = new ArrayList<>();
            for (String name : store.keySet()) {
                resolveModel(name).ifPresent(list::add);
            }
            return Collections.unmodifiableList(list);
        }

        @Override
        public List<OnnxModelDescriptor> listVersions(String modelName) {
            Map<String, OnnxModelDescriptor> versions = store.get(modelName);
            if (versions == null) return Collections.emptyList();
            return List.copyOf(versions.values());
        }

        @Override
        public boolean hasModel(String modelName) {
            return store.containsKey(modelName) && !store.get(modelName).isEmpty();
        }

        @Override
        public boolean hasModelVersion(String modelName, String version) {
            Map<String, OnnxModelDescriptor> versions = store.get(modelName);
            return versions != null && versions.containsKey(version);
        }
    }

    @Test
    @DisplayName("Real Scenario: Multi-Model Lifecycle, Version Promotion & Streaming Fraud Decisions")
    void testFintechFraudDetectionRealScenario() {
        ModelRegistry registry = new ProductionSimulatedRegistry();

        List<String> fraud17Features = List.of(
                "amount", "hour_of_day", "day_of_week", "merchant_category",
                "transaction_currency", "velocity_1h", "velocity_24h", "velocity_7d",
                "amount_deviation_30d", "unique_merchants_24h", "is_new_device",
                "device_risk_score", "is_vpn_or_proxy", "country_mismatch",
                "account_age_days", "is_account_suspended", "previous_chargeback"
        );

        // Step 1: Data Science team deploys fraud_model_v1 (30KB)
        OnnxModelDescriptor fraudV1 = OnnxModelDescriptor.builder()
                .modelName("fraud_model")
                .version("1.0.0")
                .modelPath("/opt/helix/models/fraud_model_v1.onnx")
                .inputFeatures(fraud17Features)
                .outputTensorName("probabilities")
                .outputIndex(1)
                .description("Production XGBoost fraud risk classifier calibrated at 2.0% CNP rate")
                .fileSizeBytes(31522L)
                .active(true)
                .build();

        registry.registerModel(fraudV1);

        // Step 2: Platform registers AST optimizer policy model (225KB)
        OnnxModelDescriptor astPolicyV1 = OnnxModelDescriptor.builder()
                .modelName("ast_reorder_policy")
                .version("1.0.0")
                .modelPath("/opt/helix/models/ast_reorder_policy.onnx")
                .inputFeatures(List.of("observation"))
                .outputTensorName("action_logits")
                .outputIndex(0)
                .description("Neural actor policy for dynamic short-circuit AST permutation")
                .fileSizeBytes(231321L)
                .active(true)
                .build();

        registry.registerModel(astPolicyV1);

        // Verify both models are discoverable in active catalog
        assertEquals(2, registry.listModels().size());
        assertTrue(registry.hasModel("fraud_model"));
        assertTrue(registry.hasModel("ast_reorder_policy"));

        // Step 3: Verify fraud model feature schema integrity
        OnnxModelDescriptor activeFraud = registry.resolveModel("fraud_model").orElseThrow();
        assertEquals("1.0.0", activeFraud.version());
        assertEquals(17, activeFraud.featureCount());
        assertTrue(activeFraud.hasFeature("device_risk_score"));
        assertTrue(activeFraud.hasFeature("is_vpn_or_proxy"));
        assertTrue(activeFraud.hasFeature("velocity_7d"));

        // Step 4: Simulate streaming transaction evaluations through OnnxInferenceResult
        long now = System.currentTimeMillis();

        // Transaction A: High-risk account takeover attack
        OnnxInferenceResult attackResult = new OnnxInferenceResult(
                activeFraud.modelName(), activeFraud.version(),
                0.985f, 1, 24500L, now
        );
        assertTrue(attackResult.isPositive(0.80f), "High risk attack must satisfy rule threshold > 0.80");
        assertEquals(98.5f, attackResult.probabilityPercent(), 1e-2f);
        assertTrue(attackResult.latencyMicros() < 50.0, "Sub-50 microsecond execution target");

        // Transaction B: Standard grocery shopping transaction
        OnnxInferenceResult safeResult = new OnnxInferenceResult(
                activeFraud.modelName(), activeFraud.version(),
                0.002f, 0, 21800L, now
        );
        assertFalse(safeResult.isPositive(0.80f), "Legitimate transaction must clear fraud rule");
        assertEquals(0.2f, safeResult.probabilityPercent(), 1e-2f);

        // Step 5: Data Science releases retrained model v2.0.0 (Zero-downtime hot promotion)
        OnnxModelDescriptor fraudV2 = OnnxModelDescriptor.builder()
                .modelName("fraud_model")
                .version("2.0.0")
                .modelPath("/opt/helix/models/fraud_model_v2.onnx")
                .inputFeatures(fraud17Features)
                .outputTensorName("probabilities")
                .outputIndex(1)
                .description("Retrained fraud model with improved velocity attribution")
                .fileSizeBytes(34200L)
                .active(false)
                .build();

        registry.registerModel(fraudV2);
        assertEquals(2, registry.listVersions("fraud_model").size());

        // Prior to promotion, v1 remains active
        assertEquals("1.0.0", registry.resolveModel("fraud_model").orElseThrow().version());

        // Promote v2.0.0 as active
        registry.activateModelVersion("fraud_model", "2.0.0");
        OnnxModelDescriptor promoted = registry.resolveModel("fraud_model").orElseThrow();
        assertEquals("2.0.0", promoted.version());
        assertTrue(promoted.active());

        // Previous v1.0.0 is retained for audit or rollback
        OnnxModelDescriptor historicalV1 = registry.resolveModel("fraud_model", "1.0.0").orElseThrow();
        assertFalse(historicalV1.active());

        // Step 6: Invalidation lifecycle test
        registry.invalidateModelVersion("fraud_model", "1.0.0");
        assertFalse(registry.hasModelVersion("fraud_model", "1.0.0"));
        assertTrue(registry.hasModelVersion("fraud_model", "2.0.0"));
    }
}
