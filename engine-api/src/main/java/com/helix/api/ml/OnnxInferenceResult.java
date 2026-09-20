package com.helix.api.ml;

import java.io.Serializable;

/**
 * Encapsulates the output and performance telemetry from an in-process ONNX model evaluation.
 *
 * @param modelName          Name of the evaluated model
 * @param version            Version of the evaluated model
 * @param probabilityScore   Normalized confidence or posterior probability in range [0.0, 1.0]
 * @param predictedClass     Discrete classification output (e.g., 0 for legitimate, 1 for fraud)
 * @param latencyNanos       Inference latency measured in nanoseconds
 * @param evaluatedTimestamp Epoch milliseconds timestamp of inference execution
 */
public record OnnxInferenceResult(
        String modelName,
        String version,
        float probabilityScore,
        int predictedClass,
        long latencyNanos,
        long evaluatedTimestamp
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Canonical constructor enforcing probability bounds and non-negative latency.
     */
    public OnnxInferenceResult {
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalArgumentException("modelName cannot be null or blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version cannot be null or blank");
        }
        if (probabilityScore < 0.0f || probabilityScore > 1.0f) {
            throw new IllegalArgumentException("probabilityScore must be within [0.0, 1.0], got: " + probabilityScore);
        }
        if (latencyNanos < 0) {
            throw new IllegalArgumentException("latencyNanos cannot be negative: " + latencyNanos);
        }
    }

    /**
     * Evaluates if the probability score satisfies or exceeds a defined decision threshold.
     *
     * @param threshold classification threshold in range [0.0, 1.0]
     * @return true if probability >= threshold
     */
    public boolean isPositive(float threshold) {
        return probabilityScore >= threshold;
    }

    /**
     * Returns the probability score formatted as a percentage [0.0% to 100.0%].
     *
     * @return percentage probability
     */
    public float probabilityPercent() {
        return probabilityScore * 100.0f;
    }

    /**
     * Returns the inference duration in microseconds.
     *
     * @return latency in microseconds
     */
    public double latencyMicros() {
        return latencyNanos / 1000.0;
    }
}
