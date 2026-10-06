package com.helix.core.sandbox;

import com.helix.api.sandbox.CapabilityViolationException;
import com.helix.core.parser.ast.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CapabilityPolicyEnforcer Specification")
class CapabilityPolicyEnforcerTest {

    private CapabilityPolicyEnforcer enforcer;

    @BeforeEach
    void setUp() {
        enforcer = new CapabilityPolicyEnforcer(new DefaultCapabilityPolicy());
    }

    @Nested
    @DisplayName("Safe Expressions")
    class SafeExpressionTests {

        @Test
        @DisplayName("Allows simple safe comparison expressions")
        void testAllowsSafeComparison() {
            // amount > 1000
            ComparisonNode node = new ComparisonNode(
                    BinaryOpNode.Operator.GREATER_THAN,
                    new VariableNode("amount"),
                    new LiteralNode(1000)
            );
            assertDoesNotThrow(() -> enforcer.enforce(node));
        }

        @Test
        @DisplayName("Allows safe String method calls")
        void testAllowsSafeStringMethodCalls() {
            // name.equals("admin")
            MethodCallNode node = new MethodCallNode(
                    new VariableNode("name"),
                    "equals",
                    List.of(new LiteralNode("admin"))
            );
            assertDoesNotThrow(() -> enforcer.enforce(node));
        }

        @Test
        @DisplayName("Allows safe Math method calls")
        void testAllowsSafeMathMethodCalls() {
            // Math.abs(diff)
            MethodCallNode node = new MethodCallNode(
                    new VariableNode("java.lang.Math"),
                    "abs",
                    List.of(new VariableNode("diff"))
            );
            assertDoesNotThrow(() -> enforcer.enforce(node));
        }

        @Test
        @DisplayName("Allows safe compound logical expressions")
        void testAllowsSafeCompoundExpressions() {
            // (amount > 500 && status.equals("ACTIVE")) || Math.abs(risk) < 0.2
            ExpressionNode leftComparison = new ComparisonNode(
                    BinaryOpNode.Operator.GREATER_THAN,
                    new VariableNode("amount"),
                    new LiteralNode(500)
            );
            ExpressionNode statusCheck = new MethodCallNode(
                    new VariableNode("status"),
                    "equals",
                    List.of(new LiteralNode("ACTIVE"))
            );
            ExpressionNode left = new BinaryExpressionNode(
                    BinaryOpNode.Operator.AND,
                    leftComparison,
                    statusCheck
            );

            ExpressionNode mathCall = new MethodCallNode(
                    new VariableNode("Math"),
                    "abs",
                    List.of(new VariableNode("risk"))
            );
            ExpressionNode right = new ComparisonNode(
                    BinaryOpNode.Operator.LESS_THAN,
                    mathCall,
                    new LiteralNode(0.2)
            );

            ExpressionNode root = new BinaryExpressionNode(
                    BinaryOpNode.Operator.OR,
                    left,
                    right
            );

            assertDoesNotThrow(() -> enforcer.enforce(root));
        }

        @Test
        @DisplayName("Enforces null node safely without throwing exception")
        void testNullNodeDoesNotThrow() {
            assertDoesNotThrow(() -> enforcer.enforce(null));
        }
    }

    @Nested
    @DisplayName("Forbidden Expressions")
    class ForbiddenExpressionTests {

        @Test
        @DisplayName("Rejects direct System.exit invocation")
        void testRejectsSystemExit() {
            MethodCallNode exitCall = new MethodCallNode(
                    new VariableNode("java.lang.System"),
                    "exit",
                    List.of(new LiteralNode(0))
            );

            CapabilityViolationException ex = assertThrows(
                    CapabilityViolationException.class,
                    () -> enforcer.enforce(exitCall)
            );
            assertEquals("java.lang.System", ex.getViolatingClass());
            assertEquals("exit", ex.getViolatingMethod());
        }

