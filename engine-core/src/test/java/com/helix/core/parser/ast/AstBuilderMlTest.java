package com.helix.core.parser.ast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AstBuilder ML() Parsing Tests")
class AstBuilderMlTest {

    private final AstBuilder astBuilder = new AstBuilder();

    @Test
    @DisplayName("Should build OnnxInferenceNode from ML() expression via AstBuilder")
    void testAstBuilderWithMlCall() throws Exception {
        ExpressionNode ast = astBuilder.buildAst("ML(fraud_model_v1) > 0.80");
        assertInstanceOf(BinaryOpNode.class, ast);
        BinaryOpNode cmp = (BinaryOpNode) ast;
        assertInstanceOf(OnnxInferenceNode.class, cmp.getLeft());
        OnnxInferenceNode ml = (OnnxInferenceNode) cmp.getLeft();
        assertEquals("fraud_model_v1", ml.getModelName());
    }

    @Test
    @DisplayName("Should build compound AST with short-circuit AND and ML() clause")
    void testAstBuilderCompound() throws Exception {
        ExpressionNode ast = astBuilder.buildAst("velocity_1h > 5 && ML(fraud_model_v1) >= 0.70");
        assertInstanceOf(BinaryOpNode.class, ast);
        BinaryOpNode andNode = (BinaryOpNode) ast;
        assertEquals(BinaryOpNode.Operator.AND, andNode.getOperator());
        assertInstanceOf(BinaryOpNode.class, andNode.getRight());
        BinaryOpNode rightCmp = (BinaryOpNode) andNode.getRight();
        assertInstanceOf(OnnxInferenceNode.class, rightCmp.getLeft());
    }
}
