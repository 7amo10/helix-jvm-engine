package com.helix.core.reorder;

import com.helix.core.ml.LocalModelRegistry;
import com.helix.core.ml.OnnxSessionPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReorderingPolicyFactory Unit Tests")
class ReorderingPolicyFactoryTest {

    @AfterEach
    void tearDown() {
        System.clearProperty(ReorderingPolicyFactory.PROPERTY_KEY);
    }

    @Test
    @DisplayName("Should create RatioSortPolicy when RATIO type is selected")
    void testCreateRatioPolicy() {
        ReorderingPolicy policy = ReorderingPolicyFactory.createPolicy(ReorderingPolicyType.RATIO, null);
        assertNotNull(policy);
        assertInstanceOf(RatioSortPolicy.class, policy);
    }

    @Test
    @DisplayName("Should create NeuralAstReorderingPolicy when NEURAL type is selected")
    void testCreateNeuralPolicy() {
        LocalModelRegistry registry = new LocalModelRegistry();
        try (OnnxSessionPool pool = new OnnxSessionPool(registry, 2)) {
            ReorderingPolicy policy = ReorderingPolicyFactory.createPolicy(ReorderingPolicyType.NEURAL, pool);
            assertNotNull(policy);
            assertInstanceOf(NeuralAstReorderingPolicy.class, policy);
        }
    }

    @Test
    @DisplayName("Should toggle between ratio and neural policy via system property")
    void testToggleViaSystemProperty() {
        LocalModelRegistry registry = new LocalModelRegistry();
        try (OnnxSessionPool pool = new OnnxSessionPool(registry, 2)) {
            // Default should be ratio
            assertInstanceOf(RatioSortPolicy.class, ReorderingPolicyFactory.resolveDefault(pool));

            // Set to neural
            System.setProperty(ReorderingPolicyFactory.PROPERTY_KEY, "neural");
            assertInstanceOf(NeuralAstReorderingPolicy.class, ReorderingPolicyFactory.resolveDefault(pool));

            // Set to ratio
            System.setProperty(ReorderingPolicyFactory.PROPERTY_KEY, "ratio");
            assertInstanceOf(RatioSortPolicy.class, ReorderingPolicyFactory.resolveDefault(pool));
        }
    }
}
