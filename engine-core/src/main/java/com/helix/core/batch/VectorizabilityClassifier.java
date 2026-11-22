package com.helix.core.batch;

import com.helix.api.CompiledRule;
import com.helix.api.Rule;
import com.helix.core.parser.ast.AstBuilder;
import com.helix.core.parser.ast.AstVisitor;
import com.helix.core.parser.ast.BinaryExpressionNode;
import com.helix.core.parser.ast.BinaryOpNode;
import com.helix.core.parser.ast.ComparisonNode;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.FieldAccessNode;
import com.helix.core.parser.ast.FunctionCallNode;
import com.helix.core.parser.ast.LiteralNode;
import com.helix.core.parser.ast.MethodCallNode;
import com.helix.core.parser.ast.OnnxInferenceNode;
import com.helix.core.parser.ast.RuleNode;
import com.helix.core.parser.ast.UnaryOpNode;
import com.helix.core.parser.ast.VariableNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Analyzes rule AST expression trees to classify whether a rule is eligible for
 * hardware-accelerated SIMD vectorised batch evaluation using the Panama Vector API.
 * <p>
 * A rule is vectorizable if and only if its expression consists exclusively of:
 * <ul>
 *     <li>Pure numeric comparisons (&gt;, &lt;, &gt;=, &lt;=, ==, !=) over double-typed fields.</li>
 *     <li>Logical AND (&amp;&amp;) and OR (||) chains connecting numeric comparisons.</li>
 *     <li>Arithmetic expressions (+, -, *, /, %) over numeric fields.</li>
 *     <li>Unary negation (-) or logical NOT (!) on vectorizable sub-expressions.</li>
 * </ul>
 * <p>
 * Rules containing machine learning models ({@link OnnxInferenceNode}), string or object operands,
 * dynamic field lookups, method calls, custom functions, or side-effecting operations are
 * classified as non-vectorizable and will automatically route to the scalar evaluation path.
 */
public class VectorizabilityClassifier implements AstVisitor<Boolean> {

    private static final Logger log = LoggerFactory.getLogger(VectorizabilityClassifier.class);

    private final ConcurrentMap<String, Boolean> classificationCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Class<?>, Boolean> classCache = new ConcurrentHashMap<>();
    private final AstBuilder astBuilder = new AstBuilder();

    private final ThreadLocal<Map<String, Class<?>>> currentSchema = new ThreadLocal<>();

    /**
     * Classifies an {@link ExpressionNode} AST root without schema context.
     *
     * @param node the root AST node
     * @return true if the expression is vectorizable, false otherwise
     */
    public boolean classify(ExpressionNode node) {
        return classify(node, Collections.emptyMap());
    }

    /**
     * Classifies an {@link ExpressionNode} AST root with input variable schema typing.
     *
     * @param node   the root AST node
     * @param schema map of variable names to declared Java classes
     * @return true if the expression is vectorizable, false otherwise
     */
    public boolean classify(ExpressionNode node, Map<String, Class<?>> schema) {
        if (node == null) {
            return false;
        }
        try {
            currentSchema.set(schema != null ? schema : Collections.emptyMap());
            Boolean result = node.accept(this);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.debug("Exception during vectorizability classification: {}", e.getMessage(), e);
            return false;
        } finally {
            currentSchema.remove();
        }
    }

    /**
     * Classifies a composite {@link RuleNode} using its root AST node and schema.
     *
     * @param ruleNode rule node
     * @return true if vectorizable, false otherwise
     */
    public boolean classify(RuleNode ruleNode) {
        if (ruleNode == null) {
            return false;
        }
        return classify(ruleNode.getRootNode(), ruleNode.getInputSchema());
    }

