package com.helix.core.ml;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import com.helix.api.ExecutionContext;
import com.helix.api.ml.OnnxModelDescriptor;

import java.util.List;
import java.util.Objects;

/**
 * Extracts and transforms runtime execution context features into native ONNX tensor buffers.
 *
 * <p>Validates schema alignment against {@link OnnxModelDescriptor#inputFeatures()}, safely coerces
 * mixed numeric and boolean types into single-precision float representations, and constructs
 * zero-copy 2D arrays ready for {@link OnnxTensor} allocation.</p>
 */
public class OnnxFeatureExtractor {

    /**
     * Extracts an ordered feature vector for a single execution context.
     *
     * @param descriptor target ONNX model descriptor
     * @param context    runtime evaluation context
     * @return 1D float array of model input features
     * @throws IllegalArgumentException if a required feature is missing or cannot be coerced to float
     */
    public float[] extractFeatures(OnnxModelDescriptor descriptor, ExecutionContext context) {
        Objects.requireNonNull(descriptor, "descriptor cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        List<String> featureNames = descriptor.inputFeatures();
        int count = featureNames.size();
        float[] vector = new float[count];

        for (int i = 0; i < count; i++) {
            String name = featureNames.get(i);
            Object value = context.getVariable(name).orElse(null);
            if (value == null) {
                throw new IllegalArgumentException(
                        "Missing required feature '" + name + "' for ML model '" + descriptor.modelName() + "'"
                );
            }
            vector[i] = coerceToFloat(name, value);
        }

        return vector;
    }

    /**
     * Extracts an ordered 2D batch feature matrix for multiple execution contexts.
     *
     * @param descriptor target ONNX model descriptor
     * @param contexts   list of execution contexts
     * @return 2D float array with shape [batch_size, feature_count]
     */
    public float[][] extractBatchFeatures(OnnxModelDescriptor descriptor, List<ExecutionContext> contexts) {
        Objects.requireNonNull(descriptor, "descriptor cannot be null");
        Objects.requireNonNull(contexts, "contexts cannot be null");

        int batchSize = contexts.size();
        int featureCount = descriptor.featureCount();
        float[][] matrix = new float[batchSize][featureCount];

        for (int b = 0; b < batchSize; b++) {
            matrix[b] = extractFeatures(descriptor, contexts.get(b));
        }

        return matrix;
    }

    /**
     * Constructs a native {@link OnnxTensor} for a single-row inference request.
     *
     * @param env        active ONNX Runtime environment
     * @param descriptor target ONNX model descriptor
     * @param context    single evaluation context
     * @return allocated native ONNX tensor buffer with shape [1, feature_count]
     * @throws OrtException if native tensor allocation fails
     */
    public OnnxTensor createTensor(OrtEnvironment env, OnnxModelDescriptor descriptor, ExecutionContext context)
            throws OrtException {
        float[] row = extractFeatures(descriptor, context);
        float[][] matrix = new float[1][row.length];
        matrix[0] = row;
        return OnnxTensor.createTensor(env, matrix);
    }

    /**
     * Constructs a native {@link OnnxTensor} for a batch inference request.
     *
     * @param env        active ONNX Runtime environment
     * @param descriptor target ONNX model descriptor
     * @param contexts   batch of evaluation contexts
     * @return allocated native ONNX tensor buffer with shape [batch_size, feature_count]
     * @throws OrtException if native tensor allocation fails
     */
    public OnnxTensor createTensor(OrtEnvironment env, OnnxModelDescriptor descriptor, List<ExecutionContext> contexts)
            throws OrtException {
        float[][] matrix = extractBatchFeatures(descriptor, contexts);
        return OnnxTensor.createTensor(env, matrix);
    }

    private float coerceToFloat(String featureName, Object value) {
        if (value instanceof Number num) {
            return num.floatValue();
        } else if (value instanceof Boolean bool) {
            return bool ? 1.0f : 0.0f;
        } else if (value instanceof String str) {
            try {
                return Float.parseFloat(str);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "Cannot parse string value '" + str + "' for feature '" + featureName + "' to float", e
                );
            }
        }
        throw new IllegalArgumentException(
                "Unsupported feature type for '" + featureName + "': " + value.getClass().getName()
        );
    }
}
