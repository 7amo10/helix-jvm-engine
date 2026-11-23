package com.helix.core.batch;

import com.helix.api.ExecutionContext;
import com.helix.core.parser.ExpressionRuleParser;
import com.helix.core.parser.ast.ExpressionNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VectorizedBatchEvaluator: AVX2 8-Lane SIMD Vectorized Batch Rule Evaluation")
class VectorizedBatchEvaluatorTest {

    private final ExpressionRuleParser parser = new ExpressionRuleParser();
    private final VectorizedBatchEvaluator evaluator = new VectorizedBatchEvaluator();

    @Test
    @DisplayName("Evaluates all comparison operators with bit-exact parity against scalar evaluation")
    void testAllComparisonOperatorsParityWithScalar() throws Exception {
        String[] expressions = {
                "x > 50.0",
                "x >= 50.0",
                "x < 50.0",
                "x <= 50.0",
                "x == 50.0",
                "x != 50.0"
        };

        double[] testValues = {0.0, 49.99, 50.0, 50.01, 100.0, -10.0, 50.0, 75.0};
        int batchSize = testValues.length;

        try (Arena arena = Arena.ofConfined()) {
            ExecutionContext[] batch = new ExecutionContext[batchSize];
            for (int i = 0; i < batchSize; i++) {
                batch[i] = new ExecutionContext(Map.of("x", testValues[i]));
            }

            Map<String, MemorySegment> columns = ColumnExtractor.extract(batch, List.of("x"), arena);

            for (String expr : expressions) {
                ExpressionNode ast = parser.parse(expr);
                boolean[] simdResults = evaluator.evaluate(ast, columns, batchSize);

                assertEquals(batchSize, simdResults.length);
                for (int i = 0; i < batchSize; i++) {
                    double x = testValues[i];
                    boolean expected = switch (expr) {
                        case "x > 50.0" -> x > 50.0;
                        case "x >= 50.0" -> x >= 50.0;
                        case "x < 50.0" -> x < 50.0;
                        case "x <= 50.0" -> x <= 50.0;
                        case "x == 50.0" -> x == 50.0;
                        case "x != 50.0" -> x != 50.0;
                        default -> throw new IllegalStateException();
                    };
                    assertEquals(expected, simdResults[i],
                            String.format("Mismatch at index %d for expr '%s' with x=%f", i, expr, x));
                }
            }
        }
    }

    @Test
    @DisplayName("Evaluates composite AND / OR logical chains and unary negation correctly")
    void testCompoundLogicAndOrChains() throws Exception {
        String expr = "(amount > 100.0 && risk < 0.5) || (amount > 1000.0 && !(risk >= 0.9))";
        ExpressionNode ast = parser.parse(expr);

        int batchSize = 16;
        try (Arena arena = Arena.ofConfined()) {
            ExecutionContext[] batch = new ExecutionContext[batchSize];
            for (int i = 0; i < batchSize; i++) {
                double amount = (i % 2 == 0) ? 150.0 : ((i % 4 == 1) ? 1200.0 : 50.0);
                double risk = (i % 3 == 0) ? 0.3 : 0.95;
                batch[i] = new ExecutionContext(Map.of("amount", amount, "risk", risk));
            }

            Map<String, MemorySegment> columns = ColumnExtractor.extract(batch, List.of("amount", "risk"), arena);
            boolean[] results = evaluator.evaluate(ast, columns, batchSize);

            for (int i = 0; i < batchSize; i++) {
                double amount = batch[i].getDouble("amount");
                double risk = batch[i].getDouble("risk");
                boolean expected = (amount > 100.0 && risk < 0.5) || (amount > 1000.0 && !(risk >= 0.9));
                assertEquals(expected, results[i], "Mismatch at row " + i);
            }
        }
    }

    @Test
    @DisplayName("Evaluates arithmetic expressions and unary negation within comparison clauses")
    void testArithmeticExpressionsWithinClauses() throws Exception {
        String expr = "(amount * 1.05 > 100.0) && (risk + 0.1 < 0.8) && (-creditScore < -500.0)";
        ExpressionNode ast = parser.parse(expr);

        int batchSize = 24;
        try (Arena arena = Arena.ofConfined()) {
            ExecutionContext[] batch = new ExecutionContext[batchSize];
            for (int i = 0; i < batchSize; i++) {
                batch[i] = new ExecutionContext(Map.of(
                        "amount", 96.0 + i,
                        "risk", 0.65 - (i * 0.01),
                        "creditScore", 480.0 + (i * 2.0)
                ));
            }

            Map<String, MemorySegment> columns = ColumnExtractor.extract(
                    batch, List.of("amount", "risk", "creditScore"), arena
            );
            boolean[] results = evaluator.evaluate(ast, columns, batchSize);

            for (int i = 0; i < batchSize; i++) {
                double amount = batch[i].getDouble("amount");
                double risk = batch[i].getDouble("risk");
                double creditScore = batch[i].getDouble("creditScore");

                boolean expected = (amount * 1.05 > 100.0)
                        && (risk + 0.1 < 0.8)
                        && (-creditScore < -500.0);
                assertEquals(expected, results[i], "Mismatch at row " + i);
            }
        }
    }

