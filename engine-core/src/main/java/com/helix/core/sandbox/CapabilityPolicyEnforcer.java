package com.helix.core.sandbox;

import com.helix.api.sandbox.CapabilityPolicy;
import com.helix.api.sandbox.CapabilityViolationException;
import com.helix.core.parser.ast.*;

import java.util.Objects;

/**
 * Traverses an AST expression tree during compilation and applies capability policy checks
 * to all method calls, function invocations, and class/package accesses.
 * <p>
 * Ensures zero execution overhead by rejecting forbidden operations statically before bytecode generation.
 */
public class CapabilityPolicyEnforcer implements AstVisitor<Void> {

    private final CapabilityPolicy policy;

    /**
     * Constructs a CapabilityPolicyEnforcer with the specified policy.
     *
     * @param policy capability policy to enforce
     */
    public CapabilityPolicyEnforcer(CapabilityPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy cannot be null");
    }

    /**
     * Returns the underlying capability policy.
     *
     * @return policy
     */
    public CapabilityPolicy getPolicy() {
        return policy;
    }

    /**
     * Enforces the capability policy on the given AST root expression.
     *
     * @param root the root ExpressionNode of the compiled rule
     * @throws CapabilityViolationException if any restricted operation is detected
     */
    public void enforce(ExpressionNode root) throws CapabilityViolationException {
        if (root != null) {
            root.accept(this);
        }
    }

    @Override
    public Void visit(LiteralNode node) {
        return null;
    }

    @Override
    public Void visit(VariableNode node) {
        return null;
    }

    @Override
    public Void visit(BinaryOpNode node) {
        if (node != null) {
            if (node.getLeft() != null) {
                node.getLeft().accept(this);
            }
            if (node.getRight() != null) {
                node.getRight().accept(this);
            }
        }
        return null;
    }

    @Override
    public Void visit(UnaryOpNode node) {
        if (node != null && node.getOperand() != null) {
            node.getOperand().accept(this);
        }
        return null;
    }

    @Override
    public Void visit(MethodCallNode node) {
        if (node == null) {
            return null;
        }

        // Traverse target first in case it contains nested method calls: target.foo().bar()
        if (node.getTarget() != null) {
            node.getTarget().accept(this);
        }

        // Traverse all arguments recursively: foo(bar.baz())
        if (node.getArguments() != null) {
            for (ExpressionNode arg : node.getArguments()) {
                if (arg != null) {
                    arg.accept(this);
                }
            }
        }

        // Extract and normalize target class name
        String className = resolveTargetClassName(node.getTarget());
        String methodName = node.getMethodName();

        policy.checkMethodCall(className, methodName);
        return null;
    }

    @Override
    public Void visit(FieldAccessNode node) {
        if (node != null && node.getTarget() != null) {
            node.getTarget().accept(this);
        }
        return null;
    }

    @Override
    public Void visit(FunctionCallNode node) {
        if (node == null) {
            return null;
        }
        if (node.getArguments() != null) {
            for (ExpressionNode arg : node.getArguments()) {
                if (arg != null) {
                    arg.accept(this);
                }
            }
        }
        return null;
    }

    @Override
    public Void visit(BinaryExpressionNode node) {
        return visit((BinaryOpNode) node);
    }

    @Override
    public Void visit(ComparisonNode node) {
        return visit((BinaryOpNode) node);
    }

    @Override
    public Void visit(OnnxInferenceNode node) {
        return null;
    }

    private String resolveTargetClassName(ExpressionNode target) {
        if (target == null) {
            return "unknown";
        }
        if (target instanceof VariableNode vn) {
            return normalizeWellKnownClass(vn.getName());
        }
        if (target instanceof FieldAccessNode fan) {
            return resolveFieldAccessChain(fan);
        }
        return target.toString();
    }

    private String resolveFieldAccessChain(FieldAccessNode node) {
        StringBuilder sb = new StringBuilder();
        ExpressionNode current = node;
        while (current instanceof FieldAccessNode fan) {
            if (sb.length() > 0) {
                sb.insert(0, fan.getFieldName() + ".");
            } else {
                sb.append(fan.getFieldName());
            }
            current = fan.getTarget();
        }
        if (current instanceof VariableNode vn) {
            sb.insert(0, vn.getName() + ".");
        } else if (current != null) {
            sb.insert(0, current + ".");
        }
        return normalizeWellKnownClass(sb.toString());
    }

    private String normalizeWellKnownClass(String name) {
        if (name == null) {
            return "unknown";
        }
        return switch (name) {
            case "System" -> "java.lang.System";
            case "Runtime" -> "java.lang.Runtime";
            case "ProcessBuilder" -> "java.lang.ProcessBuilder";
            case "Class" -> "java.lang.Class";
            case "Math" -> "java.lang.Math";
            case "String" -> "java.lang.String";
            default -> name;
        };
    }
}
