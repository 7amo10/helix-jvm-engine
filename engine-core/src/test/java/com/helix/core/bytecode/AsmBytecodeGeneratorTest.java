package com.helix.core.bytecode;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.api.ml.OnnxModelDescriptor;
import com.helix.core.classloader.RuleClassLoader;
import com.helix.core.ml.LocalModelRegistry;
import com.helix.core.ml.OnnxModelExecutor;
import com.helix.core.ml.OnnxSessionPool;
import com.helix.core.parser.ExpressionRuleParser;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.RuleNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AsmBytecodeGenerator Tests")
class AsmBytecodeGeneratorTest {

    private static LocalModelRegistry registry;
    private static OnnxSessionPool sessionPool;

    private static final List<String> FRAUD_FEATURES = List.of(
            "amount", "hour_of_day", "day_of_week", "merchant_category", "transaction_currency",
            "velocity_1h", "velocity_24h", "velocity_7d", "amount_deviation_30d", "unique_merchants_24h",
            "is_new_device", "device_risk_score", "is_vpn_or_proxy", "country_mismatch",
            "account_age_days", "is_account_suspended", "previous_chargeback"
    );

    @BeforeAll
    static void setUp() {
        URL modelUrl = AsmBytecodeGeneratorTest.class.getResource("/models/fraud_model_v1.onnx");
        assertNotNull(modelUrl);
        File modelFile = new File(modelUrl.getFile());

        OnnxModelDescriptor descriptor = OnnxModelDescriptor.builder()
                .modelName("fraud_model_v1")
                .version("1.0.0")
                .modelPath(modelFile.getAbsolutePath())
                .inputFeatures(FRAUD_FEATURES)
                .outputTensorName("probabilities")
                .outputIndex(1)
                .active(true)
                .build();

        registry = new LocalModelRegistry();
        registry.registerModel(descriptor);

        sessionPool = new OnnxSessionPool(registry, 4);
        OnnxModelExecutor.setSessionPool(sessionPool);
    }

    @AfterAll
    static void tearDown() {
        OnnxModelExecutor.reset();
        if (sessionPool != null) {
            sessionPool.close();
        }
    }

    private ExecutionContext createLegitContext() {
        return new ExecutionContext(Map.ofEntries(
                Map.entry("amount", 66.55),
                Map.entry("hour_of_day", 10.0),
                Map.entry("day_of_week", 4.0),
                Map.entry("merchant_category", 0.0),
                Map.entry("transaction_currency", 0.0),
                Map.entry("velocity_1h", 1.0),
                Map.entry("velocity_24h", 5.0),
                Map.entry("velocity_7d", 21.0),
                Map.entry("amount_deviation_30d", -1.10),
                Map.entry("unique_merchants_24h", 5.0),
                Map.entry("is_new_device", 0.0),
                Map.entry("device_risk_score", 0.35),
                Map.entry("is_vpn_or_proxy", 0.0),
                Map.entry("country_mismatch", 0.0),
                Map.entry("account_age_days", 3511.0),
                Map.entry("is_account_suspended", 0.0),
                Map.entry("previous_chargeback", 1.0)
        ));
    }

    private ExecutionContext createFraudContext() {
        return new ExecutionContext(Map.ofEntries(
                Map.entry("amount", 1728.70),
                Map.entry("hour_of_day", 2.0),
                Map.entry("day_of_week", 4.0),
                Map.entry("merchant_category", 3.0),
                Map.entry("transaction_currency", 2.0),
                Map.entry("velocity_1h", 5.0),
                Map.entry("velocity_24h", 20.0),
                Map.entry("velocity_7d", 65.0),
                Map.entry("amount_deviation_30d", 2.09),
                Map.entry("unique_merchants_24h", 9.0),
                Map.entry("is_new_device", 1.0),
                Map.entry("device_risk_score", 0.43),
                Map.entry("is_vpn_or_proxy", 0.0),
                Map.entry("country_mismatch", 1.0),
                Map.entry("account_age_days", 86.0),
                Map.entry("is_account_suspended", 0.0),
                Map.entry("previous_chargeback", 0.0)
        ));
    }

