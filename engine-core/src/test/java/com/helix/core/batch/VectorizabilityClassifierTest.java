package com.helix.core.batch;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.core.parser.ast.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VectorizabilityClassifier Unit Tests")
class VectorizabilityClassifierTest {

    private VectorizabilityClassifier classifier;
    private AstBuilder astBuilder;

    @BeforeEach
    void setUp() {
        classifier = new VectorizabilityClassifier();
        astBuilder = new AstBuilder();
    }

    @Test
    @DisplayName("Pure numeric comparison is classified as vectorizable")
    void testPureNumericComparisons() throws Exception {
        assertTrue(classifier.classify(astBuilder.buildAst("amount > 1000")));
        assertTrue(classifier.classify(astBuilder.buildAst("riskScore < 0.85")));
        assertTrue(classifier.classify(astBuilder.buildAst("score >= 50.0")));
        assertTrue(classifier.classify(astBuilder.buildAst("latency <= 200")));
        assertTrue(classifier.classify(astBuilder.buildAst("count == 0")));
        assertTrue(classifier.classify(astBuilder.buildAst("balance != 0.0")));
    }

    @Test
    @DisplayName("Logical AND/OR chains of numeric comparisons are vectorizable")
    void testLogicalChainsOfNumericComparisons() throws Exception {
        assertTrue(classifier.classify(astBuilder.buildAst("amount > 1000 && riskScore < 0.85")));
        assertTrue(classifier.classify(astBuilder.buildAst("amount > 500 || (velocity > 10.0 && riskScore < 0.3)")));
        assertTrue(classifier.classify(astBuilder.buildAst("amount > 100 && riskScore < 0.5 && velocity <= 20.0")));
        assertTrue(classifier.classify(astBuilder.buildAst("!(amount <= 100) && riskScore > 0.5")));
    }

    @Test
    @DisplayName("Arithmetic expressions over double fields in comparisons are vectorizable")
    void testArithmeticSubexpressionsInComparisons() throws Exception {
        assertTrue(classifier.classify(astBuilder.buildAst("(amount * 1.05) > 1000")));
        assertTrue(classifier.classify(astBuilder.buildAst("(amount + fee) > 500 && (balance - amount) >= 0")));
        assertTrue(classifier.classify(astBuilder.buildAst("((amount * 1.2) / factor) <= 100")));
        assertTrue(classifier.classify(astBuilder.buildAst("-riskScore < -0.5")));
    }

    @Test
    @DisplayName("Rules with string operands or string literals are not vectorizable")
    void testStringOperandsNotVectorizable() throws Exception {
        assertFalse(classifier.classify(astBuilder.buildAst("country == \"US\"")));
        assertFalse(classifier.classify(astBuilder.buildAst("status != \"ACTIVE\"")));
        assertFalse(classifier.classify(astBuilder.buildAst("amount > 1000 && status == \"ACTIVE\"")));
        assertFalse(classifier.classify(astBuilder.buildAst("status == \"FRAUD\" || riskScore > 0.9")));
    }

    @Test
    @DisplayName("Rules containing ML() OnnxInferenceNode are not vectorizable")
    void testMlNodesNotVectorizable() {
        OnnxInferenceNode mlNode = new OnnxInferenceNode("fraud_model_v1");
        BinaryOpNode comp = new BinaryOpNode(BinaryOpNode.Operator.GREATER_THAN, mlNode, new LiteralNode(0.85));
        assertFalse(classifier.classify(comp));

        BinaryOpNode compound = new BinaryOpNode(
                BinaryOpNode.Operator.AND,
                new BinaryOpNode(BinaryOpNode.Operator.GREATER_THAN, new VariableNode("amount"), new LiteralNode(1000.0)),
                comp
        );
        assertFalse(classifier.classify(compound));
    }

    @Test
    @DisplayName("Rules with method calls or function calls are not vectorizable")
    void testMethodAndFunctionCallsNotVectorizable() {
        MethodCallNode methodCall = new MethodCallNode(new VariableNode("user"), "isFraudulent", Collections.emptyList());
        assertFalse(classifier.classify(methodCall));

        FunctionCallNode funcCall = new FunctionCallNode("abs", List.of(new VariableNode("amount")));
        BinaryOpNode comp = new BinaryOpNode(BinaryOpNode.Operator.GREATER_THAN, funcCall, new LiteralNode(1000.0));
        assertFalse(classifier.classify(comp));
    }

