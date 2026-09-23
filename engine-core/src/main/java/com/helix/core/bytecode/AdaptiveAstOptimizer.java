package com.helix.core.bytecode;

import com.helix.api.CompiledRule;
import com.helix.api.Rule;
import com.helix.core.cache.CacheKey;
import com.helix.core.cache.TieredRuleCache;
import com.helix.core.parser.ast.BinaryOpNode;
import com.helix.core.parser.ast.ExpressionNode;
import com.helix.core.parser.ast.LiteralNode;
import com.helix.core.parser.ast.OnnxInferenceNode;
import com.helix.core.parser.ast.UnaryOpNode;
import com.helix.profiler.node.AstNodeProfiler;
import com.helix.profiler.node.NodeStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helix.core.reorder.ReorderingPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Adaptive AST Optimizer dynamically reordering commutative boolean clauses
 * based on live cost-to-failure ratios (C_i / F_i) or pluggable ReorderingPolicy SPI,
 * and executing zero-downtime bytecode hot-swapping into tiered rule caches.
 *
 * <p>In short-circuiting conjunctive queries ({@code AND} chains), evaluating clauses with the lowest
 * {@code C_i / F_i} ratio first minimizes the expected total evaluation cost by maximizing the probability
 * of early short-circuiting prior to evaluating expensive operations such as ML inferences.</p>
 */
