package com.helix.api.ml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OnnxInferenceResult Tests")
class OnnxInferenceResultTest {

    @Test
    @DisplayName("Should instantiate valid inference result record")
    void testCreateInferenceResult() {
        long now = System.currentTimeMillis();
        OnnxInferenceResult result = new OnnxInferenceResult(
                "fraud_model_v1",
                "1.0.0",
                0.945f,
                1,
                24500L,
                now
        );

        assertEquals("fraud_model_v1", result.modelName());
        assertEquals("1.0.0", result.version());
        assertEquals(0.945f, result.probabilityScore(), 1e-5f);
        assertEquals(1, result.predictedClass());
        assertEquals(24500L, result.latencyNanos());
        assertEquals(now, result.evaluatedTimestamp());
        assertEquals(24.5, result.latencyMicros(), 1e-3);
        assertEquals(94.5f, result.probabilityPercent(), 1e-3f);
        assertTrue(result.isPositive(0.80f));
        assertTrue(result.isPositive(0.90f));
        assertFalse(result.isPositive(0.95f));
    }

    @Test
    @DisplayName("Should reject null modelName or version")
    void testRejectNullModelNameOrVersion() {
        assertThrows(IllegalArgumentException.class, () -> new OnnxInferenceResult(
                null, "1.0.0", 0.5f, 0, 1000L, System.currentTimeMillis()));

        assertThrows(IllegalArgumentException.class, () -> new OnnxInferenceResult(
                "model", null, 0.5f, 0, 1000L, System.currentTimeMillis()));
    }

    @Test
    @DisplayName("Should reject probability scores outside [0.0, 1.0]")
    void testRejectInvalidProbabilityScores() {
        assertThrows(IllegalArgumentException.class, () -> new OnnxInferenceResult(
                "model", "1.0.0", -0.01f, 0, 1000L, System.currentTimeMillis()));

        assertThrows(IllegalArgumentException.class, () -> new OnnxInferenceResult(
                "model", "1.0.0", 1.001f, 0, 1000L, System.currentTimeMillis()));
    }

    @Test
    @DisplayName("Should reject negative latency")
    void testRejectNegativeLatency() {
        assertThrows(IllegalArgumentException.class, () -> new OnnxInferenceResult(
                "model", "1.0.0", 0.5f, 0, -100L, System.currentTimeMillis()));
    }
}
