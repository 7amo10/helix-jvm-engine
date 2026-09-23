package com.helix.api.ml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OnnxModelDescriptor Tests")
class OnnxModelDescriptorTest {

    @Test
    @DisplayName("Should create valid model descriptor with all attributes")
    void testCreateValidDescriptor() {
        List<String> features = List.of("amount", "velocity_1h", "device_risk_score");
        OnnxModelDescriptor descriptor = new OnnxModelDescriptor(
                "fraud_model_v1",
                "1.0.0",
                "/opt/helix/models/fraud_model_v1.onnx",
                features,
                "probabilities",
                1,
                "XGBoost fraud detection model",
                31522L,
                true
        );

        assertEquals("fraud_model_v1", descriptor.modelName());
        assertEquals("1.0.0", descriptor.version());
        assertEquals("/opt/helix/models/fraud_model_v1.onnx", descriptor.modelPath());
        assertEquals(3, descriptor.featureCount());
        assertEquals(features, descriptor.inputFeatures());
        assertEquals("probabilities", descriptor.outputTensorName());
        assertEquals(1, descriptor.outputIndex());
        assertEquals("XGBoost fraud detection model", descriptor.description());
        assertEquals(31522L, descriptor.fileSizeBytes());
        assertTrue(descriptor.active());
        assertTrue(descriptor.hasFeature("amount"));
        assertTrue(descriptor.hasFeature("velocity_1h"));
        assertFalse(descriptor.hasFeature("non_existent"));
    }

    @Test
    @DisplayName("Should support fluent builder construction")
    void testBuilderConstruction() {
        OnnxModelDescriptor descriptor = OnnxModelDescriptor.builder()
                .modelName("ast_reorder_policy")
                .version("1.0.0")
                .modelPath("models/ast_reorder_policy.onnx")
                .inputFeatures(List.of("observation"))
                .outputTensorName("action_logits")
                .outputIndex(0)
                .description("Neural AST reordering policy")
                .fileSizeBytes(231321L)
                .active(true)
                .build();

        assertNotNull(descriptor);
        assertEquals("ast_reorder_policy", descriptor.modelName());
        assertEquals(1, descriptor.featureCount());
        assertTrue(descriptor.hasFeature("observation"));
        assertEquals("action_logits", descriptor.outputTensorName());
        assertEquals(0, descriptor.outputIndex());
    }

    @Test
    @DisplayName("Should reject null or blank modelName")
    void testRejectNullOrBlankModelName() {
        assertThrows(IllegalArgumentException.class, () -> new OnnxModelDescriptor(
                null, "1.0.0", "/path", List.of("f1"), "out", 0, "desc", 100L, true));

        assertThrows(IllegalArgumentException.class, () -> new OnnxModelDescriptor(
                "   ", "1.0.0", "/path", List.of("f1"), "out", 0, "desc", 100L, true));
    }

    @Test
    @DisplayName("Should reject null or blank version")
    void testRejectNullOrBlankVersion() {
        assertThrows(IllegalArgumentException.class, () -> new OnnxModelDescriptor(
                "model", null, "/path", List.of("f1"), "out", 0, "desc", 100L, true));

        assertThrows(IllegalArgumentException.class, () -> new OnnxModelDescriptor(
                "model", "", "/path", List.of("f1"), "out", 0, "desc", 100L, true));
    }

    @Test
    @DisplayName("Should reject null or blank modelPath")
    void testRejectNullOrBlankModelPath() {
        assertThrows(IllegalArgumentException.class, () -> new OnnxModelDescriptor(
                "model", "1.0.0", null, List.of("f1"), "out", 0, "desc", 100L, true));

        assertThrows(IllegalArgumentException.class, () -> new OnnxModelDescriptor(
                "model", "1.0.0", "  ", List.of("f1"), "out", 0, "desc", 100L, true));
    }

    @Test
    @DisplayName("Should reject empty or null inputFeatures")
    void testRejectEmptyOrNullInputFeatures() {
        assertThrows(IllegalArgumentException.class, () -> new OnnxModelDescriptor(
                "model", "1.0.0", "/path", null, "out", 0, "desc", 100L, true));

        assertThrows(IllegalArgumentException.class, () -> new OnnxModelDescriptor(
                "model", "1.0.0", "/path", List.of(), "out", 0, "desc", 100L, true));
    }

    @Test
    @DisplayName("Should reject negative outputIndex")
    void testRejectNegativeOutputIndex() {
        assertThrows(IllegalArgumentException.class, () -> new OnnxModelDescriptor(
                "model", "1.0.0", "/path", List.of("f1"), "out", -1, "desc", 100L, true));
    }

    @Test
    @DisplayName("Should ensure inputFeatures list is defensively copied and unmodifiable")
    void testDefensiveCopy() {
        List<String> mutableList = new java.util.ArrayList<>(List.of("f1", "f2"));
        OnnxModelDescriptor descriptor = new OnnxModelDescriptor(
                "model", "1.0.0", "/path", mutableList, "out", 0, "desc", 100L, true);

        mutableList.add("f3");
        assertEquals(2, descriptor.inputFeatures().size());
        assertThrows(UnsupportedOperationException.class, () -> descriptor.inputFeatures().add("f4"));
    }
}
