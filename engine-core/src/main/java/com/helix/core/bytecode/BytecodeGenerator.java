package com.helix.core.bytecode;

import com.helix.api.CompiledRule;
import com.helix.api.Rule;
import com.helix.core.parser.ast.ExpressionNode;

/**
 * Strategy interface for compiling AST expression trees into executable {@link CompiledRule} instances.
 */
public interface BytecodeGenerator {

    /**
     * Generates an executable {@link CompiledRule} instance from a rule definition and its parsed AST.
     *
     * @param rule    raw rule definition metadata
     * @param astRoot parsed root AST node
     * @return compiled rule instance
     * @throws BytecodeGenerationException if generation fails
     */
    CompiledRule generate(Rule rule, ExpressionNode astRoot) throws BytecodeGenerationException;
}
