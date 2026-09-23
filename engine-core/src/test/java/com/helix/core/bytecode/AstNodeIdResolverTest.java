package com.helix.core.bytecode;

import com.helix.core.parser.ExpressionRuleParser;
import com.helix.core.parser.ast.ExpressionNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AstNodeIdResolver Tests")
class AstNodeIdResolverTest {

    private final ExpressionRuleParser parser = new ExpressionRuleParser();

    @Test
    @DisplayName("Should resolve consistent ID for ML comparison nodes")
    void testResolveMlComparisonNodeId() throws Exception {
        ExpressionNode node = parser.parse("ML('fraud_model_v1') > 0.85");
        String id = AstNodeIdResolver.resolveNodeId("FraudRule", node);

        assertNotNull(id);
        assertTrue(id.contains("fraud_model_v1"));
        assertTrue(id.contains("greater_than") || id.contains("gt"));
    }

    @Test
    @DisplayName("Should resolve consistent ID for variable comparison nodes")
    void testResolveVariableComparisonNodeId() throws Exception {
        ExpressionNode node = parser.parse("amount > 1000");
        String id = AstNodeIdResolver.resolveNodeId("FraudRule", node);

        assertNotNull(id);
        assertEquals("clause_amount_greater_than", id);
    }

    @Test
    @DisplayName("Should resolve consistent ID for boolean variable nodes")
    void testResolveBooleanVariableNodeId() throws Exception {
        ExpressionNode node = parser.parse("is_active");
        String id = AstNodeIdResolver.resolveNodeId("ActiveRule", node);

        assertNotNull(id);
        assertEquals("clause_var_is_active", id);
    }

    @Test
    @DisplayName("Should sanitize invalid identifier characters in node IDs")
    void testSanitizeNodeIdNames() {
        assertEquals("model_v1_0", AstNodeIdResolver.sanitizeName("model-v1.0"));
        assertEquals("var_x", AstNodeIdResolver.sanitizeName("var:x"));
    }
}
