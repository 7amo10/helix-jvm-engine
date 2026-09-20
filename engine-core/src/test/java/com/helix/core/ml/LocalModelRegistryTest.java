package com.helix.core.ml;

import com.helix.api.ml.OnnxModelDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LocalModelRegistry Tests")
class LocalModelRegistryTest {

    private LocalModelRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new LocalModelRegistry();
    }

    private OnnxModelDescriptor createDescriptor(String modelName, String version, boolean active) {
        return OnnxModelDescriptor.builder()
                .modelName(modelName)
                .version(version)
                .modelPath("/models/" + modelName + "_" + version + ".onnx")
                .inputFeatures(List.of("amount", "oldbalanceOrg", "newbalanceOrig"))
                .outputTensorName("probabilities")
                .outputIndex(1)
                .description("Test model")
                .active(active)
                .build();
    }

    @Test
    @DisplayName("Should register and resolve active model descriptor")
    void testRegisterAndResolveActive() {
        OnnxModelDescriptor v1 = createDescriptor("fraud_model", "1.0.0", true);
        registry.registerModel(v1);

        assertTrue(registry.hasModel("fraud_model"));
        Optional<OnnxModelDescriptor> resolved = registry.resolveModel("fraud_model");
        assertTrue(resolved.isPresent());
        assertEquals("1.0.0", resolved.get().version());
    }

    @Test
    @DisplayName("Should support multiple versions and active version switching")
    void testMultipleVersionsAndActivation() {
        OnnxModelDescriptor v1 = createDescriptor("fraud_model", "1.0.0", true);
        OnnxModelDescriptor v2 = createDescriptor("fraud_model", "2.0.0", false);

        registry.registerModel(v1);
        registry.registerModel(v2);

        assertEquals(2, registry.listVersions("fraud_model").size());
        assertEquals("1.0.0", registry.resolveModel("fraud_model").orElseThrow().version());

        registry.activateModelVersion("fraud_model", "2.0.0");
        assertEquals("2.0.0", registry.resolveModel("fraud_model").orElseThrow().version());
        assertFalse(registry.resolveModel("fraud_model", "1.0.0").orElseThrow().active());
        assertTrue(registry.resolveModel("fraud_model", "2.0.0").orElseThrow().active());
    }

    @Test
    @DisplayName("Should invalidate specific version or entire model")
    void testInvalidation() {
        OnnxModelDescriptor v1 = createDescriptor("fraud_model", "1.0.0", true);
        OnnxModelDescriptor v2 = createDescriptor("fraud_model", "2.0.0", false);
        registry.registerModel(v1);
        registry.registerModel(v2);

        registry.invalidateModelVersion("fraud_model", "1.0.0");
        assertFalse(registry.hasModelVersion("fraud_model", "1.0.0"));
        assertTrue(registry.hasModelVersion("fraud_model", "2.0.0"));

        registry.invalidateModel("fraud_model");
        assertFalse(registry.hasModel("fraud_model"));
        assertTrue(registry.listModels().isEmpty());
    }

    @Test
    @DisplayName("Should scan directory and discover ONNX model files")
    void testDirectoryScanDiscovery(@TempDir Path tempDir) throws IOException {
        Path model1 = tempDir.resolve("model_alpha.onnx");
        Path model2 = tempDir.resolve("model_beta.onnx");
        Files.writeString(model1, "mock binary alpha");
        Files.writeString(model2, "mock binary beta");

        LocalModelRegistry diskRegistry = new LocalModelRegistry(tempDir);
        int discovered = diskRegistry.scanModels(name -> List.of("f1", "f2"));
        assertEquals(2, discovered);
        assertTrue(diskRegistry.hasModel("model_alpha"));
        assertTrue(diskRegistry.hasModel("model_beta"));
    }
}
