package com.helix.core.reorder;

import com.helix.core.ml.OnnxSessionPool;

/**
 * Factory for instantiating and configuring {@link ReorderingPolicy} instances.
 */
public final class ReorderingPolicyFactory {

    public static final String PROPERTY_KEY = "helix.reorder.policy";

    private ReorderingPolicyFactory() {}

    /**
     * Creates a reordering policy for the specified type.
     *
     * @param type        policy type (RATIO or NEURAL)
     * @param sessionPool ONNX session pool for neural policies
     * @return initialized policy instance
     */
    public static ReorderingPolicy createPolicy(ReorderingPolicyType type, OnnxSessionPool sessionPool) {
        if (type == ReorderingPolicyType.NEURAL) {
            return new NeuralAstReorderingPolicy(sessionPool);
        }
        return new RatioSortPolicy();
    }

    /**
     * Resolves the default reordering policy based on the 'helix.reorder.policy' configuration property.
     *
     * @param sessionPool ONNX session pool
     * @return configured policy instance (defaults to RatioSortPolicy)
     */
    public static ReorderingPolicy resolveDefault(OnnxSessionPool sessionPool) {
        String prop = System.getProperty(PROPERTY_KEY, "ratio").trim().toLowerCase();
        if ("neural".equals(prop)) {
            return new NeuralAstReorderingPolicy(sessionPool);
        }
        return new RatioSortPolicy();
    }
}
