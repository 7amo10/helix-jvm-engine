package com.helix.core.ml;

import com.helix.api.ml.ModelRegistry;
import com.helix.api.ml.OnnxModelDescriptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * In-memory and filesystem-backed implementation of {@link ModelRegistry}.
 *
 * <p>Provides thread-safe model registration, semantic version resolution,
 * active version activation, and filesystem discovery for local ONNX model stores.</p>
 */
public class LocalModelRegistry implements ModelRegistry {

    private final Path baseDirectory;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, OnnxModelDescriptor>> modelStorage = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> activeVersions = new ConcurrentHashMap<>();

    public LocalModelRegistry() {
        this.baseDirectory = null;
    }

    public LocalModelRegistry(Path baseDirectory) {
        this.baseDirectory = baseDirectory;
    }

    public Optional<Path> getBaseDirectory() {
        return Optional.ofNullable(baseDirectory);
    }

    @Override
    public Optional<OnnxModelDescriptor> resolveModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return Optional.empty();
        }
        String activeVersion = activeVersions.get(modelName);
        if (activeVersion != null) {
            return resolveModel(modelName, activeVersion);
        }
        ConcurrentHashMap<String, OnnxModelDescriptor> versions = modelStorage.get(modelName);
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        return versions.values().stream().filter(OnnxModelDescriptor::active).findFirst();
    }

    @Override
    public Optional<OnnxModelDescriptor> resolveModel(String modelName, String version) {
        if (modelName == null || version == null) {
            return Optional.empty();
        }
        ConcurrentHashMap<String, OnnxModelDescriptor> versions = modelStorage.get(modelName);
        if (versions == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(versions.get(version));
    }

    @Override
    public void registerModel(OnnxModelDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor cannot be null");
        String name = descriptor.modelName();
        String version = descriptor.version();

        ConcurrentHashMap<String, OnnxModelDescriptor> versions =
                modelStorage.computeIfAbsent(name, k -> new ConcurrentHashMap<>());

        boolean shouldActivate = descriptor.active() || versions.isEmpty() || !activeVersions.containsKey(name);

        if (shouldActivate) {
            // Deactivate existing versions
            for (Map.Entry<String, OnnxModelDescriptor> entry : versions.entrySet()) {
                if (entry.getValue().active() && !entry.getKey().equals(version)) {
                    entry.setValue(entry.getValue().withActive(false));
                }
            }
            activeVersions.put(name, version);
            versions.put(version, descriptor.withActive(true));
        } else {
            versions.put(version, descriptor);
        }
    }

    @Override
    public void activateModelVersion(String modelName, String version) {
        Objects.requireNonNull(modelName, "modelName cannot be null");
        Objects.requireNonNull(version, "version cannot be null");

        ConcurrentHashMap<String, OnnxModelDescriptor> versions = modelStorage.get(modelName);
        if (versions == null || !versions.containsKey(version)) {
            throw new IllegalArgumentException("Model or version does not exist: " + modelName + " " + version);
        }

        for (Map.Entry<String, OnnxModelDescriptor> entry : versions.entrySet()) {
            boolean isTarget = entry.getKey().equals(version);
            entry.setValue(entry.getValue().withActive(isTarget));
        }
        activeVersions.put(modelName, version);
    }

    @Override
    public void invalidateModel(String modelName) {
        if (modelName != null) {
            modelStorage.remove(modelName);
            activeVersions.remove(modelName);
        }
    }

    @Override
    public void invalidateModelVersion(String modelName, String version) {
        if (modelName == null || version == null) {
            return;
        }
        ConcurrentHashMap<String, OnnxModelDescriptor> versions = modelStorage.get(modelName);
        if (versions != null) {
            versions.remove(version);
            if (version.equals(activeVersions.get(modelName))) {
                activeVersions.remove(modelName);
                // Promote first remaining version if available
                versions.values().stream().findFirst().ifPresent(remaining -> {
                    activeVersions.put(modelName, remaining.version());
                    versions.put(remaining.version(), remaining.withActive(true));
                });
            }
            if (versions.isEmpty()) {
                modelStorage.remove(modelName);
            }
        }
    }

    @Override
    public List<OnnxModelDescriptor> listModels() {
        List<OnnxModelDescriptor> result = new ArrayList<>();
        for (String modelName : modelStorage.keySet()) {
            resolveModel(modelName).ifPresent(result::add);
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<OnnxModelDescriptor> listVersions(String modelName) {
        if (modelName == null) {
            return Collections.emptyList();
        }
        ConcurrentHashMap<String, OnnxModelDescriptor> versions = modelStorage.get(modelName);
        if (versions == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(versions.values()));
    }

    @Override
    public boolean hasModel(String modelName) {
        return resolveModel(modelName).isPresent();
    }

    @Override
    public boolean hasModelVersion(String modelName, String version) {
        return resolveModel(modelName, version).isPresent();
    }

    /**
     * Scans the configured base directory for `.onnx` model files and registers them.
     *
     * @param featureProvider function mapping model names to expected input feature signatures
     * @return count of discovered and registered model files
     * @throws IOException if directory traversal fails
     */
    public int scanModels(Function<String, List<String>> featureProvider) throws IOException {
        if (baseDirectory == null || !Files.exists(baseDirectory)) {
            return 0;
        }

        int count = 0;
        try (Stream<Path> stream = Files.walk(baseDirectory)) {
            List<Path> onnxFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".onnx"))
                    .toList();

            for (Path path : onnxFiles) {
                String fileName = path.getFileName().toString();
                String modelName = fileName.substring(0, fileName.length() - 5);
                List<String> features = featureProvider != null ? featureProvider.apply(modelName) : List.of();
                long size = Files.size(path);

                OnnxModelDescriptor descriptor = OnnxModelDescriptor.builder()
                        .modelName(modelName)
                        .version("1.0.0")
                        .modelPath(path.toAbsolutePath().toString())
                        .inputFeatures(features.isEmpty() ? List.of("feature_0") : features)
                        .outputTensorName("probabilities")
                        .outputIndex(1)
                        .description("Autodiscovered from " + path.getFileName())
                        .fileSizeBytes(size)
                        .active(true)
                        .build();

                registerModel(descriptor);
                count++;
            }
        }
        return count;
    }
}
