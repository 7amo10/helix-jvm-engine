package com.helix.core.bytecode;

import com.helix.core.parser.ast.AstBuilder;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.LiteralNode;
import com.helix.core.parser.ast.VariableNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BytecodeOptimizerTest {

    private AstBuilder astBuilder;
    private BytecodeOptimizer optimizer;

    @BeforeEach
    void setUp() {
        astBuilder = new AstBuilder();
        optimizer = new BytecodeOptimizer();
    }

    @Test
    @DisplayName("Should fold numeric addition (2 + 3 -> 5)")
    void testConstantFoldingAddition() throws Exception {
        ExpressionNode ast = astBuilder.buildAst("2 + 3");
        ExpressionNode optimized = optimizer.optimize(ast);

        assertTrue(optimized instanceof LiteralNode);
        assertEquals(5L, ((LiteralNode) optimized).getValue());
    }

    @Test
    @DisplayName("Should fold string concatenation (\"hello \" + \"world\")")
    void testConstantFoldingStringConcat() throws Exception {
        ExpressionNode ast = astBuilder.buildAst("\"hello \" + \"world\"");
        ExpressionNode optimized = optimizer.optimize(ast);

        assertTrue(optimized instanceof LiteralNode);
        assertEquals("hello world", ((LiteralNode) optimized).getValue());
    }

    @Test
    @DisplayName("Should fold comparison (10 > 5 -> true)")
    void testConstantFoldingComparison() throws Exception {
        ExpressionNode ast = astBuilder.buildAst("10 > 5");
        ExpressionNode optimized = optimizer.optimize(ast);

        assertTrue(optimized instanceof LiteralNode);
        assertEquals(Boolean.TRUE, ((LiteralNode) optimized).getValue());
    }

    @Test
    @DisplayName("Should eliminate dead code (false && x > 10 -> false)")
    void testDeadCodeEliminationFalseAnd() throws Exception {
        ExpressionNode ast = astBuilder.buildAst("false && x > 10");
        ExpressionNode optimized = optimizer.optimize(ast);

        assertTrue(optimized instanceof LiteralNode);
        assertEquals(Boolean.FALSE, ((LiteralNode) optimized).getValue());
    }

    @Test
    @DisplayName("Should eliminate dead code (true || y < 5 -> true)")
    void testDeadCodeEliminationTrueOr() throws Exception {
        ExpressionNode ast = astBuilder.buildAst("true || y < 5");
        ExpressionNode optimized = optimizer.optimize(ast);

        assertTrue(optimized instanceof LiteralNode);
        assertEquals(Boolean.TRUE, ((LiteralNode) optimized).getValue());
    }

    @Test
    @DisplayName("Should simplify logical term (true && x > 10 -> x > 10)")
    void testDeadCodeEliminationTrueAnd() throws Exception {
        ExpressionNode ast = astBuilder.buildAst("true && x > 10");
        ExpressionNode optimized = optimizer.optimize(ast);

        assertFalse(optimized instanceof LiteralNode);
    }

    @Test
    @DisplayName("Should return unchanged AST when optimizer is disabled")
    void testDisabledOptimizer() throws Exception {
        optimizer.setEnabled(false);
        ExpressionNode ast = astBuilder.buildAst("2 + 3");
        ExpressionNode optimized = optimizer.optimize(ast);

        assertEquals(ast, optimized);
    }
}
