package com.helix.core.batch;

import com.helix.api.Rule;
import com.helix.core.parser.ExpressionRuleParser;
import com.helix.core.parser.ast.BinaryOpNode;
import com.helix.core.parser.ast.ComparisonNode;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.FieldAccessNode;
import com.helix.core.parser.ast.LiteralNode;
import com.helix.core.parser.ast.RuleNode;
import com.helix.core.parser.ast.UnaryOpNode;
import com.helix.core.parser.ast.VariableNode;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Objects;

/**
 * High-performance hardware SIMD batch rule evaluator utilizing the Java 21 Panama Vector API.
 * Evaluates numeric comparison expressions across vector strides (e.g. 8-lane {@link DoubleVector} on AVX2/AVX-512)
 * with multi-clause mask compounding, safe Double.NaN lane masking, and scalar tail loop handling.
 */
public class VectorizedBatchEvaluator {

    private final VectorSpecies<Double> species;
    private final ExpressionRuleParser parser = new ExpressionRuleParser();

    /**
     * Constructs a VectorizedBatchEvaluator using the preferred double species for the host architecture.
     */
    public VectorizedBatchEvaluator() {
        this(DoubleVector.SPECIES_PREFERRED);
    }

    /**
     * Constructs a VectorizedBatchEvaluator using a specific {@link VectorSpecies}.
     *
     * @param species target vector species
     */
    public VectorizedBatchEvaluator(VectorSpecies<Double> species) {
        this.species = Objects.requireNonNull(species, "species cannot be null");
    }

    /**
     * Returns the {@link VectorSpecies} utilized by this evaluator.
     *
     * @return current vector species
     */
    public VectorSpecies<Double> getSpecies() {
        return species;
    }

    /**
     * Evaluates a rule AST against columnar memory segments.
     *
     * @param ast       root AST node of the rule expression
     * @param columns   map of field name to columnar off-heap {@link MemorySegment}
     * @param batchSize total number of rows in the batch
     * @return boolean[] results where each element corresponds to a row outcome
     */
    public boolean[] evaluate(ExpressionNode ast, Map<String, MemorySegment> columns, int batchSize) {
        Objects.requireNonNull(ast, "ast cannot be null");
        Objects.requireNonNull(columns, "columns cannot be null");
        if (batchSize < 0) {
            throw new IllegalArgumentException("batchSize cannot be negative: " + batchSize);
        }
        if (batchSize == 0) {
            return new boolean[0];
        }

        ExpressionNode root = (ast instanceof RuleNode rn) ? rn.getRootNode() : ast;
        boolean[] results = new boolean[batchSize];

        int stride = species.length();
        int loopBound = species.loopBound(batchSize);

        // Vectorized SIMD stride loop
        for (int i = 0; i < loopBound; i += stride) {
            VectorMask<Double> mask = evaluateBooleanVector(root, columns, i, species);
            mask.intoArray(results, i);
        }

        // Scalar remainder tail loop
        for (int i = loopBound; i < batchSize; i++) {
            results[i] = evaluateScalarBoolean(root, columns, i);
        }

        return results;
    }

    /**
     * Evaluates a rule expression string against columnar memory segments.
     *
     * @param expression rule expression string
     * @param columns    map of field name to columnar off-heap {@link MemorySegment}
     * @param batchSize  total number of rows in the batch
     * @return boolean[] results
     */
    public boolean[] evaluate(String expression, Map<String, MemorySegment> columns, int batchSize) {
        Objects.requireNonNull(expression, "expression cannot be null");
        try {
            return evaluate(parser.parse(expression), columns, batchSize);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse rule expression: " + expression, e);
        }
    }

    /**
     * Evaluates a {@link Rule} against columnar memory segments.
     *
     * @param rule      rule definition
     * @param columns   map of field name to columnar off-heap {@link MemorySegment}
     * @param batchSize total number of rows in the batch
     * @return boolean[] results
     */
    public boolean[] evaluate(Rule rule, Map<String, MemorySegment> columns, int batchSize) {
        Objects.requireNonNull(rule, "rule cannot be null");
        if (rule instanceof RuleNode rn) {
            return evaluate(rn.getRootNode(), columns, batchSize);
        }
        return evaluate(rule.getExpression(), columns, batchSize);
    }