    /**
     * Classifies a {@link Rule} definition by parsing its expression and inspecting its schema.
     *
     * @param rule rule definition
     * @return true if vectorizable, false otherwise
     */
    public boolean classify(Rule rule) {
        if (rule == null || rule.getExpression() == null || rule.getExpression().isBlank()) {
            return false;
        }
        try {
            ExpressionNode ast = astBuilder.buildAst(rule.getExpression());
            return classify(ast, rule.getInputSchema());
        } catch (Exception e) {
            log.debug("Failed to parse rule expression for classification: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Classifies a raw string rule expression.
     *
     * @param expression rule expression string
     * @return true if vectorizable, false otherwise
     */
    public boolean classify(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        try {
            ExpressionNode ast = astBuilder.buildAst(expression);
            return classify(ast);
        } catch (Exception e) {
            log.debug("Failed to parse raw expression for classification: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Classifies an executable {@link CompiledRule} with O(1) cached lookup and annotation inspection.
     *
     * @param compiledRule compiled rule instance
     * @return true if vectorizable, false otherwise
     */
    public boolean classify(CompiledRule compiledRule) {
        if (compiledRule == null) {
            return false;
        }

        Class<?> clazz = compiledRule.getClass();
        Boolean classResult = classCache.get(clazz);
        if (classResult != null) {
            return classResult;
        }

        VectorizableRule annotation = clazz.getAnnotation(VectorizableRule.class);
        if (annotation != null) {
            boolean isVec = annotation.value();
            classCache.put(clazz, isVec);
            return isVec;
        }

        String ruleName = compiledRule.getName();
        if (ruleName != null) {
            Boolean cachedResult = classificationCache.get(ruleName);
            if (cachedResult != null) {
                return cachedResult;
            }
        }

        return false;
    }

    /**
     * Registers a classification result for a rule name in the classification cache for O(1) lookups.
     *
     * @param ruleName     rule identifier
     * @param vectorizable vectorizability classification
     */
    public void register(String ruleName, boolean vectorizable) {
        if (ruleName != null) {
            classificationCache.put(ruleName, vectorizable);
        }
    }

    /**
     * Registers a classification result for a compiled rule instance.
     *
     * @param compiledRule rule instance
     * @param vectorizable vectorizability classification
     */
    public void register(CompiledRule compiledRule, boolean vectorizable) {
        if (compiledRule != null) {
            classCache.put(compiledRule.getClass(), vectorizable);
            if (compiledRule.getName() != null) {
                classificationCache.put(compiledRule.getName(), vectorizable);
            }
        }
    }

    /**
     * Extracts all variable and field names referenced by the given AST expression.
     * Useful for columnar memory layout allocation in SIMD batch processing.
     *
     * @param node root AST node
     * @return ordered list of unique field names
     */
    public List<String> extractReferencedFields(ExpressionNode node) {
        if (node == null) {
            return Collections.emptyList();
        }
        Set<String> fields = new LinkedHashSet<>();
        collectFields(node, fields);
        return new ArrayList<>(fields);
    }

    /**
     * Extracts all variable and field names referenced by the given rule expression string.
     *
     * @param expression rule expression string
     * @return ordered list of unique field names
     */
    public List<String> extractReferencedFields(String expression) {
        if (expression == null || expression.isBlank()) {
            return Collections.emptyList();
        }
        try {
            ExpressionNode ast = astBuilder.buildAst(expression);
            return extractReferencedFields(ast);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private void collectFields(ExpressionNode node, Set<String> fields) {
        if (node == null) {
            return;
        }
        if (node instanceof VariableNode vn) {
            fields.add(vn.getName());
        } else if (node instanceof FieldAccessNode fan) {
            fields.add(fan.getFullPath());
        } else if (node instanceof BinaryOpNode bon) {
            collectFields(bon.getLeft(), fields);
            collectFields(bon.getRight(), fields);
        } else if (node instanceof UnaryOpNode uon) {
            collectFields(uon.getOperand(), fields);
        }
    }

    @Override
    public Boolean visit(LiteralNode node) {
        // A bare literal at the boolean root expression is not a vectorizable comparison
        return false;
    }

    @Override
    public Boolean visit(VariableNode node) {
        // A bare variable at the boolean root expression is not a vectorizable comparison
        return false;
    }

    @Override
    public Boolean visit(BinaryOpNode node) {
        if (node == null) {
            return false;
        }
        BinaryOpNode.Operator op = node.getOperator();

        // 1. Logical connectives (AND, OR)
        if (op == BinaryOpNode.Operator.AND || op == BinaryOpNode.Operator.OR) {
            Boolean leftOk = node.getLeft() != null && node.getLeft().accept(this);
            Boolean rightOk = node.getRight() != null && node.getRight().accept(this);
            return Boolean.TRUE.equals(leftOk) && Boolean.TRUE.equals(rightOk);
        }

        // 2. Relational and equality comparisons
        if (isComparisonOperator(op)) {
            Map<String, Class<?>> schema = currentSchema.get();
            boolean leftNumeric = isNumericSubexpression(node.getLeft(), schema);
            boolean rightNumeric = isNumericSubexpression(node.getRight(), schema);
            return leftNumeric && rightNumeric;
        }

        // 3. Arithmetic operations at root level do not produce a boolean predicate
        return false;
    }

    @Override
    public Boolean visit(UnaryOpNode node) {
        if (node == null || node.getOperand() == null) {
            return false;
        }
        if (node.getOperator() == UnaryOpNode.Operator.NOT) {
            return Boolean.TRUE.equals(node.getOperand().accept(this));
        }
        // Unary NEGATE at boolean root is not a boolean predicate
        return false;
    }

    @Override
    public Boolean visit(MethodCallNode node) {
        // Method calls are strictly non-vectorizable
        return false;
    }

    @Override
    public Boolean visit(FunctionCallNode node) {
        // Custom DSL functions and function calls are strictly non-vectorizable
        return false;
    }

    @Override
    public Boolean visit(FieldAccessNode node) {
        // A bare field access at the boolean root is not a boolean predicate
        return false;
    }

    @Override
    public Boolean visit(OnnxInferenceNode node) {
        // ML model inference nodes are strictly non-vectorizable
        return false;
    }

    @Override
    public Boolean visit(ComparisonNode node) {
        return visit((BinaryOpNode) node);
    }

    @Override
    public Boolean visit(BinaryExpressionNode node) {
        return visit((BinaryOpNode) node);
    }

    private boolean isComparisonOperator(BinaryOpNode.Operator op) {
        return op == BinaryOpNode.Operator.GREATER_THAN
                || op == BinaryOpNode.Operator.GREATER_EQUAL
                || op == BinaryOpNode.Operator.LESS_THAN
                || op == BinaryOpNode.Operator.LESS_EQUAL
                || op == BinaryOpNode.Operator.EQUAL
                || op == BinaryOpNode.Operator.NOT_EQUAL;
    }

    private boolean isNumericSubexpression(ExpressionNode node, Map<String, Class<?>> schema) {
        if (node == null) {
            return false;
        }

        // 1. Literal numbers
        if (node instanceof LiteralNode ln) {
            Object val = ln.getValue();
            return val instanceof Number;
        }

        // 2. Variables
        if (node instanceof VariableNode vn) {
            if (schema != null && schema.containsKey(vn.getName())) {
                Class<?> type = schema.get(vn.getName());
                return isNumericType(type);
            }
            // Without explicit schema typing, assume double in numeric comparison context
            return true;
        }

        // 3. Field access chains (e.g. ctx.amount)
        if (node instanceof FieldAccessNode fan) {
            if (!isValidFieldAccessTarget(fan.getTarget())) {
                return false;
            }
            if (schema != null && schema.containsKey(fan.getFullPath())) {
                Class<?> type = schema.get(fan.getFullPath());
                return isNumericType(type);
            }
            return true;
        }

        // 4. Arithmetic binary expressions (e.g. amount * 1.05, a + b)
        if (node instanceof BinaryOpNode bon) {
            BinaryOpNode.Operator op = bon.getOperator();
            if (isArithmeticOperator(op)) {
                return isNumericSubexpression(bon.getLeft(), schema)
                        && isNumericSubexpression(bon.getRight(), schema);
            }
            return false;
        }

        // 5. Unary negation (e.g. -riskScore)
        if (node instanceof UnaryOpNode uon) {
            if (uon.getOperator() == UnaryOpNode.Operator.NEGATE) {
                return isNumericSubexpression(uon.getOperand(), schema);
            }
            return false;
        }

        // All other node types (MethodCallNode, OnnxInferenceNode, FunctionCallNode, etc.) are non-numeric
        return false;
    }

    private boolean isArithmeticOperator(BinaryOpNode.Operator op) {
        return op == BinaryOpNode.Operator.ADD
                || op == BinaryOpNode.Operator.SUBTRACT
                || op == BinaryOpNode.Operator.MULTIPLY
                || op == BinaryOpNode.Operator.DIVIDE
                || op == BinaryOpNode.Operator.MODULO;
    }

    private boolean isValidFieldAccessTarget(ExpressionNode target) {
        if (target instanceof VariableNode) {
            return true;
        }
        if (target instanceof FieldAccessNode fan) {
            return isValidFieldAccessTarget(fan.getTarget());
        }
        return false;
    }

    private boolean isNumericType(Class<?> type) {
        if (type == null) {
            return false;
        }
        if (Number.class.isAssignableFrom(type)) {
            return true;
        }
        return type == double.class || type == float.class
                || type == int.class || type == long.class
                || type == short.class || type == byte.class;
    }
}
