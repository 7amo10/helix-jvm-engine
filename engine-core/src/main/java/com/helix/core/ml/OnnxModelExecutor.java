package com.helix.core.ml;

import com.helix.api.ExecutionContext;
import com.helix.api.ml.OnnxInferenceResult;

import java.util.Objects;

/**
 * Static runtime bridge for executing ONNX model inference from compiled JVM bytecode and interpreters.
 *
 * <p>Provides high-performance static dispatch methods invoked directly by compiled rule classes
 * via {@code INVOKESTATIC}, delegating to the globally active {@link OnnxSessionPool}.</p>
 */
public final class OnnxModelExecutor {

    private static volatile OnnxSessionPool activePool;

    private OnnxModelExecutor() {
        // Static utility
    }

    /**
     * Registers the active session pool for runtime evaluation.
     *
     * @param pool session pool instance
     */
    public static void setSessionPool(OnnxSessionPool pool) {
        activePool = pool;
    }

    /**
     * Retrieves the active session pool.
     *
     * @return current active session pool, or null if uninitialized
     */
    public static OnnxSessionPool getSessionPool() {
        return activePool;
    }

    /**
     * Resets the active session pool reference.
     */
    public static void reset() {
        activePool = null;
    }

    /**
     * Evaluates the named model using its default output tensor and class index.
     *
     * @param modelName model identifier
     * @param context   runtime execution context
     * @return probability or score value as a double
     */
    public static double evaluate(String modelName, ExecutionContext context) {
        OnnxSessionPool pool = requirePool();
        OnnxInferenceResult result = pool.executeInference(modelName, context);
        return result.probabilityScore();
    }

    /**
     * Evaluates the named model with custom output tensor and index mapping.
     *
     * @param modelName        model identifier
     * @param outputTensorName target output tensor name
     * @param outputIndex      target class or output index
     * @param context          runtime execution context
     * @return extracted score value as a double
     */
    public static double evaluate(String modelName, String outputTensorName, int outputIndex, ExecutionContext context) {
        OnnxSessionPool pool = requirePool();
        OnnxInferenceResult result = pool.executeInference(modelName, outputTensorName, outputIndex, context);
        return result.probabilityScore();
    }

    /**
     * Evaluates the named model and returns the full telemetry {@link OnnxInferenceResult}.
     *
     * @param modelName model identifier
     * @param context   runtime execution context
     * @return detailed inference result
     */
    public static OnnxInferenceResult evaluateWithResult(String modelName, ExecutionContext context) {
        OnnxSessionPool pool = requirePool();
        return pool.executeInference(modelName, context);
    }

    private static OnnxSessionPool requirePool() {
        OnnxSessionPool pool = activePool;
        if (pool == null) {
            throw new IllegalStateException("OnnxModelExecutor is not initialized with an active OnnxSessionPool");
        }
        return pool;
    }
}
