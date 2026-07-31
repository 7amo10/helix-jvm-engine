package com.helix.core.parser;

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
 * Static AST type checker and type inference engine.
 * Validates operand compatibility, method signature resolution, and symbol table types.
 */
public class TypeChecker implements AstVisitor<Class<?>> {

    private final TypeContext typeContext;

    public TypeChecker(TypeContext typeContext) {
        this.typeContext = Objects.requireNonNull(typeContext, "typeContext cannot be null");
    }

    /**
     * Checks types for the given AST root node and returns the resulting expression type.
     *
     * @param node AST root node
     * @return inferred Java type class
     * @throws TypeMismatchException if type validation fails
     */
    public Class<?> check(ExpressionNode node) throws TypeMismatchException {
        if (node == null) {
            throw new TypeMismatchException("AST root node cannot be null");
        }
        try {
            return node.accept(this);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof TypeMismatchException) {
                throw (TypeMismatchException) e.getCause();
            }
            throw new TypeMismatchException(e.getMessage(), e);
        }
    }

    @Override
    public Class<?> visit(LiteralNode node) {
        return node.getType();
    }

    @Override
    public Class<?> visit(VariableNode node) {
        return typeContext.getVariableType(node.getName())
                .orElseThrow(() -> new RuntimeException(new TypeMismatchException("Undeclared variable: '" + node.getName() + "'")));
    }

    @Override
    public Class<?> visit(UnaryOpNode node) {
        Class<?> operandType = node.getOperand().accept(this);
        return switch (node.getOperator()) {
            case NOT -> {
                if (!Boolean.class.isAssignableFrom(operandType) && !boolean.class.isAssignableFrom(operandType)) {
                    throw new RuntimeException(new TypeMismatchException("Unary '!' requires boolean operand but got: " + operandType.getName()));
                }
                yield Boolean.class;
            }
            case NEGATE -> {
                if (!isNumeric(operandType)) {
                    throw new RuntimeException(new TypeMismatchException("Unary '-' requires numeric operand but got: " + operandType.getName()));
                }
                yield operandType;
            }
        };
    }

    @Override
    public Class<?> visit(BinaryOpNode node) {
        Class<?> leftType = node.getLeft().accept(this);
        Class<?> rightType = node.getRight().accept(this);

        return switch (node.getOperator()) {
            case ADD -> {
                if (String.class.isAssignableFrom(leftType) || String.class.isAssignableFrom(rightType)) {
                    yield String.class;
                }
                if (isNumeric(leftType) && isNumeric(rightType)) {
                    yield promoteNumeric(leftType, rightType);
                }
                throw new RuntimeException(new TypeMismatchException("Operator '+' incompatible between " + leftType.getName() + " and " + rightType.getName()));
            }
            case SUBTRACT, MULTIPLY, DIVIDE -> {
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    throw new RuntimeException(new TypeMismatchException("Operator '" + node.getOperator().getSymbol() + "' requires numeric operands but got " + leftType.getName() + " and " + rightType.getName()));
                }
                yield promoteNumeric(leftType, rightType);
            }
            case GREATER_THAN, GREATER_EQUAL, LESS_THAN, LESS_EQUAL -> {
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    throw new RuntimeException(new TypeMismatchException("Relational operator '" + node.getOperator().getSymbol() + "' requires numeric operands but got " + leftType.getName() + " and " + rightType.getName()));
                }
                yield Boolean.class;
            }
            case EQUAL, NOT_EQUAL -> Boolean.class;
            case AND, OR -> {
                boolean leftBool = Boolean.class.isAssignableFrom(leftType) || boolean.class.isAssignableFrom(leftType);
                boolean rightBool = Boolean.class.isAssignableFrom(rightType) || boolean.class.isAssignableFrom(rightType);
                if (!leftBool || !rightBool) {
                    throw new RuntimeException(new TypeMismatchException("Logical operator '" + node.getOperator().getSymbol() + "' requires boolean operands but got " + leftType.getName() + " and " + rightType.getName()));
                }
                yield Boolean.class;
            }
        };
    }

    @Override
    public Class<?> visit(MethodCallNode node) {
        Class<?> targetType = node.getTarget().accept(this);
        List<Class<?>> argTypes = new ArrayList<>();
        for (ExpressionNode arg : node.getArguments()) {
            argTypes.add(arg.accept(this));
        }

        Method matchedMethod = resolveMethod(targetType, node.getMethodName(), argTypes);
        return matchedMethod.getReturnType();
    }

    private Method resolveMethod(Class<?> targetType, String methodName, List<Class<?>> argTypes) {
        for (Method m : targetType.getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == argTypes.size()) {
                boolean matches = true;
                Class<?>[] paramTypes = m.getParameterTypes();
                for (int i = 0; i < paramTypes.length; i++) {
                    if (!isAssignable(paramTypes[i], argTypes.get(i))) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return m;
                }
            }
        }
        throw new RuntimeException(new TypeMismatchException("No matching method found for " + targetType.getName() + "." + methodName + "(" + argTypes + ")"));
    }

    private boolean isNumeric(Class<?> clazz) {
        return Number.class.isAssignableFrom(clazz) ||
                clazz == int.class || clazz == long.class || clazz == double.class || clazz == float.class;
    }

    private Class<?> promoteNumeric(Class<?> t1, Class<?> t2) {
        if (t1 == Double.class || t1 == double.class || t2 == Double.class || t2 == double.class) {
            return Double.class;
        }
        if (t1 == Float.class || t1 == float.class || t2 == Float.class || t2 == float.class) {
            return Float.class;
        }
        if (t1 == Long.class || t1 == long.class || t2 == Long.class || t2 == long.class) {
            return Long.class;
        }
        return Integer.class;
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