    @Test
    @DisplayName("Schema-aware classification rejects variables with non-numeric declared types")
    void testSchemaAwareClassification() throws Exception {
        ExpressionNode ast = astBuilder.buildAst("amount > 1000");

        Map<String, Class<?>> numericSchema = Map.of("amount", Double.class);
        assertTrue(classifier.classify(ast, numericSchema));

        Map<String, Class<?>> primitiveSchema = Map.of("amount", double.class);
        assertTrue(classifier.classify(ast, primitiveSchema));

        Map<String, Class<?>> stringSchema = Map.of("amount", String.class);
        assertFalse(classifier.classify(ast, stringSchema));
    }

    @Test
    @DisplayName("RuleNode and Rule interface objects are classified accurately")
    void testRuleNodeClassification() throws Exception {
        ExpressionNode astValid = astBuilder.buildAst("amount > 1000 && riskScore < 0.5");
        RuleNode validRule = new RuleNode("HighRiskRule", "amount > 1000 && riskScore < 0.5",
                Map.of("amount", Double.class, "riskScore", Double.class), astValid);
        assertTrue(classifier.classify(validRule));

        ExpressionNode astInvalid = astBuilder.buildAst("status == \"FRAUD\" && amount > 1000");
        RuleNode invalidRule = new RuleNode("StatusRule", "status == \"FRAUD\" && amount > 1000",
                Map.of("status", String.class, "amount", Double.class), astInvalid);
        assertFalse(classifier.classify(invalidRule));
    }

    @Test
    @DisplayName("Null or empty AST nodes safely return false without throwing")
    void testNullSafeHandling() {
        assertFalse(classifier.classify((ExpressionNode) null));
        assertFalse(classifier.classify((RuleNode) null));
        assertFalse(classifier.classify((Rule) null));
        assertFalse(classifier.classify((CompiledRule) null));
        assertFalse(classifier.classify(""));
        assertFalse(classifier.classify("   "));
        assertFalse(classifier.classify((String) null));
    }

    @Test
    @DisplayName("@VectorizableRule annotation enables O(1) lookup on compiled rules")
    void testVectorizableRuleAnnotationLookup() {
        @VectorizableRule(true)
        class VectorizableMockRule implements CompiledRule {
            @Override public String getName() { return "VecRule"; }
            @Override public String getVersion() { return "1.0.0"; }
            @Override public ExecutionResult execute(ExecutionContext context) { return null; }
        }

        @VectorizableRule(false)
        class NonVectorizableMockRule implements CompiledRule {
            @Override public String getName() { return "NonVecRule"; }
            @Override public String getVersion() { return "1.0.0"; }
            @Override public ExecutionResult execute(ExecutionContext context) { return null; }
        }

        class UnannotatedMockRule implements CompiledRule {
            @Override public String getName() { return "UnannotatedRule"; }
            @Override public String getVersion() { return "1.0.0"; }
            @Override public ExecutionResult execute(ExecutionContext context) { return null; }
        }

        assertTrue(classifier.classify(new VectorizableMockRule()));
        assertFalse(classifier.classify(new NonVectorizableMockRule()));

        UnannotatedMockRule unannotated = new UnannotatedMockRule();
        assertFalse(classifier.classify(unannotated));

        // Pre-register and verify cache hit
        classifier.register(unannotated.getName(), true);
        assertTrue(classifier.classify(unannotated));
    }

    @Test
    @DisplayName("Extract referenced field names for vector columnar layout")
    void testExtractReferencedFields() throws Exception {
        ExpressionNode ast = astBuilder.buildAst("amount > 1000 && riskScore < 0.85 && (velocity + fee) > 10.0");
        List<String> fields = classifier.extractReferencedFields(ast);

        assertEquals(4, fields.size());
        assertTrue(fields.contains("amount"));
        assertTrue(fields.contains("riskScore"));
        assertTrue(fields.contains("velocity"));
        assertTrue(fields.contains("fee"));
    }
}