    @Test
    @DisplayName("Safely encodes Double.NaN lanes to false without hardware traps or exceptions")
    void testDoubleNaNLaneMaskingSafety() throws Exception {
        String[] exprs = {"x > 50.0", "x < 50.0", "x >= 50.0", "x <= 50.0", "x == 50.0", "x != 50.0"};

        try (Arena arena = Arena.ofConfined()) {
            ExecutionContext[] batch = new ExecutionContext[] {
                    new ExecutionContext(Map.of("x", Double.NaN)),
                    new ExecutionContext(Map.of("x", 100.0)),
                    new ExecutionContext(Map.of("x", Double.NaN)),
                    new ExecutionContext(Map.of("x", 20.0)),
                    new ExecutionContext(Map.of("x", Double.NaN)),
                    new ExecutionContext(Map.of("x", Double.NaN)),
                    new ExecutionContext(Map.of("x", 50.0)),
                    new ExecutionContext(Map.of("x", Double.NaN))
            };

            Map<String, MemorySegment> columns = ColumnExtractor.extract(batch, List.of("x"), arena);

            for (String expr : exprs) {
                ExpressionNode ast = parser.parse(expr);
                boolean[] results = evaluator.evaluate(ast, columns, batch.length);

                for (int i = 0; i < batch.length; i++) {
                    if (Double.isNaN(batch[i].getDouble("x"))) {
                        assertFalse(results[i],
                                String.format("Lane %d with Double.NaN must evaluate to false for expr '%s'", i, expr));
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 7, 8, 9, 15, 16, 17, 31, 32, 33, 100, 255, 256, 1001})
    @DisplayName("Evaluates arbitrary batch sizes and stride remainder tails accurately")
    void testArbitraryBatchSizesAndTailRemainder(int batchSize) throws Exception {
        String expr = "x > 25.0 && y <= 50.0";
        ExpressionNode ast = parser.parse(expr);

        try (Arena arena = Arena.ofConfined()) {
            ExecutionContext[] batch = new ExecutionContext[batchSize];
            for (int i = 0; i < batchSize; i++) {
                batch[i] = new ExecutionContext(Map.of("x", (double) (i % 50), "y", (double) (i % 100)));
            }

            Map<String, MemorySegment> columns = ColumnExtractor.extract(batch, List.of("x", "y"), arena);
            boolean[] results = evaluator.evaluate(ast, columns, batchSize);

            assertEquals(batchSize, results.length);
            for (int i = 0; i < batchSize; i++) {
                double x = (double) (i % 50);
                double y = (double) (i % 100);
                boolean expected = (x > 25.0 && y <= 50.0);
                assertEquals(expected, results[i], "Mismatch at index " + i + " for batchSize " + batchSize);
            }
        }
    }

    @Test
    @DisplayName("Verifies bit-exact parity against scalar evaluation across 10,000 randomized test cases")
    void testRandomizedParityOver10000Cases() throws Exception {
        String expr = "(a > 10.0 && b <= 20.0) || (c == 0.0 && !(d < 5.0))";
        ExpressionNode ast = parser.parse(expr);

        int batchSize = 10_000;
        Random rng = new Random(42);

        try (Arena arena = Arena.ofConfined()) {
            ExecutionContext[] batch = new ExecutionContext[batchSize];
            List<String> fields = List.of("a", "b", "c", "d");

            for (int i = 0; i < batchSize; i++) {
                // 5% chance of missing/NaN field
                Double a = rng.nextDouble() < 0.05 ? Double.NaN : rng.nextDouble() * 30.0;
                Double b = rng.nextDouble() < 0.05 ? Double.NaN : rng.nextDouble() * 40.0;
                Double c = rng.nextDouble() < 0.05 ? Double.NaN : (rng.nextBoolean() ? 0.0 : 1.0);
                Double d = rng.nextDouble() < 0.05 ? Double.NaN : rng.nextDouble() * 10.0;

                batch[i] = new ExecutionContext(Map.of("a", a, "b", b, "c", c, "d", d));
            }

            Map<String, MemorySegment> columns = ColumnExtractor.extract(batch, fields, arena);
            boolean[] simdResults = evaluator.evaluate(ast, columns, batchSize);

            assertEquals(batchSize, simdResults.length);

            for (int i = 0; i < batchSize; i++) {
                double a = batch[i].getDouble("a");
                double b = batch[i].getDouble("b");
                double c = batch[i].getDouble("c");
                double d = batch[i].getDouble("d");

                boolean clause1 = (a > 10.0 && b <= 20.0);
                boolean clause2 = (c == 0.0 && !(d < 5.0));
                boolean expected = clause1 || clause2;

                assertEquals(expected, simdResults[i], "Randomized parity mismatch at index " + i);
            }
        }
    }

    @Test
    @DisplayName("Handles empty batch and null arguments safely")
    void testEdgeCases() throws Exception {
        ExpressionNode ast = parser.parse("x > 10.0");
        try (Arena arena = Arena.ofConfined()) {
            Map<String, MemorySegment> columns = ColumnExtractor.extract(new ExecutionContext[0], List.of("x"), arena);
            boolean[] emptyResults = evaluator.evaluate(ast, columns, 0);
            assertEquals(0, emptyResults.length);

            assertThrows(NullPointerException.class, () -> evaluator.evaluate((ExpressionNode) null, columns, 0));
            assertThrows(NullPointerException.class, () -> evaluator.evaluate(ast, null, 0));
            assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(ast, columns, -1));
        }
    }
}