public class AdaptiveAstOptimizer {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveAstOptimizer.class);

    private static final double DEFAULT_ML_RATIO = 100_000.0;
    private static final double DEFAULT_CHEAP_RATIO = 50.0;

    private final ReorderingPolicy reorderingPolicy;
    private final BiFunction<String, ExpressionNode, Double> customRatioResolver;
    private final BytecodeOptimizer staticOptimizer;

    public AdaptiveAstOptimizer() {
        this((ReorderingPolicy) null);
    }

    public AdaptiveAstOptimizer(ReorderingPolicy reorderingPolicy) {
        this.reorderingPolicy = reorderingPolicy;
        this.customRatioResolver = null;
        this.staticOptimizer = new BytecodeOptimizer(true);
    }

    public AdaptiveAstOptimizer(BiFunction<String, ExpressionNode, Double> customRatioResolver) {
        this.customRatioResolver = customRatioResolver;
        this.reorderingPolicy = null;
        this.staticOptimizer = new BytecodeOptimizer(true);
    }

    public ReorderingPolicy getReorderingPolicy() {
        return reorderingPolicy;
    }

    /**
     * Optimizes the AST root for an unnamed or ad-hoc rule.
     *
     * @param astRoot input AST root
     * @return optimized AST root
     */
    public ExpressionNode optimize(ExpressionNode astRoot) {
        return optimize("unknown", astRoot);
    }

    /**
     * Recursively optimizes the AST expression tree by reordering commutative {@code AND} chains
     * in ascending order of their cost-to-failure ratios.
     *
     * @param ruleName name of the rule
     * @param astRoot  input AST root
     * @return reordered and optimized AST root
     */
    public ExpressionNode optimize(String ruleName, ExpressionNode astRoot) {
        if (astRoot == null) {
            return null;
        }

        return reorderNode(ruleName != null ? ruleName : "unknown", astRoot);
    }

    private ExpressionNode reorderNode(String ruleName, ExpressionNode node) {
        if (node instanceof BinaryOpNode b) {
            if (b.getOperator() == BinaryOpNode.Operator.AND) {
                return reorderAndChain(ruleName, b);
            } else if (b.getOperator() == BinaryOpNode.Operator.OR) {
                // Recursively optimize OR operands while preserving structure
                ExpressionNode optLeft = reorderNode(ruleName, b.getLeft());
                ExpressionNode optRight = reorderNode(ruleName, b.getRight());
                if (Objects.equals(b.getLeft(), optLeft) && Objects.equals(b.getRight(), optRight)) {
                    return b;
                }
                return new BinaryOpNode(b.getOperator(), optLeft, optRight);
            }
            return node;
        }

        if (node instanceof UnaryOpNode u) {
            ExpressionNode optOperand = reorderNode(ruleName, u.getOperand());
            if (Objects.equals(u.getOperand(), optOperand)) {
                return u;
            }
            return new UnaryOpNode(u.getOperator(), optOperand);
        }

        return node;
    }

    /**
     * Flattens and reorders a contiguous {@code AND} operation chain ascending by ratio.
     *
     * @param ruleName rule name
     * @param andRoot  root of the AND operation
     * @return reordered binary AND tree (or original instance if order unchanged)
     */
    private ExpressionNode reorderAndChain(String ruleName, BinaryOpNode andRoot) {
        List<ExpressionNode> clauses = new ArrayList<>();
        collectAndClauses(andRoot, clauses);

        if (clauses.size() <= 1) {
            return andRoot;
        }

        // Recursively optimize each child clause first
        List<ExpressionNode> optimizedClauses = new ArrayList<>(clauses.size());
        boolean anyChildChanged = false;
        for (ExpressionNode clause : clauses) {
            ExpressionNode opt = reorderNode(ruleName, clause);
            if (!Objects.equals(clause, opt)) {
                anyChildChanged = true;
            }
            optimizedClauses.add(opt);
        }

        List<ExpressionNode> sortedClauses;
        if (reorderingPolicy != null) {
            List<NodeStats> statsList = new ArrayList<>(optimizedClauses.size());
            for (ExpressionNode clause : optimizedClauses) {
                String nodeId = AstNodeIdResolver.resolveNodeId(ruleName, clause);
                NodeStats stats = AstNodeProfiler.getNodeStats(nodeId)
                        .orElseGet(() -> new NodeStats(ruleName != null ? ruleName : "unknown", nodeId));
                statsList.add(stats);
            }
            List<Integer> order = reorderingPolicy.determineOrder(statsList);
            sortedClauses = new ArrayList<>(optimizedClauses.size());
            boolean[] used = new boolean[optimizedClauses.size()];
            if (order != null) {
                for (int idx : order) {
                    if (idx >= 0 && idx < optimizedClauses.size() && !used[idx]) {
                        sortedClauses.add(optimizedClauses.get(idx));
                        used[idx] = true;
                    }
                }
            }
            for (int i = 0; i < optimizedClauses.size(); i++) {
                if (!used[i]) {
                    sortedClauses.add(optimizedClauses.get(i));
                }
            }
        } else {
            // Sort clauses ascending by cost-to-failure ratio (stable sort)
            sortedClauses = new ArrayList<>(optimizedClauses);
            sortedClauses.sort(Comparator.comparingDouble(clause -> computeRatio(ruleName, clause)));
        }

        boolean orderChanged = false;
        for (int i = 0; i < clauses.size(); i++) {
            if (!Objects.equals(clauses.get(i), sortedClauses.get(i))) {
                orderChanged = true;
                break;
            }
        }

        if (!orderChanged && !anyChildChanged) {
            return andRoot;
        }

        // Reconstruct left-associative AND tree
        ExpressionNode result = sortedClauses.get(0);
        for (int i = 1; i < sortedClauses.size(); i++) {
            result = new BinaryOpNode(BinaryOpNode.Operator.AND, result, sortedClauses.get(i));
        }

        return result;
    }

    private void collectAndClauses(ExpressionNode node, List<ExpressionNode> clauses) {
        if (node instanceof BinaryOpNode b && b.getOperator() == BinaryOpNode.Operator.AND) {
            collectAndClauses(b.getLeft(), clauses);
            collectAndClauses(b.getRight(), clauses);
        } else {
            clauses.add(node);
        }
    }

    /**
     * Computes the cost-to-failure ratio (C_i / F_i) for a given clause node.
     *
     * @param ruleName rule name
     * @param node     clause node
     * @return ratio value (lower values indicate faster, higher-failure clauses that should evaluate first)
     */
    public double computeRatio(String ruleName, ExpressionNode node) {
        if (customRatioResolver != null) {
            return customRatioResolver.apply(ruleName, node);
        }

        // Constant literal evaluations
        if (node instanceof LiteralNode lit && lit.getValue() instanceof Boolean b) {
            return b ? Double.POSITIVE_INFINITY : 0.0;
        }

        String nodeId = AstNodeIdResolver.resolveNodeId(ruleName, node);
        Optional<NodeStats> statsOpt = AstNodeProfiler.getNodeStats(nodeId);

        if (statsOpt.isPresent() && statsOpt.get().getExecutionCount() > 0) {
            NodeStats stats = statsOpt.get();
            double fRate = stats.getFailureRate();
            if (fRate > 0.0) {
                return stats.getCostToFailureRatio();
            }
            // If empirical failure rate is 0 because the node was only evaluated on passing subsets
            // downstream of earlier short-circuiting clauses, apply an optimistic prior (0.50)
            // to avoid short-circuit blindness and allow cheap checks to be evaluated first.
            return stats.getAverageCostNanos() / 0.50;
        }

        // Heuristic fallback when empirical telemetry is not yet recorded
        if (containsMlInference(node)) {
            return DEFAULT_ML_RATIO;
        }
        return DEFAULT_CHEAP_RATIO;
    }

    private boolean containsMlInference(ExpressionNode node) {
        if (node instanceof OnnxInferenceNode) {
            return true;
        }
        if (node instanceof BinaryOpNode b) {
            return containsMlInference(b.getLeft()) || containsMlInference(b.getRight());
        }
        if (node instanceof UnaryOpNode u) {
            return containsMlInference(u.getOperand());
        }
        return false;
    }

    /**
     * Determines whether reordering produced a different, structurally beneficial AST tree.
     *
     * @param originalAst  original AST
     * @param optimizedAst optimized AST
     * @return true if the AST order was changed
     */
    public boolean isReorderBeneficial(ExpressionNode originalAst, ExpressionNode optimizedAst) {
        return !Objects.equals(originalAst, optimizedAst);
    }

    /**
     * Dynamically optimizes an uncompiled AST, recompiles the optimized bytecode,
     * and atomically hot-swaps it into the tiered cache with zero downtime.
     *
     * @param rule    rule definition metadata
     * @param astRoot original parsed AST root
     * @param cache   active tiered rule cache
     * @return optimization result metadata
     * @throws Exception if bytecode compilation or hot-swapping fails
     */
    public OptimizationResult optimizeAndHotSwap(Rule rule, ExpressionNode astRoot, TieredRuleCache cache) throws Exception {
        return optimizeAndHotSwap(rule, astRoot, cache, new AsmBytecodeGenerator(true), false);
    }

    /**
     * Dynamically optimizes an uncompiled AST, recompiles the optimized bytecode,
     * and atomically hot-swaps it into the tiered cache with zero downtime.
     *
     * @param rule      rule definition metadata
     * @param astRoot   original parsed AST root
     * @param cache     active tiered rule cache
     * @param generator bytecode generator to use for compilation
     * @param force     whether to recompile and hot-swap even if clause order did not change
     * @return optimization result metadata
     * @throws Exception if bytecode compilation or hot-swapping fails
     */
    public OptimizationResult optimizeAndHotSwap(Rule rule, ExpressionNode astRoot, TieredRuleCache cache,
                                                 AsmBytecodeGenerator generator, boolean force) throws Exception {
        Objects.requireNonNull(rule, "rule cannot be null");
        Objects.requireNonNull(astRoot, "astRoot cannot be null");
        Objects.requireNonNull(cache, "cache cannot be null");
        Objects.requireNonNull(generator, "generator cannot be null");

        String ruleName = rule.getName();
        ExpressionNode optimizedAst = optimize(ruleName, astRoot);
        boolean reordered = isReorderBeneficial(astRoot, optimizedAst);

        // Collect clause ratio map for telemetry
        Map<String, Double> clauseRatios = new LinkedHashMap<>();
        List<ExpressionNode> clauses = new ArrayList<>();
        collectAndClauses(optimizedAst, clauses);
        for (ExpressionNode c : clauses) {
            String id = AstNodeIdResolver.resolveNodeId(ruleName, c);
            clauseRatios.put(id, computeRatio(ruleName, c));
        }

        CompiledRule compiledRule;
        byte[] bytecode;

        if (reordered || force) {
            log.info("Rule '{}' AST reordered based on cost-to-failure ratios: {}. Recompiling and hot-swapping...",
                    ruleName, clauseRatios);

            bytecode = generator.generateBytecode(rule, optimizedAst);
            compiledRule = generator.generate(rule, optimizedAst);

            CacheKey key = new CacheKey(rule);
            cache.hotSwap(key, compiledRule, bytecode);

            log.info("Rule '{}' successfully hot-swapped into TieredRuleCache with zero downtime.", ruleName);
        } else {
            log.debug("Rule '{}' AST already in optimal order; hot-swap skipped.", ruleName);
            CacheKey key = new CacheKey(rule);
            compiledRule = cache.get(key).orElse(null);
            if (compiledRule == null) {
                bytecode = generator.generateBytecode(rule, optimizedAst);
                compiledRule = generator.generate(rule, optimizedAst);
                cache.hotSwap(key, compiledRule, bytecode);
            } else {
                bytecode = null;
            }
        }

        return new OptimizationResult(
                ruleName,
                reordered,
                astRoot,
                optimizedAst,
                compiledRule,
                bytecode,
                clauseRatios,
                System.nanoTime()
        );
    }
}
