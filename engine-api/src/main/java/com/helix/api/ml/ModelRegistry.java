package com.helix.api.ml;

import java.util.List;
import java.util.Optional;

/**
 * Service Provider Interface (SPI) for resolving, registering, and managing
 * machine-learning models within the Helix rule execution engine.
 *
 * <p>Decouples compilation and execution layers from physical model storage,
 * allowing models to be resolved from local filesystems, shared distributed volumes,
 * or control plane registries dynamically.</p>
 */
public interface ModelRegistry {

    /**
     * Resolves the currently active version of a model by its identifier.
     *
     * @param modelName unique name of the model (e.g., "fraud_model_v1")
     * @return optional containing the active model descriptor, or empty if not registered
     */
    Optional<OnnxModelDescriptor> resolveModel(String modelName);

    /**
     * Resolves a specific version of a model.
     *
     * @param modelName unique name of the model
     * @param version   semantic version string (e.g., "1.0.0")
     * @return optional containing the model descriptor, or empty if not found
     */
    Optional<OnnxModelDescriptor> resolveModel(String modelName, String version);

    /**
     * Registers a new model descriptor or version in the registry.
     *
     * @param descriptor descriptor containing model metadata and storage paths
     */
    void registerModel(OnnxModelDescriptor descriptor);

    /**
     * Marks a specific version of a model as active for rule compilation and evaluation.
     *
     * @param modelName unique name of the model
     * @param version   version string to activate
     */
    void activateModelVersion(String modelName, String version);

    /**
     * Invalidates and deregisters all versions of the named model.
     *
     * @param modelName unique name of the model to invalidate
     */
    void invalidateModel(String modelName);

    /**
     * Invalidates a specific version of a model.
     *
     * @param modelName unique name of the model
     * @param version   version to invalidate
     */
    void invalidateModelVersion(String modelName, String version);

    /**
     * Lists all registered models in their active versions.
     *
     * @return unmodifiable list of active model descriptors
     */
    List<OnnxModelDescriptor> listModels();

    /**
     * Lists all available versions for a specific model.
     *
     * @param modelName unique name of the model
     * @return list of model descriptors across all registered versions
     */
    List<OnnxModelDescriptor> listVersions(String modelName);

    /**
     * Checks if an active version exists for the specified model.
     *
     * @param modelName model name to verify
     * @return true if an active model exists
     */
    boolean hasModel(String modelName);

    /**
     * Checks if a specific version exists for the specified model.
     *
     * @param modelName model name to verify
     * @param version   version string to verify
     * @return true if the specified model version exists
     */
    boolean hasModelVersion(String modelName, String version);
}
