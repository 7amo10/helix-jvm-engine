package com.helix.api.ml;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ModelRegistry SPI Contract Tests")
class ModelRegistryTest {

    static class InMemoryModelRegistry implements ModelRegistry {
        private final Map<String, Map<String, OnnxModelDescriptor>> registry = new ConcurrentHashMap<>();

        @Override
        public Optional<OnnxModelDescriptor> resolveModel(String modelName) {
            Map<String, OnnxModelDescriptor> versions = registry.get(modelName);
            if (versions == null || versions.isEmpty()) {
                return Optional.empty();
            }
            return versions.values().stream().filter(OnnxModelDescriptor::active).findFirst()
                    .or(() -> versions.values().stream().max(Comparator.comparing(OnnxModelDescriptor::version)));
        }

        @Override
        public Optional<OnnxModelDescriptor> resolveModel(String modelName, String version) {
            Map<String, OnnxModelDescriptor> versions = registry.get(modelName);
            if (versions == null) return Optional.empty();
            return Optional.ofNullable(versions.get(version));
        }

        @Override
        public void registerModel(OnnxModelDescriptor descriptor) {
            Objects.requireNonNull(descriptor, "descriptor cannot be null");
            registry.computeIfAbsent(descriptor.modelName(), k -> new ConcurrentHashMap<>())
                    .put(descriptor.version(), descriptor);
        }

        @Override
        public void activateModelVersion(String modelName, String version) {
            Map<String, OnnxModelDescriptor> versions = registry.get(modelName);
            if (versions == null || !versions.containsKey(version)) {
                throw new IllegalArgumentException("Model version not found: " + modelName + "@" + version);
            }
            // deactivate others and activate target
            for (Map.Entry<String, OnnxModelDescriptor> entry : versions.entrySet()) {
                boolean isActive = entry.getKey().equals(version);
                OnnxModelDescriptor updated = entry.getValue().withActive(isActive);
                versions.put(entry.getKey(), updated);
            }
        }

        @Override
        public void invalidateModel(String modelName) {
            registry.remove(modelName);
        }

        @Override
        public void invalidateModelVersion(String modelName, String version) {
            Map<String, OnnxModelDescriptor> versions = registry.get(modelName);
            if (versions != null) {
                versions.remove(version);
                if (versions.isEmpty()) {
                    registry.remove(modelName);
                }
            }
        }

        @Override
        public List<OnnxModelDescriptor> listModels() {
            List<OnnxModelDescriptor> activeModels = new ArrayList<>();
            for (String modelName : registry.keySet()) {
                resolveModel(modelName).ifPresent(activeModels::add);
            }
            return Collections.unmodifiableList(activeModels);
        }

        @Override
        public List<OnnxModelDescriptor> listVersions(String modelName) {
            Map<String, OnnxModelDescriptor> versions = registry.get(modelName);
            if (versions == null) return Collections.emptyList();
            return List.copyOf(versions.values());
        }

        @Override
        public boolean hasModel(String modelName) {
            return registry.containsKey(modelName) && !registry.get(modelName).isEmpty();
        }

        @Override
        public boolean hasModelVersion(String modelName, String version) {
            Map<String, OnnxModelDescriptor> versions = registry.get(modelName);
            return versions != null && versions.containsKey(version);
        }
    }

    @Test
    @DisplayName("Should register, activate, and resolve model versions accurately")
    void testModelRegistryLifecycle() {
        ModelRegistry registry = new InMemoryModelRegistry();

        OnnxModelDescriptor v1 = new OnnxModelDescriptor(
                "fraud_model", "1.0.0", "/models/fraud_v1.onnx", List.of("f1"), "out", 1, "v1", 100L, true
        );
        OnnxModelDescriptor v2 = new OnnxModelDescriptor(
                "fraud_model", "2.0.0", "/models/fraud_v2.onnx", List.of("f1", "f2"), "out", 1, "v2", 200L, false
        );

        registry.registerModel(v1);
        registry.registerModel(v2);

        assertTrue(registry.hasModel("fraud_model"));
        assertTrue(registry.hasModelVersion("fraud_model", "1.0.0"));
        assertTrue(registry.hasModelVersion("fraud_model", "2.0.0"));
        assertFalse(registry.hasModelVersion("fraud_model", "3.0.0"));

        // Default active is v1
        Optional<OnnxModelDescriptor> active = registry.resolveModel("fraud_model");
        assertTrue(active.isPresent());
        assertEquals("1.0.0", active.get().version());

        // Activate v2
        registry.activateModelVersion("fraud_model", "2.0.0");
        Optional<OnnxModelDescriptor> newActive = registry.resolveModel("fraud_model");
        assertTrue(newActive.isPresent());
        assertEquals("2.0.0", newActive.get().version());
        assertTrue(newActive.get().active());

        // Check listing
        List<OnnxModelDescriptor> allActive = registry.listModels();
        assertEquals(1, allActive.size());
        assertEquals("2.0.0", allActive.get(0).version());

        List<OnnxModelDescriptor> versions = registry.listVersions("fraud_model");
        assertEquals(2, versions.size());

        // Invalidate specific version
        registry.invalidateModelVersion("fraud_model", "1.0.0");
        assertFalse(registry.hasModelVersion("fraud_model", "1.0.0"));
        assertTrue(registry.hasModelVersion("fraud_model", "2.0.0"));

        // Invalidate entire model
        registry.invalidateModel("fraud_model");
        assertFalse(registry.hasModel("fraud_model"));
        assertEquals(0, registry.listModels().size());
    }
}
