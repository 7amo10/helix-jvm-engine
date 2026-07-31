package com.helix.core.bytecode;

import com.helix.api.ExecutionContext;
import com.helix.core.parser.ast.AstVisitor;
import com.helix.core.parser.ast.BinaryOpNode;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.LiteralNode;
import com.helix.core.parser.ast.MethodCallNode;
import com.helix.core.parser.ast.UnaryOpNode;
import com.helix.core.parser.ast.VariableNode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * AST Visitor that evaluates expressions dynamically against an {@link ExecutionContext}.
 */
public class AstEvaluator implements AstVisitor<Object> {

    private final ExecutionContext context;

    public AstEvaluator(ExecutionContext context) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
    }

    public Object evaluate(ExpressionNode node) {
        if (node == null) {
            return null;
        }
        return node.accept(this);
    }

    @Override
    public Object visit(LiteralNode node) {
        return node.getValue();
    }

    @Override
    public Object visit(VariableNode node) {
        return context.getVariable(node.getName())
                .orElseThrow(() -> new IllegalArgumentException("Missing variable in execution context: " + node.getName()));
    }

    @Override
    public Object visit(UnaryOpNode node) {
        Object val = node.getOperand().accept(this);
        return switch (node.getOperator()) {
            case NOT -> !((Boolean) val);
            case NEGATE -> {
                if (val instanceof Double d) yield -d;
                if (val instanceof Float f) yield -f;
                if (val instanceof Long l) yield -l;
                if (val instanceof Integer i) yield -i;
                throw new IllegalArgumentException("Cannot negate non-numeric value: " + val);
            }
        };
    }

    @Override
    public Object visit(BinaryOpNode node) {
        // Short-circuiting logical operations
        if (node.getOperator() == BinaryOpNode.Operator.AND) {
            Boolean leftBool = (Boolean) node.getLeft().accept(this);
            if (!leftBool) return Boolean.FALSE;
            return (Boolean) node.getRight().accept(this);
        }
        if (node.getOperator() == BinaryOpNode.Operator.OR) {
            Boolean leftBool = (Boolean) node.getLeft().accept(this);
            if (leftBool) return Boolean.TRUE;
            return (Boolean) node.getRight().accept(this);
        }

        Object left = node.getLeft().accept(this);
        Object right = node.getRight().accept(this);

        return switch (node.getOperator()) {
            case ADD -> {
                if (left instanceof String || right instanceof String) {
                    yield String.valueOf(left) + String.valueOf(right);
                }
                yield evaluateArithmetic(left, right, "+");
            }
            case SUBTRACT -> evaluateArithmetic(left, right, "-");
            case MULTIPLY -> evaluateArithmetic(left, right, "*");
            case DIVIDE -> evaluateArithmetic(left, right, "/");
            case GREATER_THAN -> compare(left, right) > 0;
            case GREATER_EQUAL -> compare(left, right) >= 0;
            case LESS_THAN -> compare(left, right) < 0;
            case LESS_EQUAL -> compare(left, right) <= 0;
            case EQUAL -> Objects.equals(left, right);
            case NOT_EQUAL -> !Objects.equals(left, right);
            case AND, OR -> throw new IllegalStateException("Handled above");
        };
    }

    @Override
    public Object visit(MethodCallNode node) {
        Object target = node.getTarget().accept(this);
        if (target == null) {
            throw new NullPointerException("Cannot invoke method '" + node.getMethodName() + "' on null target");
        }

        List<Object> args = new ArrayList<>();
        List<Class<?>> argTypes = new ArrayList<>();
        for (ExpressionNode argNode : node.getArguments()) {
            Object argVal = argNode.accept(this);
            args.add(argVal);
            argTypes.add(argVal != null ? argVal.getClass() : Object.class);
        }

        try {
            Method method = resolveMethod(target.getClass(), node.getMethodName(), argTypes);
            return method.invoke(target, args.toArray());
        } catch (Exception e) {
            throw new RuntimeException("Method invocation failed: " + node.getMethodName(), e);
        }
    }

    private Object evaluateArithmetic(Object left, Object right, String op) {
        if (!(left instanceof Number) || !(right instanceof Number)) {
            throw new IllegalArgumentException("Arithmetic operation '" + op + "' requires numeric operands");
        }
        Number n1 = (Number) left;
        Number n2 = (Number) right;

        if (n1 instanceof Double || n2 instanceof Double) {
            return switch (op) {
                case "+" -> n1.doubleValue() + n2.doubleValue();
                case "-" -> n1.doubleValue() - n2.doubleValue();
                case "*" -> n1.doubleValue() * n2.doubleValue();
                case "/" -> n1.doubleValue() / n2.doubleValue();
                default -> throw new IllegalArgumentException("Unknown op: " + op);
            };
        }
        if (n1 instanceof Long || n2 instanceof Long) {
            return switch (op) {
                case "+" -> n1.longValue() + n2.longValue();
                case "-" -> n1.longValue() - n2.longValue();
                case "*" -> n1.longValue() * n2.longValue();
                case "/" -> n1.longValue() / n2.longValue();
                default -> throw new IllegalArgumentException("Unknown op: " + op);
            };
        }
        return switch (op) {
            case "+" -> n1.intValue() + n2.intValue();
            case "-" -> n1.intValue() - n2.intValue();
            case "*" -> n1.intValue() * n2.intValue();
            case "/" -> n1.intValue() / n2.intValue();
            default -> throw new IllegalArgumentException("Unknown op: " + op);
        };
    }

    @SuppressWarnings("unchecked")
    private int compare(Object left, Object right) {
        if (left instanceof Number n1 && right instanceof Number n2) {
            return Double.compare(n1.doubleValue(), n2.doubleValue());
        }
        if (left instanceof Comparable c1 && right instanceof Comparable c2) {
            return c1.compareTo(c2);
        }
        throw new IllegalArgumentException("Cannot compare " + left + " and " + right);
    }

    private Method resolveMethod(Class<?> targetType, String methodName, List<Class<?>> argTypes) throws NoSuchMethodException {
        for (Method m : targetType.getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == argTypes.size()) {
                boolean matches = true;
                Class<?>[] paramTypes = m.getParameterTypes();
                for (int i = 0; i < paramTypes.length; i++) {
                    if (argTypes.get(i) != null && !isAssignable(paramTypes[i], argTypes.get(i))) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return m;
                }
            }
        }
        throw new NoSuchMethodException(targetType.getName() + "." + methodName);
    }

    private boolean isAssignable(Class<?> target, Class<?> source) {
        if (target.isAssignableFrom(source)) return true;
        if (target == Object.class) return true;
        if (target == int.class && source == Integer.class) return true;
        if (target == long.class && source == Long.class) return true;
        if (target == double.class && source == Double.class) return true;
        if (target == boolean.class && source == Boolean.class) return true;
        return false;
    }
}
