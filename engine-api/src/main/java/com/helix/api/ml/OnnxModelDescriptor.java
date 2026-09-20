package com.helix.api.ml;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Metadata descriptor defining an ONNX machine-learning model artifact.
 *
 * <p>Encapsulates model identification, versioning, storage paths, input feature signatures,
 * and output tensor mapping required for in-process JVM inference execution.</p>
 *
 * @param modelName         Unique model identifier (e.g., "fraud_model_v1")
 * @param version           Semantic version string (e.g., "1.0.0")
 * @param modelPath         Filesystem or volume storage path to the .onnx binary file
 * @param inputFeatures     Immutable ordered list of feature channel names expected by the model input tensor
 * @param outputTensorName  Output tensor identifier (e.g., "probabilities" or "action_logits")
 * @param outputIndex       Target output index to extract from the output tensor (e.g., 1 for fraud class probability)
 * @param description       Human-readable description or purpose of the model
 * @param fileSizeBytes     Size of the serialized .onnx artifact in bytes
 * @param active            Flag indicating if this version is currently active for rule evaluation
 */
public record OnnxModelDescriptor(
        String modelName,
        String version,
        String modelPath,
        List<String> inputFeatures,
        String outputTensorName,
        int outputIndex,
        String description,
        long fileSizeBytes,
        boolean active
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Canonical constructor enforcing strict validation invariants.
     */
    public OnnxModelDescriptor {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName cannot be null or blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version cannot be null or blank");
        }
        if (modelPath == null || modelPath.isBlank()) {
            throw new IllegalArgumentException("modelPath cannot be null or blank");
        }
        if (inputFeatures == null || inputFeatures.isEmpty()) {
            throw new IllegalArgumentException("inputFeatures cannot be null or empty");
        }
        if (outputTensorName == null || outputTensorName.isBlank()) {
            throw new IllegalArgumentException("outputTensorName cannot be null or blank");
        }
        if (outputIndex < 0) {
            throw new IllegalArgumentException("outputIndex cannot be negative: " + outputIndex);
        }

        // Defensive unmodifiable copy to ensure immutability
        inputFeatures = Collections.unmodifiableList(new ArrayList<>(inputFeatures));
    }

    /**
     * Returns the total count of input features expected by this model.
     *
     * @return feature count
     */
    public int featureCount() {
        return inputFeatures.size();
    }

    /**
     * Checks if a named feature is required by this model's input schema.
     *
     * @param featureName feature name to verify
     * @return true if the feature exists in the input schema
     */
    public boolean hasFeature(String featureName) {
        return inputFeatures.contains(featureName);
    }

    /**
     * Returns a copy of this descriptor with an updated active flag.
     *
     * @param newActive new active status
     * @return updated descriptor instance
     */
    public OnnxModelDescriptor withActive(boolean newActive) {
        return new OnnxModelDescriptor(
                modelName, version, modelPath, inputFeatures, outputTensorName,
                outputIndex, description, fileSizeBytes, newActive
        );
    }

    /**
     * Creates a new builder instance.
     *
     * @return descriptor builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link OnnxModelDescriptor}.
     */
    public static final class Builder {
        private String modelName;
        private String version;
        private String modelPath;
        private List<String> inputFeatures;
        private String outputTensorName = "probabilities";
        private int outputIndex = 1;
        private String description = "";
        private long fileSizeBytes = 0L;
        private boolean active = true;

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder modelPath(String modelPath) {
            this.modelPath = modelPath;
            return this;
        }

        public Builder inputFeatures(List<String> inputFeatures) {
            this.inputFeatures = inputFeatures;
            return this;
        }

        public Builder outputTensorName(String outputTensorName) {
            this.outputTensorName = outputTensorName;
            return this;
        }

        public Builder outputIndex(int outputIndex) {
            this.outputIndex = outputIndex;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder fileSizeBytes(long fileSizeBytes) {
            this.fileSizeBytes = fileSizeBytes;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public OnnxModelDescriptor build() {
            return new OnnxModelDescriptor(
                    modelName, version, modelPath, inputFeatures, outputTensorName,
                    outputIndex, description, fileSizeBytes, active
            );
        }
    }
}
