package com.helix.core.parser.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OnnxInferenceNode AST Tests")
class OnnxInferenceNodeTest {

    @Test
    @DisplayName("Should create valid OnnxInferenceNode with default output tensor parameters")
    void testCreateDefaultNode() {
        OnnxInferenceNode node = new OnnxInferenceNode("fraud_model_v1");

        assertEquals("fraud_model_v1", node.getModelName());
        assertEquals("probabilities", node.getOutputTensorName());
        assertEquals(1, node.getOutputIndex());
        assertEquals("ML(fraud_model_v1)", node.toString());
    }

    @Test
    @DisplayName("Should create valid OnnxInferenceNode with custom output tensor and index")
    void testCreateCustomNode() {
        OnnxInferenceNode node = new OnnxInferenceNode("ast_reorder_policy", "action_logits", 0);

        assertEquals("ast_reorder_policy", node.getModelName());
        assertEquals("action_logits", node.getOutputTensorName());
        assertEquals(0, node.getOutputIndex());
        assertEquals("ML(ast_reorder_policy, \"action_logits\", 0)", node.toString());
    }

    @Test
    @DisplayName("Should reject null or blank modelName")
    void testRejectNullOrBlankModelName() {
        assertThrows(IllegalArgumentException.class, () -> new OnnxInferenceNode(null));
        assertThrows(IllegalArgumentException.class, () -> new OnnxInferenceNode("   "));
        assertThrows(IllegalArgumentException.class, () -> new OnnxInferenceNode("", "out", 0));
    }

    @Test
    @DisplayName("Should enforce equals and hashCode contract")
    void testEqualsAndHashCode() {
        OnnxInferenceNode node1 = new OnnxInferenceNode("model_a", "prob", 1);
        OnnxInferenceNode node2 = new OnnxInferenceNode("model_a", "prob", 1);
        OnnxInferenceNode node3 = new OnnxInferenceNode("model_b", "prob", 1);

        assertEquals(node1, node2);
        assertEquals(node1.hashCode(), node2.hashCode());
        assertNotEquals(node1, node3);
    }
}
