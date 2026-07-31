package com.helix.core.parser;

import com.helix.core.parser.ast.AstBuilder;
import com.helix.core.parser.ast.ExpressionNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TypeCheckerTest {

    private AstBuilder astBuilder;

    @BeforeEach
    void setUp() {
        astBuilder = new AstBuilder();
    }

    @Test
    @DisplayName("Should infer type of valid arithmetic and relational expression")
    void testNumericTypeCheck() throws Exception {
        TypeContext context = new TypeContext(Map.of("amount", Double.class, "limit", Long.class));
        TypeChecker typeChecker = new TypeChecker(context);

        ExpressionNode ast = astBuilder.buildAst("amount > limit");
        Class<?> resultType = typeChecker.check(ast);
        assertEquals(Boolean.class, resultType);
    }

    @Test
    @DisplayName("Should promote numeric types in arithmetic expressions (Long + Double -> Double)")
    void testNumericPromotion() throws Exception {
        TypeContext context = new TypeContext(Map.of("a", Long.class, "b", Double.class));
        TypeChecker typeChecker = new TypeChecker(context);

        ExpressionNode ast = astBuilder.buildAst("a + b");
        Class<?> resultType = typeChecker.check(ast);
        assertEquals(Double.class, resultType);
    }

    @Test
    @DisplayName("Should resolve method call return type (String.equalsIgnoreCase -> boolean)")
    void testMethodResolution() throws Exception {
        TypeContext context = new TypeContext(Map.of("name", String.class));
        TypeChecker typeChecker = new TypeChecker(context);

        ExpressionNode ast = astBuilder.buildAst("name.equalsIgnoreCase(\"admin\")");
        Class<?> resultType = typeChecker.check(ast);
        assertEquals(boolean.class, resultType);
    }

    @Test
    @DisplayName("Should throw TypeMismatchException for undeclared variable")
    void testUndeclaredVariable() throws Exception {
        TypeContext context = new TypeContext();
        TypeChecker typeChecker = new TypeChecker(context);

        ExpressionNode ast = astBuilder.buildAst("unknownVar > 10");
        assertThrows(TypeMismatchException.class, () -> typeChecker.check(ast));
    }

    @Test
    @DisplayName("Should throw TypeMismatchException for arithmetic on non-numeric types")
    void testInvalidArithmeticTypes() throws Exception {
        TypeContext context = new TypeContext(Map.of("flag", Boolean.class));
        TypeChecker typeChecker = new TypeChecker(context);

        ExpressionNode ast = astBuilder.buildAst("flag * 5");
        assertThrows(TypeMismatchException.class, () -> typeChecker.check(ast));
    }

    @Test
    @DisplayName("Should throw TypeMismatchException for logical operation on non-boolean types")
    void testInvalidLogicalTypes() throws Exception {
        TypeContext context = new TypeContext(Map.of("x", Long.class));
        TypeChecker typeChecker = new TypeChecker(context);

        ExpressionNode ast = astBuilder.buildAst("x && true");
        assertThrows(TypeMismatchException.class, () -> typeChecker.check(ast));
    }

    @Test
    @DisplayName("Should throw TypeMismatchException for non-existent method call")
    void testInvalidMethodCall() throws Exception {
        TypeContext context = new TypeContext(Map.of("name", String.class));
        TypeChecker typeChecker = new TypeChecker(context);

        ExpressionNode ast = astBuilder.buildAst("name.nonExistentMethod()");
        assertThrows(TypeMismatchException.class, () -> typeChecker.check(ast));
    }
}
