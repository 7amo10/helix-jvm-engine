package com.helix.core.bytecode;

import com.helix.api.CompiledRule;
import com.helix.core.parser.ast.ExpressionNode;

import java.util.Map;

/**
 * Immutable snapshot of an adaptive AST optimization and hot-swap operation.
 *
 * @param ruleName       name of the optimized rule
 * @param reordered      whether AST clause ordering was modified
 * @param originalAst    original unoptimized AST root
 * @param optimizedAst   reordered and optimized AST root
 * @param compiledRule   newly compiled executable rule instance
 * @param bytecode       raw class bytes of the compiled rule
 * @param clauseRatios   computed cost-to-failure ratios for evaluated clauses
 * @param timestampNanos timestamp when the optimization occurred
 */
public record OptimizationResult(
        String ruleName,
        boolean reordered,
        ExpressionNode originalAst,
        ExpressionNode optimizedAst,
        CompiledRule compiledRule,
        byte[] bytecode,
        Map<String, Double> clauseRatios,
        long timestampNanos
) {
}