    private VectorMask<Double> evaluateBooleanVector(
            ExpressionNode node,
            Map<String, MemorySegment> columns,
            int offset,
            VectorSpecies<Double> species) {

        if (node instanceof ComparisonNode || (node instanceof BinaryOpNode bon && isComparisonOperator(bon.getOperator()))) {
            BinaryOpNode bon = (BinaryOpNode) node;
            DoubleVector vLeft = evaluateNumericVector(bon.getLeft(), columns, offset, species);
            DoubleVector vRight = evaluateNumericVector(bon.getRight(), columns, offset, species);

            VectorMask<Double> compMask = switch (bon.getOperator()) {
                case GREATER_THAN -> vLeft.compare(VectorOperators.GT, vRight);
                case GREATER_EQUAL -> vLeft.compare(VectorOperators.GE, vRight);
                case LESS_THAN -> vLeft.compare(VectorOperators.LT, vRight);
                case LESS_EQUAL -> vLeft.compare(VectorOperators.LE, vRight);
                case EQUAL -> vLeft.compare(VectorOperators.EQ, vRight);
                case NOT_EQUAL -> vLeft.compare(VectorOperators.NE, vRight);
                default -> throw new IllegalArgumentException("Unsupported comparison operator: " + bon.getOperator());
            };

            // Double.NaN lane masking: any lane with NaN in either operand evaluates to false
            VectorMask<Double> nanMask = vLeft.test(VectorOperators.IS_NAN).or(vRight.test(VectorOperators.IS_NAN));
            return compMask.andNot(nanMask);
        }

        if (node instanceof BinaryOpNode bon) {
            if (bon.getOperator() == BinaryOpNode.Operator.AND) {
                VectorMask<Double> leftMask = evaluateBooleanVector(bon.getLeft(), columns, offset, species);
                VectorMask<Double> rightMask = evaluateBooleanVector(bon.getRight(), columns, offset, species);
                return leftMask.and(rightMask);
            }
            if (bon.getOperator() == BinaryOpNode.Operator.OR) {
                VectorMask<Double> leftMask = evaluateBooleanVector(bon.getLeft(), columns, offset, species);
                VectorMask<Double> rightMask = evaluateBooleanVector(bon.getRight(), columns, offset, species);
                return leftMask.or(rightMask);
            }
        }

        if (node instanceof UnaryOpNode uon) {
            if (uon.getOperator() == UnaryOpNode.Operator.NOT) {
                VectorMask<Double> operandMask = evaluateBooleanVector(uon.getOperand(), columns, offset, species);
                return operandMask.not();
            }
        }

        if (node instanceof LiteralNode ln) {
            if (ln.getValue() instanceof Boolean b) {
                return b ? species.maskAll(true) : species.maskAll(false);
            }
        }

        throw new IllegalArgumentException("Unsupported boolean AST node for SIMD evaluation: " + node.getClass().getName());
    }

    private DoubleVector evaluateNumericVector(
            ExpressionNode node,
            Map<String, MemorySegment> columns,
            int offset,
            VectorSpecies<Double> species) {

        if (node instanceof LiteralNode ln) {
            if (ln.getValue() instanceof Number n) {
                return DoubleVector.broadcast(species, n.doubleValue());
            }
            return DoubleVector.broadcast(species, Double.NaN);
        }

        if (node instanceof VariableNode || node instanceof FieldAccessNode) {
            String fieldName = extractFieldName(node);
            MemorySegment segment = columns.get(fieldName);
            if (segment == null) {
                return DoubleVector.broadcast(species, Double.NaN);
            }
            long byteOffset = (long) offset * Double.BYTES;
            return DoubleVector.fromMemorySegment(species, segment, byteOffset, ByteOrder.nativeOrder());
        }

        if (node instanceof BinaryOpNode bon) {
            DoubleVector vLeft = evaluateNumericVector(bon.getLeft(), columns, offset, species);
            DoubleVector vRight = evaluateNumericVector(bon.getRight(), columns, offset, species);

            return switch (bon.getOperator()) {
                case ADD -> vLeft.add(vRight);
                case SUBTRACT -> vLeft.sub(vRight);
                case MULTIPLY -> vLeft.mul(vRight);
                case DIVIDE -> vLeft.div(vRight);
                case MODULO -> {
                    double[] left = vLeft.toArray();
                    double[] right = vRight.toArray();
                    double[] res = new double[species.length()];
                    for (int k = 0; k < species.length(); k++) {
                        res[k] = left[k] % right[k];
                    }
                    yield DoubleVector.fromArray(species, res, 0);
                }
                default -> throw new IllegalArgumentException("Unsupported arithmetic operator for SIMD: " + bon.getOperator());
            };
        }

        if (node instanceof UnaryOpNode uon) {
            if (uon.getOperator() == UnaryOpNode.Operator.NEGATE) {
                return evaluateNumericVector(uon.getOperand(), columns, offset, species).neg();
            }
        }

        throw new IllegalArgumentException("Unsupported numeric AST node for SIMD evaluation: " + node.getClass().getName());
    }

