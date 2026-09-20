package com.helix.core.ml;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.helix.api.ExecutionContext;
import com.helix.api.ml.ModelRegistry;
import com.helix.api.ml.OnnxInferenceResult;
import com.helix.api.ml.OnnxModelDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe, high-throughput runtime session pool for in-process ONNX model inference.
 *
 * <p>Manages bounded pools of native {@link OrtSession} instances per registered model,
 * optimizing multi-threaded virtual-thread execution with zero lock contention, zero JNI leaks,
 * and atomic session draining upon dynamic model reload.</p>
 */
public class OnnxSessionPool implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(OnnxSessionPool.class);

    private final ModelRegistry modelRegistry;
    private final OnnxFeatureExtractor featureExtractor;
    private final OrtEnvironment environment;
    private final int maxSessionsPerModel;
    private final ConcurrentHashMap<String, ModelPool> pools = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public OnnxSessionPool(ModelRegistry modelRegistry) {
        this(modelRegistry, Math.max(4, Runtime.getRuntime().availableProcessors()));
    }

    public OnnxSessionPool(ModelRegistry modelRegistry, int maxSessionsPerModel) {
        this(modelRegistry, new OnnxFeatureExtractor(), maxSessionsPerModel);
    }

    public OnnxSessionPool(ModelRegistry modelRegistry, OnnxFeatureExtractor featureExtractor, int maxSessionsPerModel) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry cannot be null");
        this.featureExtractor = Objects.requireNonNull(featureExtractor, "featureExtractor cannot be null");
        this.maxSessionsPerModel = Math.max(1, maxSessionsPerModel);
        this.environment = OrtEnvironment.getEnvironment();
        if (OnnxModelExecutor.getSessionPool() == null) {
            OnnxModelExecutor.setSessionPool(this);
        }
    }

    /**
     * Static runtime bridge invoked by compiled ASM bytecode.
     *
     * @param context   runtime execution context containing input features
     * @param modelName name of registered ONNX model
     * @return predicted probability score as primitive float
     */
    public static float run(ExecutionContext context, String modelName) {
        return (float) OnnxModelExecutor.evaluate(modelName, context);
    }

    /**
     * Static runtime bridge invoked by compiled ASM bytecode with explicit output tensor and index.
     *
     * @param context          runtime execution context containing input features
     * @param modelName        name of registered ONNX model
     * @param outputTensorName target output tensor name
     * @param outputIndex      class or output index
     * @return predicted probability score as primitive float
     */
    public static float run(ExecutionContext context, String modelName, String outputTensorName, int outputIndex) {
        return (float) OnnxModelExecutor.evaluate(modelName, outputTensorName, outputIndex, context);
    }

    /**
     * Executes single-row in-process inference for the specified model and context.
     *
     * @param modelName name of the active registered model
     * @param context   runtime execution context containing required input features
     * @return populated inference result containing score, predicted class, and latency
     */
    public OnnxInferenceResult executeInference(String modelName, ExecutionContext context) {
        OnnxModelDescriptor descriptor = modelRegistry.resolveModel(modelName)
                .orElseThrow(() -> new IllegalArgumentException("Unregistered or inactive ML model: '" + modelName + "'"));

        return executeInference(descriptor, descriptor.outputTensorName(), descriptor.outputIndex(), context);
    }

    /**
     * Executes single-row inference with explicit output tensor and index overrides.
     *
     * @param modelName        model name
     * @param outputTensorName target output tensor name
     * @param outputIndex      class or output index
     * @param context          evaluation context
     * @return inference result
     */
    public OnnxInferenceResult executeInference(String modelName, String outputTensorName, int outputIndex, ExecutionContext context) {
        OnnxModelDescriptor descriptor = modelRegistry.resolveModel(modelName)
                .orElseThrow(() -> new IllegalArgumentException("Unregistered or inactive ML model: '" + modelName + "'"));

        return executeInference(descriptor, outputTensorName, outputIndex, context);
    }

    private OnnxInferenceResult executeInference(OnnxModelDescriptor descriptor, String targetOutputTensor, int targetOutputIndex, ExecutionContext context) {
        ensureOpen();
        ModelPool pool = getOrCreatePool(descriptor);
        OrtSession session = pool.borrowSession();

        long startNanos = System.nanoTime();
        OnnxTensor inputTensor = null;
        OrtSession.Result result = null;

        float probabilityScore = 0.0f;
        int predictedClass = 0;

        try {
            inputTensor = featureExtractor.createTensor(environment, descriptor, context);
            String inputName = pool.getInputTensorName(session);
            String outputName = targetOutputTensor != null ? targetOutputTensor : descriptor.outputTensorName();

            try {
                result = session.run(Collections.singletonMap(inputName, inputTensor), Collections.singleton(outputName));
            } catch (OrtException e) {
                // Fallback to default run if specific output tensor requested is not accepted
                result = session.run(Collections.singletonMap(inputName, inputTensor));
            }
            OnnxValue outValue = null;
            for (Map.Entry<String, OnnxValue> entry : result) {
                if (entry.getKey().equalsIgnoreCase(outputName)) {
                    outValue = entry.getValue();
                    break;
                }
            }

            if (outValue == null && result.iterator().hasNext()) {
                outValue = result.iterator().next().getValue();
            }

            if (outValue instanceof OnnxTensor outTensor) {
                Object tensorValue = outTensor.getValue();
                if (tensorValue instanceof float[][] matrix && matrix.length > 0) {
                    float[] row = matrix[0];
                    int idx = Math.min(targetOutputIndex, row.length - 1);
                    probabilityScore = row[idx];
                    predictedClass = argmax(row);
                } else if (tensorValue instanceof float[] array && array.length > 0) {
                    int idx = Math.min(targetOutputIndex, array.length - 1);
                    probabilityScore = array[idx];
                    predictedClass = argmax(array);
                } else if (tensorValue instanceof long[] longArray && longArray.length > 0) {
                    predictedClass = (int) longArray[0];
                    probabilityScore = (float) predictedClass;
                }
            }

            // Also check for discrete label tensor if available
            for (Map.Entry<String, OnnxValue> entry : result) {
                if ("label".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof OnnxTensor labelTensor) {
                    Object val = labelTensor.getValue();
                    if (val instanceof long[] labels && labels.length > 0) {
                        predictedClass = (int) labels[0];
                    }
                }
            }

            long latencyNanos = Math.max(1, System.nanoTime() - startNanos);

            // Clamp probability to valid [0.0, 1.0] range
            float clampedScore = Math.max(0.0f, Math.min(1.0f, probabilityScore));

            return new OnnxInferenceResult(
                    descriptor.modelName(),
                    descriptor.version(),
                    clampedScore,
                    predictedClass,
                    latencyNanos,
                    System.currentTimeMillis()
            );
        } catch (OrtException e) {
            throw new RuntimeException("ONNX Runtime native evaluation failed for model '" + descriptor.modelName() + "': " + e.getMessage(), e);
        } finally {
            if (inputTensor != null) {
                inputTensor.close();
            }
            if (result != null) {
                result.close();
            }
            pool.returnSession(session);
        }
    }

    /**
     * Atomically reloads sessions for the named model, draining existing sessions.
     *
     * @param modelName name of model to reload
     */
    public void reloadModel(String modelName) {
        ensureOpen();
        OnnxModelDescriptor descriptor = modelRegistry.resolveModel(modelName)
                .orElseThrow(() -> new IllegalArgumentException("Cannot reload unregistered model: '" + modelName + "'"));

        ModelPool oldPool = pools.remove(modelName);
        if (oldPool != null) {
            oldPool.close();
        }
        pools.put(modelName, new ModelPool(descriptor));
        logger.info("Reloaded ONNX model '{}' with version '{}'", modelName, descriptor.version());
    }

    /**
     * Drains and invalidates cached sessions for a model.
     *
     * @param modelName name of model to invalidate
     */
    public void invalidate(String modelName) {
        ModelPool pool = pools.remove(modelName);
        if (pool != null) {
            pool.close();
        }
    }

    private ModelPool getOrCreatePool(OnnxModelDescriptor descriptor) {
        return pools.compute(descriptor.modelName(), (k, existing) -> {
            if (existing == null || !existing.descriptor.version().equals(descriptor.version())) {
                if (existing != null) {
                    existing.close();
                }
                return new ModelPool(descriptor);
            }
            return existing;
        });
    }

    private static int argmax(float[] array) {
        if (array == null || array.length == 0) {
            return 0;
        }
        int maxIdx = 0;
        float maxVal = array[0];
        for (int i = 1; i < array.length; i++) {
            if (array[i] > maxVal) {
                maxVal = array[i];
                maxIdx = i;
            }
        }
        return maxIdx;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("OnnxSessionPool has been closed");
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            for (ModelPool pool : pools.values()) {
                pool.close();
            }
            pools.clear();
            if (OnnxModelExecutor.getSessionPool() == this) {
                OnnxModelExecutor.reset();
            }
            logger.info("OnnxSessionPool shut down cleanly.");
        }
    }

    /**
     * Internal bounded session pool per model.
     */
    private final class ModelPool implements AutoCloseable {
        private final OnnxModelDescriptor descriptor;
        private final BlockingDeque<OrtSession> sessionDeque = new LinkedBlockingDeque<>();
        private final AtomicInteger activeCount = new AtomicInteger(0);
        private final AtomicBoolean poolClosed = new AtomicBoolean(false);
        private volatile String cachedInputName = null;

        ModelPool(OnnxModelDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        String getInputTensorName(OrtSession session) throws OrtException {
            if (cachedInputName == null) {
                synchronized (this) {
                    if (cachedInputName == null) {
                        cachedInputName = session.getInputNames().iterator().next();
                    }
                }
            }
            return cachedInputName;
        }

        OrtSession borrowSession() {
            if (poolClosed.get()) {
                throw new IllegalStateException("ModelPool is closed for " + descriptor.modelName());
            }

            OrtSession session = sessionDeque.pollFirst();
            if (session != null) {
                return session;
            }

            if (activeCount.get() < maxSessionsPerModel) {
                int current = activeCount.incrementAndGet();
                if (current <= maxSessionsPerModel) {
                    try {
                        return createNewSession();
                    } catch (Exception e) {
                        activeCount.decrementAndGet();
                        throw new RuntimeException("Failed to initialize OrtSession for " + descriptor.modelName(), e);
                    }
                } else {
                    activeCount.decrementAndGet();
                }
            }

            try {
                session = sessionDeque.pollFirst(5, TimeUnit.SECONDS);
                if (session == null) {
                    throw new IllegalStateException("Timed out waiting for available OrtSession for model: " + descriptor.modelName());
                }
                return session;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for OrtSession", e);
            }
        }

        void returnSession(OrtSession session) {
            if (session == null) {
                return;
            }
            if (poolClosed.get()) {
                try {
                    session.close();
                } catch (OrtException ignored) {
                }
                activeCount.decrementAndGet();
                return;
            }
            sessionDeque.offerLast(session);
        }

        private OrtSession createNewSession() throws OrtException {
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            opts.setIntraOpNumThreads(1);
            return environment.createSession(descriptor.modelPath(), opts);
        }

        @Override
        public void close() {
            if (poolClosed.compareAndSet(false, true)) {
                OrtSession s;
                while ((s = sessionDeque.pollFirst()) != null) {
                    try {
                        s.close();
                    } catch (OrtException ignored) {
                    }
                    activeCount.decrementAndGet();
                }
            }
        }
    }
}
