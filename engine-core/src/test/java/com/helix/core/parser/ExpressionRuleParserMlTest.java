package com.helix.core.parser;

import com.helix.core.parser.ast.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExpressionRuleParser ML() Parsing Tests")
class ExpressionRuleParserMlTest {

    private final ExpressionRuleParser parser = new ExpressionRuleParser();

    @Test
    @DisplayName("Should parse basic ML(model_name) with identifier parameter")
    void testParseBasicMlCall() throws Exception {
        ExpressionNode ast = parser.parse("ML(fraud_model_v1)");
        assertInstanceOf(OnnxInferenceNode.class, ast);
        OnnxInferenceNode mlNode = (OnnxInferenceNode) ast;
        assertEquals("fraud_model_v1", mlNode.getModelName());
        assertEquals("probabilities", mlNode.getOutputTensorName());
        assertEquals(1, mlNode.getOutputIndex());
    }

    @Test
    @DisplayName("Should parse ML(\"model_name\") with string literal parameter")
    void testParseMlCallWithStringLiteral() throws Exception {
        ExpressionNode ast = parser.parse("ML(\"fraud_model_v1\")");
        assertInstanceOf(OnnxInferenceNode.class, ast);
        OnnxInferenceNode mlNode = (OnnxInferenceNode) ast;
        assertEquals("fraud_model_v1", mlNode.getModelName());
    }

    @Test
    @DisplayName("Should parse ML(model_name, outputTensor, outputIndex) with custom options")
    void testParseMlCallWithCustomParameters() throws Exception {
        ExpressionNode ast = parser.parse("ML(ast_reorder_policy, \"action_logits\", 0)");
        assertInstanceOf(OnnxInferenceNode.class, ast);
        OnnxInferenceNode mlNode = (OnnxInferenceNode) ast;
        assertEquals("ast_reorder_policy", mlNode.getModelName());
        assertEquals("action_logits", mlNode.getOutputTensorName());
        assertEquals(0, mlNode.getOutputIndex());
    }

    @Test
    @DisplayName("Should parse relational expression containing ML() call")
    void testParseRelationalExpression() throws Exception {
        ExpressionNode ast = parser.parse("ML(fraud_model_v1) > 0.85");
        assertInstanceOf(ComparisonNode.class, ast);
        ComparisonNode cmp = (ComparisonNode) ast;
        assertInstanceOf(OnnxInferenceNode.class, cmp.getLeft());
        assertInstanceOf(LiteralNode.class, cmp.getRight());
        assertEquals(BinaryOpNode.Operator.GREATER_THAN, cmp.getOperator());
    }

    @Test
    @DisplayName("Should parse compound boolean expression with ML() and variables")
    void testParseCompoundBooleanExpression() throws Exception {
        ExpressionNode ast = parser.parse("amount > 1000.0 && ML(fraud_model_v1) >= 0.75");
        assertInstanceOf(BinaryExpressionNode.class, ast);
        BinaryExpressionNode andNode = (BinaryExpressionNode) ast;
        assertEquals(BinaryOpNode.Operator.AND, andNode.getOperator());
        assertInstanceOf(ComparisonNode.class, andNode.getLeft());
        assertInstanceOf(ComparisonNode.class, andNode.getRight());

        ComparisonNode rightCmp = (ComparisonNode) andNode.getRight();
        assertInstanceOf(OnnxInferenceNode.class, rightCmp.getLeft());
        assertEquals("fraud_model_v1", ((OnnxInferenceNode) rightCmp.getLeft()).getModelName());
    }

    @Test
    @DisplayName("Should reject ML() with missing arguments")
    void testRejectEmptyMlCall() {
        assertThrows(ExpressionParseException.class, () -> parser.parse("ML()"));
    }

    @Test
    @DisplayName("Should reject ML() with invalid argument types")
    void testRejectInvalidMlArgument() {
        assertThrows(ExpressionParseException.class, () -> parser.parse("ML(123 + 456)"));
    }

    @Test
    @DisplayName("Should reject ML() with too many arguments")
    void testRejectTooManyArguments() {
        assertThrows(ExpressionParseException.class, () -> parser.parse("ML(model, \"out\", 0, \"extra\")"));
    }
}
