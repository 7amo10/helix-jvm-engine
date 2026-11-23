package com.helix.core.batch;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.core.RuleCompiler;
import com.helix.core.executor.BatchExecutor;
import com.helix.core.parser.ExpressionRuleParser;
import com.helix.core.parser.ast.AstBuilder;
import com.helix.core.parser.ast.ExpressionNode;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorSpecies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BatchDispatcherTest {

    private BatchDispatcher defaultDispatcher;
    private BatchDispatcher scalarOnlyDispatcher;
    private RuleCompiler compiler;
    private AstBuilder astBuilder;

    @BeforeEach
    void setUp() {
        defaultDispatcher = new BatchDispatcher();
        scalarOnlyDispatcher = new BatchDispatcher(true);
        compiler = new RuleCompiler();
        astBuilder = new AstBuilder();
    }

    @Test
    @DisplayName("Should probe runtime SIMD capability once at startup and cache preferred species")
    void testSimdCapabilityProbe() {
        VectorSpecies<Double> preferred = DoubleVector.SPECIES_PREFERRED;
        boolean expectedCapable = preferred.length() > 1;

        assertEquals(expectedCapable, BatchDispatcher.isSimdCapable());
        assertEquals(preferred.length(), BatchDispatcher.getSimdLaneCount());
        assertEquals(preferred, BatchDispatcher.getPreferredSpecies());

        // Verify instance defaults reflect host capabilities
        assertEquals(expectedCapable, defaultDispatcher.isSimdActive());
        assertEquals(preferred.length(), defaultDispatcher.getLaneCount());
        assertFalse(scalarOnlyDispatcher.isSimdActive());
    }

    @Test
    @DisplayName("Should route vectorizable numeric rule with batchSize >= laneCount to SIMD path")
    void testRouteVectorizableNumericRuleToSimd() throws Exception {
        int laneCount = defaultDispatcher.getLaneCount();
        int batchSize = Math.max(laneCount * 4, 32);

        String expr = "amount >= 100.0 && riskScore < 50.0";
        ExpressionNode ast = astBuilder.buildAst(expr);
        List<String> fields = List.of("amount", "riskScore");

        List<ExecutionContext> contexts = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            contexts.add(new ExecutionContext(Map.of(
                    "amount", (double) (i * 10),
                    "riskScore", (double) (i % 60)
            )));
        }

        BatchDispatcher.DispatchResult result = defaultDispatcher.dispatch(ast, fields, contexts);

        assertNotNull(result);
        assertEquals(batchSize, result.results().length);
        assertEquals(batchSize, result.executionResults().size());
        if (BatchDispatcher.isSimdCapable()) {
            assertEquals(BatchDispatcher.EvaluationMode.SIMD, result.evaluationMode());
            assertTrue(result.isSimd());
            assertFalse(result.isScalar());
        }
    }

    @Test
    @DisplayName("Should route non-vectorizable rule (with ML inference) to scalar path")
    void testRouteNonVectorizableRuleToScalar() throws Exception {
        int batchSize = 32;
        String expr = "ML('models/fraud_detector.onnx', amount, velocity) > 0.8";

        List<ExecutionContext> contexts = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            contexts.add(new ExecutionContext(Map.of(
                    "amount", (double) (i * 10),
                    "velocity", (double) i
            )));
        }

        BatchDispatcher.DispatchResult result = defaultDispatcher.dispatch(expr, contexts);

        assertNotNull(result);
        assertEquals(BatchDispatcher.EvaluationMode.SCALAR, result.evaluationMode());
        assertTrue(result.isScalar());
        assertFalse(result.isSimd());
    }

    @Test
    @DisplayName("Should transparently fall back to scalar path when batch size < vector lane count")
    void testRouteSmallBatchToScalarFallback() throws Exception {
        int laneCount = defaultDispatcher.getLaneCount();
        int smallBatchSize = Math.max(1, laneCount - 1);

        String expr = "x > 10.0";
        List<ExecutionContext> contexts = new ArrayList<>();
        for (int i = 0; i < smallBatchSize; i++) {
            contexts.add(new ExecutionContext(Map.of("x", 20.0)));
        }

        BatchDispatcher.DispatchResult result = defaultDispatcher.dispatch(expr, contexts);

        assertNotNull(result);
        assertEquals(smallBatchSize, result.results().length);
        assertEquals(BatchDispatcher.EvaluationMode.SCALAR, result.evaluationMode());
        assertTrue(result.isScalar());
    }

    @Test
    @DisplayName("Should transparently route to scalar path when SIMD is disabled")
    void testRouteDisabledSimdToScalar() throws Exception {
        int batchSize = 64;
        String expr = "x > 10.0 && y <= 50.0";

        List<ExecutionContext> contexts = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            contexts.add(new ExecutionContext(Map.of("x", (double) i, "y", (double) (i * 2))));
        }

        BatchDispatcher.DispatchResult result = scalarOnlyDispatcher.dispatch(expr, contexts);

        assertNotNull(result);
        assertEquals(batchSize, result.results().length);
        assertEquals(BatchDispatcher.EvaluationMode.SCALAR, result.evaluationMode());
        assertTrue(result.isScalar());
    }

    @Test
    @DisplayName("Should guarantee bit-exact result parity between SIMD and scalar evaluation over 1000 randomized contexts")
    void testBitExactParityBetweenSimdAndScalar() throws Exception {
        String expr = "(x * 2.5 + y) > 100.0 && !(z <= 0.0) || w == 42.0";
        ExpressionNode ast = astBuilder.buildAst(expr);
        List<String> fields = List.of("x", "y", "z", "w");

        int batchSize = 1000;
        Random rng = new Random(42);
        List<ExecutionContext> contexts = new ArrayList<>(batchSize);

        for (int i = 0; i < batchSize; i++) {
            Map<String, Object> vars = new HashMap<>();
            // Include diverse numbers: negatives, zero, boundaries, NaNs
            vars.put("x", (rng.nextInt(10) == 0) ? Double.NaN : (rng.nextDouble() * 200.0 - 50.0));
            vars.put("y", (rng.nextInt(10) == 0) ? Double.NaN : (rng.nextDouble() * 100.0));
            vars.put("z", (rng.nextInt(20) == 0) ? 0.0 : (rng.nextDouble() * 20.0 - 5.0));
            vars.put("w", (rng.nextInt(15) == 0) ? 42.0 : rng.nextDouble() * 50.0);
            contexts.add(new ExecutionContext(vars));
        }

        BatchDispatcher.DispatchResult simdResult = defaultDispatcher.dispatch(ast, fields, contexts);
        BatchDispatcher.DispatchResult scalarResult = scalarOnlyDispatcher.dispatch(ast, fields, contexts);

        assertEquals(batchSize, simdResult.results().length);
        assertEquals(batchSize, scalarResult.results().length);

        if (BatchDispatcher.isSimdCapable()) {
            assertEquals(BatchDispatcher.EvaluationMode.SIMD, simdResult.evaluationMode());
        }
        assertEquals(BatchDispatcher.EvaluationMode.SCALAR, scalarResult.evaluationMode());

        assertArrayEquals(scalarResult.results(), simdResult.results(),
                "SIMD and scalar paths must produce bit-exact identical boolean results");
    }

    @Test
    @DisplayName("Should dispatch Rule definition and route correctly")
    void testDispatchRuleDefinition() throws Exception {
        Rule rule = new Rule() {
            @Override
            public String getName() {
                return "AccountTierRule";
            }

            @Override
            public String getVersion() {
                return "1.0.0";
            }

            @Override
            public String getDescription() {
                return "Evaluates account tier";
            }

            @Override
            public String getExpression() {
                return "balance >= 5000.0 && transactions > 10.0";
            }

            @Override
            public Map<String, Class<?>> getInputSchema() {
                return Map.of("balance", double.class, "transactions", double.class);
            }
        };

        int batchSize = 64;
        List<ExecutionContext> contexts = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            contexts.add(new ExecutionContext(Map.of(
                    "balance", (double) (i * 200),
                    "transactions", (double) i
            )));
        }

        BatchDispatcher.DispatchResult result = defaultDispatcher.dispatch(rule, contexts);
        assertNotNull(result);
        assertEquals(batchSize, result.results().length);
        if (BatchDispatcher.isSimdCapable()) {
            assertEquals(BatchDispatcher.EvaluationMode.SIMD, result.evaluationMode());
        }
    }

    @Test
    @DisplayName("Should dispatch registered rules by name")
    void testDispatchRegisteredRule() throws Exception {
        String ruleName = "DiscountQualification";
        String expr = "cartTotal >= 150.0 && loyaltyPoints >= 20.0";
        defaultDispatcher.registerRule(ruleName, expr);

        int batchSize = 64;
        List<ExecutionContext> contexts = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            contexts.add(new ExecutionContext(Map.of(
                    "cartTotal", (double) (i * 5),
                    "loyaltyPoints", (double) i
            )));
        }

        BatchDispatcher.DispatchResult result = defaultDispatcher.dispatch(ruleName, contexts);
        assertNotNull(result);
        assertEquals(batchSize, result.results().length);
        if (BatchDispatcher.isSimdCapable()) {
            assertEquals(BatchDispatcher.EvaluationMode.SIMD, result.evaluationMode());
        }
    }

    @Test
    @DisplayName("Should handle empty and null context batches safely")
    void testEmptyAndNullBatches() throws Exception {
        String expr = "x > 10.0";

        BatchDispatcher.DispatchResult emptyResult = defaultDispatcher.dispatch(expr, List.of());
        assertNotNull(emptyResult);
        assertEquals(0, emptyResult.results().length);
        assertTrue(emptyResult.executionResults().isEmpty());
        assertEquals(0, emptyResult.getBatchSize());

        BatchDispatcher.DispatchResult nullResult = defaultDispatcher.dispatch(expr, (List<ExecutionContext>) null);
        assertNotNull(nullResult);
        assertEquals(0, nullResult.results().length);
        assertTrue(nullResult.executionResults().isEmpty());
    }

    @Test
    @DisplayName("Should seamlessly integrate BatchDispatcher into BatchExecutor as drop-in")
    void testBatchExecutorIntegration() throws Exception {
        BatchExecutor executor = new BatchExecutor();
        assertNotNull(executor.getBatchDispatcher());

        String json = """
                {
                    "name": "VectorizableRule",
                    "expression": "score > 80.0",
                    "inputSchema": {
                        "score": "double"
                    }
                }
                """;
        CompiledRule rule = compiler.compile(json);

        int batchSize = 64;
        List<ExecutionContext> contexts = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            contexts.add(new ExecutionContext(Map.of("score", (double) (i * 2))));
        }

        List<ExecutionResult> results = executor.executeBatch(rule, contexts);
        assertEquals(batchSize, results.size());
        for (int i = 0; i < batchSize; i++) {
            ExecutionResult res = results.get(i);
            assertTrue(res.isSuccess());
            boolean expected = (i * 2) > 80.0;
            assertEquals(expected, res.getResult().orElse(null));
        }

        // Test overloaded method on BatchExecutor
        BatchDispatcher.DispatchResult dispatchResult = executor.dispatch(rule, contexts);
        assertNotNull(dispatchResult);
        assertEquals(batchSize, dispatchResult.getBatchSize());
    }
}