    @Test
    @DisplayName("Should generate valid bytecode opcodes containing ALOAD, LDC, INVOKESTATIC, FCMP")
    void testDisassembledBytecodeOpcodes() throws Exception {
        ExpressionRuleParser parser = new ExpressionRuleParser();
        ExpressionNode ast = parser.parse("ML('fraud_model_v1') > 0.85");
        Rule rule = new RuleNode("TestBytecodeOpcodesRule", "ML('fraud_model_v1') > 0.85", Map.of(), ast);

        AsmBytecodeGenerator generator = new AsmBytecodeGenerator();
        byte[] classBytes = generator.generateBytecode("com.helix.compiled.asm.TestBytecodeOpcodesRule", rule, ast);

        assertNotNull(classBytes);
        assertTrue(classBytes.length > 0);

        // Disassemble class bytes to inspect bytecode instructions
        StringWriter sw = new StringWriter();
        ClassReader cr = new ClassReader(classBytes);
        cr.accept(new TraceClassVisitor(new PrintWriter(sw)), 0);
        String disassembly = sw.toString();

        assertTrue(disassembly.contains("ALOAD"), "Must emit ALOAD for ExecutionContext");
        assertTrue(disassembly.contains("LDC \"fraud_model_v1\""), "Must emit LDC for model name constant");
        assertTrue(disassembly.contains("com/helix/core/ml/OnnxSessionPool") || disassembly.contains("com/helix/core/ml/OnnxModelExecutor"),
                "Must invoke ML runtime bridge");
        assertTrue(disassembly.contains("FCMP") || disassembly.contains("DCMP"), "Must emit float/double comparison opcode");
    }

    @Test
    @DisplayName("Should compile and execute in isolated RuleClassLoader with accurate decisions")
    void testExecutionInRuleClassLoader() throws Exception {
        ExpressionRuleParser parser = new ExpressionRuleParser();
        ExpressionNode ast = parser.parse("ML('fraud_model_v1') > 0.85");
        Rule rule = new RuleNode("ExecutionInRuleLoaderRule", "ML('fraud_model_v1') > 0.85", Map.of(), ast);

        AsmBytecodeGenerator generator = new AsmBytecodeGenerator();
        CompiledRule compiled = generator.generate(rule, ast);

        assertNotNull(compiled);

        // Verify legitimate transaction decision
        ExecutionContext legitCtx = createLegitContext();
        ExecutionResult legitRes = compiled.execute(legitCtx);
        assertTrue(legitRes.isSuccess());
        assertEquals(Boolean.FALSE, legitRes.getResult().orElse(null), "Legit transaction should evaluate to FALSE");

        // Verify fraudulent transaction decision
        ExecutionContext fraudCtx = createFraudContext();
        ExecutionResult fraudRes = compiled.execute(fraudCtx);
        assertTrue(fraudRes.isSuccess());
        assertEquals(Boolean.TRUE, fraudRes.getResult().orElse(null), "Fraudulent transaction should evaluate to TRUE");
    }

    @Test
    @DisplayName("Should propagate error as ExecutionResult failure on runtime exception")
    void testErrorPropagationOnMissingModel() throws Exception {
        ExpressionRuleParser parser = new ExpressionRuleParser();
        ExpressionNode ast = parser.parse("ML('unregistered_model') > 0.5");
        Rule rule = new RuleNode("ErrorPropagationRule", "ML('unregistered_model') > 0.5", Map.of(), ast);

        AsmBytecodeGenerator generator = new AsmBytecodeGenerator();
        CompiledRule compiled = generator.generate(rule, ast);

        ExecutionContext ctx = createLegitContext();
        ExecutionResult res = compiled.execute(ctx);
        assertFalse(res.isSuccess(), "Evaluation must fail when model is unregistered");
        assertTrue(res.getError().isPresent());
    }
}