        @Test
        @DisplayName("Rejects short unqualified System.exit invocation")
        void testRejectsUnqualifiedSystemExit() {
            MethodCallNode exitCall = new MethodCallNode(
                    new VariableNode("System"),
                    "exit",
                    List.of(new LiteralNode(1))
            );

            CapabilityViolationException ex = assertThrows(
                    CapabilityViolationException.class,
                    () -> enforcer.enforce(exitCall)
            );
            assertTrue(ex.getViolatingClass().contains("System"));
            assertEquals("exit", ex.getViolatingMethod());
        }

        @Test
        @DisplayName("Rejects Runtime.exec invocation")
        void testRejectsRuntimeExec() {
            MethodCallNode execCall = new MethodCallNode(
                    new VariableNode("java.lang.Runtime"),
                    "exec",
                    List.of(new LiteralNode("rm -rf /"))
            );

            CapabilityViolationException ex = assertThrows(
                    CapabilityViolationException.class,
                    () -> enforcer.enforce(execCall)
            );
            assertEquals("java.lang.Runtime", ex.getViolatingClass());
            assertEquals("exec", ex.getViolatingMethod());
        }

        @Test
        @DisplayName("Rejects forbidden calls nested inside deep binary expressions")
        void testRejectsNestedInBinaryExpression() {
            // amount > 100 && (user == "guest" || Runtime.getRuntime())
            MethodCallNode runtimeCall = new MethodCallNode(
                    new VariableNode("Runtime"),
                    "getRuntime",
                    List.of()
            );

            ExpressionNode rightOr = new BinaryExpressionNode(
                    BinaryOpNode.Operator.OR,
                    new ComparisonNode(
                            BinaryOpNode.Operator.EQUAL,
                            new VariableNode("user"),
                            new LiteralNode("guest")
                    ),
                    runtimeCall
            );

            ExpressionNode root = new BinaryExpressionNode(
                    BinaryOpNode.Operator.AND,
                    new ComparisonNode(
                            BinaryOpNode.Operator.GREATER_THAN,
                            new VariableNode("amount"),
                            new LiteralNode(100)
                    ),
                    rightOr
            );

            assertThrows(CapabilityViolationException.class, () -> enforcer.enforce(root));
        }

        @Test
        @DisplayName("Rejects forbidden calls inside unary operations")
        void testRejectsInsideUnaryOp() {
            // !(System.exit(0))
            MethodCallNode exitCall = new MethodCallNode(
                    new VariableNode("System"),
                    "exit",
                    List.of(new LiteralNode(0))
            );
            UnaryOpNode unary = new UnaryOpNode(UnaryOpNode.Operator.NOT, exitCall);

            assertThrows(CapabilityViolationException.class, () -> enforcer.enforce(unary));
        }

        @Test
        @DisplayName("Rejects forbidden calls nested inside arguments of safe methods")
        void testRejectsInsideArgumentList() {
            // Math.max(1, Runtime.getRuntime())
            MethodCallNode runtimeCall = new MethodCallNode(
                    new VariableNode("Runtime"),
                    "getRuntime",
                    List.of()
            );
            MethodCallNode mathMax = new MethodCallNode(
                    new VariableNode("Math"),
                    "max",
                    List.of(new LiteralNode(1), runtimeCall)
            );

            assertThrows(CapabilityViolationException.class, () -> enforcer.enforce(mathMax));
        }

        @Test
        @DisplayName("Rejects fully qualified package traversal via FieldAccessNode chain")
        void testRejectsFieldAccessChain() {
            // java.lang.ProcessBuilder.start()
            FieldAccessNode javaLang = new FieldAccessNode(new VariableNode("java"), "lang");
            FieldAccessNode javaLangPb = new FieldAccessNode(javaLang, "ProcessBuilder");
            MethodCallNode startCall = new MethodCallNode(javaLangPb, "start", List.of());

            assertThrows(CapabilityViolationException.class, () -> enforcer.enforce(startCall));
        }
    }
}
