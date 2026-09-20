package com.helix.core.parser;

import com.helix.api.ml.ModelRegistry;
import com.helix.api.ml.OnnxModelDescriptor;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.OnnxInferenceNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TypeChecker ML() Static Analysis Tests")
class TypeCheckerMlTest {

    private final ExpressionRuleParser parser = new ExpressionRuleParser();

    static class SimpleRegistry implements ModelRegistry {
        private final Map<String, OnnxModelDescriptor> models = new HashMap<>();

        void add(OnnxModelDescriptor desc) {
            models.put(desc.modelName(), desc);
        }

        @Override public Optional<OnnxModelDescriptor> resolveModel(String modelName) { return Optional.ofNullable(models.get(modelName)); }
        @Override public Optional<OnnxModelDescriptor> resolveModel(String modelName, String version) { return resolveModel(modelName); }
        @Override public void registerModel(OnnxModelDescriptor descriptor) { models.put(descriptor.modelName(), descriptor); }
        @Override public void activateModelVersion(String modelName, String version) {}
        @Override public void invalidateModel(String modelName) { models.remove(modelName); }
        @Override public void invalidateModelVersion(String modelName, String version) {}
        @Override public List<OnnxModelDescriptor> listModels() { return List.copyOf(models.values()); }
        @Override public List<OnnxModelDescriptor> listVersions(String modelName) { return List.copyOf(models.values()); }
        @Override public boolean hasModel(String modelName) { return models.containsKey(modelName); }
        @Override public boolean hasModelVersion(String modelName, String version) { return models.containsKey(modelName); }
    }

    @Test
    @DisplayName("Should infer Double type for OnnxInferenceNode and Boolean for relational comparison")
    void testInferTypesForMlComparison() throws Exception {
        ExpressionNode node = parser.parse("ML(fraud_model_v1)");
        TypeContext ctx = new TypeContext();
        TypeChecker checker = new TypeChecker(ctx);

        assertEquals(Double.class, checker.check(node));

        ExpressionNode cmpNode = parser.parse("ML(fraud_model_v1) > 0.85");
        assertEquals(Boolean.class, checker.check(cmpNode));
    }

    @Test
    @DisplayName("Should reject logical operators directly on ML() result")
    void testRejectLogicalOpOnMlCall() throws Exception {
        ExpressionNode badNode = parser.parse("ML(fraud_model_v1) && true");
        TypeContext ctx = new TypeContext();
        TypeChecker checker = new TypeChecker(ctx);

        TypeMismatchException ex = assertThrows(TypeMismatchException.class, () -> checker.check(badNode));
        assertTrue(ex.getMessage().contains("requires boolean operands"));
    }

    @Test
    @DisplayName("Should validate required model features when ModelRegistry is provided")
    void testValidateModelFeaturesWithRegistry() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        registry.add(OnnxModelDescriptor.builder()
                .modelName("fraud_model_v1")
                .version("1.0.0")
                .modelPath("/models/fraud.onnx")
                .inputFeatures(List.of("amount", "velocity_1h", "device_risk_score"))
                .build());

        // Case A: Missing velocity_1h in context
        Map<String, Class<?>> schemaMissing = Map.of(
                "amount", Double.class,
                "device_risk_score", Double.class
        );
        TypeContext ctxMissing = new TypeContext(schemaMissing, registry);
        TypeChecker checkerMissing = new TypeChecker(ctxMissing);
        ExpressionNode expr = parser.parse("ML(fraud_model_v1) > 0.80");

        TypeMismatchException ex = assertThrows(TypeMismatchException.class, () -> checkerMissing.check(expr));
        assertTrue(ex.getMessage().contains("Missing required feature 'velocity_1h'"));

        // Case B: All required features present in context
        Map<String, Class<?>> schemaComplete = Map.of(
                "amount", Double.class,
                "velocity_1h", Double.class,
                "device_risk_score", Double.class
        );
        TypeContext ctxComplete = new TypeContext(schemaComplete, registry);
        TypeChecker checkerComplete = new TypeChecker(ctxComplete);

        assertDoesNotThrow(() -> assertEquals(Boolean.class, checkerComplete.check(expr)));
    }

    @Test
    @DisplayName("Should reject unregistered model name when ModelRegistry is provided")
    void testRejectUnregisteredModel() throws Exception {
        SimpleRegistry registry = new SimpleRegistry();
        TypeContext ctx = new TypeContext(Collections.emptyMap(), registry);
        TypeChecker checker = new TypeChecker(ctx);
        ExpressionNode expr = parser.parse("ML(non_existent_model) > 0.5");

        TypeMismatchException ex = assertThrows(TypeMismatchException.class, () -> checker.check(expr));
        assertTrue(ex.getMessage().contains("Unregistered ML model"));
    }
}