    private boolean evaluateScalarBoolean(ExpressionNode node, Map<String, MemorySegment> columns, int index) {
        if (node instanceof ComparisonNode || (node instanceof BinaryOpNode bon && isComparisonOperator(bon.getOperator()))) {
            BinaryOpNode bon = (BinaryOpNode) node;
            double left = evaluateScalarNumeric(bon.getLeft(), columns, index);
            double right = evaluateScalarNumeric(bon.getRight(), columns, index);

            if (Double.isNaN(left) || Double.isNaN(right)) {
                return false;
            }

            return switch (bon.getOperator()) {
                case GREATER_THAN -> left > right;
                case GREATER_EQUAL -> left >= right;
                case LESS_THAN -> left < right;
                case LESS_EQUAL -> left <= right;
                case EQUAL -> left == right;
                case NOT_EQUAL -> left != right;
                default -> throw new IllegalArgumentException("Unsupported comparison operator: " + bon.getOperator());
            };
        }

        if (node instanceof BinaryOpNode bon) {
            if (bon.getOperator() == BinaryOpNode.Operator.AND) {
                return evaluateScalarBoolean(bon.getLeft(), columns, index) && evaluateScalarBoolean(bon.getRight(), columns, index);
            }
            if (bon.getOperator() == BinaryOpNode.Operator.OR) {
                return evaluateScalarBoolean(bon.getLeft(), columns, index) || evaluateScalarBoolean(bon.getRight(), columns, index);
            }
        }

        if (node instanceof UnaryOpNode uon) {
            if (uon.getOperator() == UnaryOpNode.Operator.NOT) {
                return !evaluateScalarBoolean(uon.getOperand(), columns, index);
            }
        }

        if (node instanceof LiteralNode ln) {
            if (ln.getValue() instanceof Boolean b) {
                return b;
            }
        }

        throw new IllegalArgumentException("Unsupported scalar boolean AST node: " + node.getClass().getName());
    }

    private double evaluateScalarNumeric(ExpressionNode node, Map<String, MemorySegment> columns, int index) {
        if (node instanceof LiteralNode ln) {
            if (ln.getValue() instanceof Number n) {
                return n.doubleValue();
            }
            return Double.NaN;
        }

        if (node instanceof VariableNode || node instanceof FieldAccessNode) {
            String fieldName = extractFieldName(node);
            MemorySegment segment = columns.get(fieldName);
            if (segment == null) {
                return Double.NaN;
            }
            return segment.getAtIndex(ValueLayout.JAVA_DOUBLE, index);
        }

        if (node instanceof BinaryOpNode bon) {
            double left = evaluateScalarNumeric(bon.getLeft(), columns, index);
            double right = evaluateScalarNumeric(bon.getRight(), columns, index);

            return switch (bon.getOperator()) {
                case ADD -> left + right;
                case SUBTRACT -> left - right;
                case MULTIPLY -> left * right;
                case DIVIDE -> left / right;
                case MODULO -> left % right;
                default -> throw new IllegalArgumentException("Unsupported scalar arithmetic operator: " + bon.getOperator());
            };
        }

        if (node instanceof UnaryOpNode uon) {
            if (uon.getOperator() == UnaryOpNode.Operator.NEGATE) {
                return -evaluateScalarNumeric(uon.getOperand(), columns, index);
            }
        }

        throw new IllegalArgumentException("Unsupported scalar numeric AST node: " + node.getClass().getName());
    }

    private boolean isComparisonOperator(BinaryOpNode.Operator op) {
        return op == BinaryOpNode.Operator.GREATER_THAN
                || op == BinaryOpNode.Operator.GREATER_EQUAL
                || op == BinaryOpNode.Operator.LESS_THAN
                || op == BinaryOpNode.Operator.LESS_EQUAL
                || op == BinaryOpNode.Operator.EQUAL
                || op == BinaryOpNode.Operator.NOT_EQUAL;
    }

    private String extractFieldName(ExpressionNode node) {
        if (node instanceof VariableNode vn) {
            return vn.getName();
        }
        if (node instanceof FieldAccessNode fan) {
            return fan.getFullPath();
        }
        return null;
    }
}
