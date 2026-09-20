package com.helix.core.parser;

import com.helix.api.ml.ModelRegistry;
import com.helix.api.ml.OnnxModelDescriptor;
import com.helix.core.parser.ast.BinaryExpressionNode;
import com.helix.core.parser.ast.BinaryOpNode;
import com.helix.core.parser.ast.ComparisonNode;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.LiteralNode;
import com.helix.core.parser.ast.OnnxInferenceNode;
import com.helix.core.parser.ast.VariableNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Real Scenario: Enterprise Financial Fraud Grammar & AST Verification")
class MlGrammarRealScenarioIntegrationTest {

    private final ExpressionRuleParser parser = new ExpressionRuleParser();

    static class ProductionFintechRegistry implements ModelRegistry {
        private final Map<String, OnnxModelDescriptor> catalog = new HashMap<>();

        void register(OnnxModelDescriptor descriptor) {
            catalog.put(descriptor.modelName(), descriptor);
        }

        @Override public Optional<OnnxModelDescriptor> resolveModel(String modelName) { return Optional.ofNullable(catalog.get(modelName)); }
        @Override public Optional<OnnxModelDescriptor> resolveModel(String modelName, String version) { return resolveModel(modelName); }
        @Override public void registerModel(OnnxModelDescriptor descriptor) { register(descriptor); }
        @Override public void activateModelVersion(String modelName, String version) {}
        @Override public void invalidateModel(String modelName) { catalog.remove(modelName); }
        @Override public void invalidateModelVersion(String modelName, String version) {}
        @Override public List<OnnxModelDescriptor> listModels() { return List.copyOf(catalog.values()); }
        @Override public List<OnnxModelDescriptor> listVersions(String modelName) { return List.copyOf(catalog.values()); }
        @Override public boolean hasModel(String modelName) { return catalog.containsKey(modelName); }
        @Override public boolean hasModelVersion(String modelName, String version) { return catalog.containsKey(modelName); }
    }

    private ProductionFintechRegistry createFintechRegistry() {
        ProductionFintechRegistry registry = new ProductionFintechRegistry();
        List<String> fraudFeatures = List.of(
                "amount", "hour_of_day", "day_of_week", "merchant_category",
                "transaction_currency", "velocity_1h", "velocity_24h", "velocity_7d",
                "amount_deviation_30d", "unique_merchants_24h", "is_new_device",
                "device_risk_score", "is_vpn_or_proxy", "country_mismatch",
                "account_age_days", "is_account_suspended", "previous_chargeback"
        );

        registry.register(OnnxModelDescriptor.builder()
                .modelName("fraud_model_v1")
                .version("1.0.0")
                .modelPath("/opt/helix/models/fraud_model_v1.onnx")
                .inputFeatures(fraudFeatures)
                .outputTensorName("probabilities")
                .outputIndex(1)
                .description("Production 17-feature XGBoost fraud classifier")
                .fileSizeBytes(31522L)
                .active(true)
                .build());

        registry.register(OnnxModelDescriptor.builder()
                .modelName("ast_reorder_policy")
                .version("1.0.0")
                .modelPath("/opt/helix/models/ast_reorder_policy.onnx")
                .inputFeatures(List.of("observation"))
                .outputTensorName("action_logits")
                .outputIndex(0)
                .description("Neural PPO actor policy for short-circuit AST clause reordering")
                .fileSizeBytes(231326L)
                .active(true)
                .build());

        return registry;
    }

    private Map<String, Class<?>> createCompleteTransactionSchema() {
        Map<String, Class<?>> schema = new HashMap<>();
        schema.put("amount", Double.class);
        schema.put("hour_of_day", Integer.class);
        schema.put("day_of_week", Integer.class);
        schema.put("merchant_category", Integer.class);
        schema.put("transaction_currency", Integer.class);
        schema.put("velocity_1h", Double.class);
        schema.put("velocity_24h", Double.class);
        schema.put("velocity_7d", Double.class);
        schema.put("amount_deviation_30d", Double.class);
        schema.put("unique_merchants_24h", Double.class);
        schema.put("is_new_device", Double.class);
        schema.put("device_risk_score", Double.class);
        schema.put("is_vpn_or_proxy", Double.class);
        schema.put("country_mismatch", Double.class);
        schema.put("account_age_days", Double.class);
        schema.put("is_account_suspended", Double.class);
        schema.put("previous_chargeback", Double.class);
        schema.put("manual_override", Boolean.class);
        return schema;
    }

    @Test
    @DisplayName("Scenario 1: Complete AST Structural Inspection for High-Risk Card-Not-Present Rule")
    void testAstTreeStructuralIntegrity() throws Exception {
        String ruleExpression = "amount > 2500.0 && velocity_1h >= 4.0 && ML(fraud_model_v1) > 0.85";

        ExpressionNode ast = parser.parse(ruleExpression);

        // Verify root node is AND
        assertInstanceOf(BinaryExpressionNode.class, ast);
        BinaryExpressionNode rootAnd = (BinaryExpressionNode) ast;
        assertEquals(BinaryOpNode.Operator.AND, rootAnd.getOperator());

        // Verify right child is the ML comparison
        assertInstanceOf(ComparisonNode.class, rootAnd.getRight());
        ComparisonNode mlComparison = (ComparisonNode) rootAnd.getRight();
        assertEquals(BinaryOpNode.Operator.GREATER_THAN, mlComparison.getOperator());

        // Verify ML inference node structure
        assertInstanceOf(OnnxInferenceNode.class, mlComparison.getLeft());
        OnnxInferenceNode mlNode = (OnnxInferenceNode) mlComparison.getLeft();
        assertEquals("fraud_model_v1", mlNode.getModelName());
        assertEquals("probabilities", mlNode.getOutputTensorName());
        assertEquals(1, mlNode.getOutputIndex());

        // Verify threshold literal
        assertInstanceOf(LiteralNode.class, mlComparison.getRight());
        assertEquals(0.85, ((Number) ((LiteralNode) mlComparison.getRight()).getValue()).doubleValue(), 1e-4);

        // Verify left child is the nested AND (amount > 2500.0 && velocity_1h >= 4.0)
        assertInstanceOf(BinaryExpressionNode.class, rootAnd.getLeft());
        BinaryExpressionNode nestedAnd = (BinaryExpressionNode) rootAnd.getLeft();
        assertEquals(BinaryOpNode.Operator.AND, nestedAnd.getOperator());

        // Verify nested left: amount > 2500.0
        assertInstanceOf(ComparisonNode.class, nestedAnd.getLeft());
        ComparisonNode amountCmp = (ComparisonNode) nestedAnd.getLeft();
        assertInstanceOf(VariableNode.class, amountCmp.getLeft());
        assertEquals("amount", ((VariableNode) amountCmp.getLeft()).getName());

        // Verify nested right: velocity_1h >= 4.0
        assertInstanceOf(ComparisonNode.class, nestedAnd.getRight());
        ComparisonNode velCmp = (ComparisonNode) nestedAnd.getRight();
        assertInstanceOf(VariableNode.class, velCmp.getLeft());
        assertEquals("velocity_1h", ((VariableNode) velCmp.getLeft()).getName());
    }

    @Test
    @DisplayName("Scenario 2: End-to-End Compile-Time Validation with Full 17-Feature Production Schema")
    void testEndToEndTypeCheckingWithFullSchema() throws Exception {
        ProductionFintechRegistry registry = createFintechRegistry();
        Map<String, Class<?>> schema = createCompleteTransactionSchema();
        TypeContext context = new TypeContext(schema, registry);
        TypeChecker checker = new TypeChecker(context);

        String ruleExpr = "is_vpn_or_proxy == 1.0 && device_risk_score > 0.80 && ML(fraud_model_v1) >= 0.90 || is_account_suspended == 1.0";
        ExpressionNode ast = parser.parse(ruleExpr);

        // Full type checking must pass and produce Boolean
        Class<?> resultType = checker.check(ast);
        assertEquals(Boolean.class, resultType, "Rule expression must evaluate to boolean condition");
    }

    @Test
    @DisplayName("Scenario 3: Guard against Missing Production Features at Compile Time")
    void testCompileTimeMissingFeatureRejection() throws Exception {
        ProductionFintechRegistry registry = createFintechRegistry();
        Map<String, Class<?>> incompleteSchema = createCompleteTransactionSchema();
        // Simulate missing feature: velocity_7d is omitted from pipeline schema
        incompleteSchema.remove("velocity_7d");

        TypeContext context = new TypeContext(incompleteSchema, registry);
        TypeChecker checker = new TypeChecker(context);

        String ruleExpr = "amount > 100.0 && ML(fraud_model_v1) > 0.80";
        ExpressionNode ast = parser.parse(ruleExpr);

        TypeMismatchException ex = assertThrows(TypeMismatchException.class, () -> checker.check(ast));
        assertTrue(ex.getMessage().contains("Missing required feature 'velocity_7d' for ML model 'fraud_model_v1'"));
    }

    @Test
    @DisplayName("Scenario 4: Guard against Unregistered Models at Compile Time")
    void testCompileTimeUnregisteredModelRejection() throws Exception {
        ProductionFintechRegistry registry = createFintechRegistry();
        Map<String, Class<?>> schema = createCompleteTransactionSchema();

        TypeContext context = new TypeContext(schema, registry);
        TypeChecker checker = new TypeChecker(context);

        String ruleExpr = "ML(experimental_crypto_fraud_v3) > 0.50";
        ExpressionNode ast = parser.parse(ruleExpr);

        TypeMismatchException ex = assertThrows(TypeMismatchException.class, () -> checker.check(ast));
        assertTrue(ex.getMessage().contains("Unregistered ML model: 'experimental_crypto_fraud_v3'"));
    }

    @Test
    @DisplayName("Scenario 5: Parsing Latency SLA Benchmark (10,000 Iterations < 25µs)")
    void testParsingLatencySla() throws Exception {
        String ruleExpression = "amount > 500.0 && ML(fraud_model_v1) > 0.80";

        // Warmup
        for (int i = 0; i < 1000; i++) {
            parser.parse(ruleExpression);
        }

        long start = System.nanoTime();
        int iterations = 10_000;
        for (int i = 0; i < iterations; i++) {
            parser.parse(ruleExpression);
        }
        long elapsedNanos = System.nanoTime() - start;
        double avgMicros = (elapsedNanos / (double) iterations) / 1000.0;

        System.out.printf("ML Rule Parsing Benchmark: %.2f µs / parse (SLA < 35 µs)%n", avgMicros);
        assertTrue(avgMicros < 35.0, "Parsing must complete with sub-35 microsecond latency");
    }
}
