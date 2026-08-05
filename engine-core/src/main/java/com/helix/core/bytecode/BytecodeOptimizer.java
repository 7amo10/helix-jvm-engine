package com.helix.core.bytecode;

import com.helix.core.parser.ast.ExpressionNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Bytecode &amp; AST Optimizer engine orchestrating constant folding and dead code elimination passes.
 * Can be enabled or disabled dynamically.
 */
public class BytecodeOptimizer {

    private static final Logger log = LoggerFactory.getLogger(BytecodeOptimizer.class);
    private static final int MAX_OPTIMIZATION_PASSES = 5;

    private boolean enabled;
    private final ConstantFolder constantFolder;
    private final DeadCodeEliminator deadCodeEliminator;

    public BytecodeOptimizer() {
        this(true);
    }

    public BytecodeOptimizer(boolean enabled) {
        this.enabled = enabled;
        this.constantFolder = new ConstantFolder();
        this.deadCodeEliminator = new DeadCodeEliminator();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Optimizes the given AST root expression tree through iterative constant folding and dead code elimination passes.
     *
     * @param astRoot raw AST root
     * @return optimized AST root (or original if disabled/null)
     */
    public ExpressionNode optimize(ExpressionNode astRoot) {
        if (!enabled || astRoot == null) {
            return astRoot;
        }

        ExpressionNode current = astRoot;
        for (int pass = 1; pass <= MAX_OPTIMIZATION_PASSES; pass++) {
            ExpressionNode folded = constantFolder.fold(current);
            ExpressionNode eliminated = deadCodeEliminator.eliminate(folded);

            if (Objects.equals(current, eliminated)) {
                log.debug("AST optimization converged in pass {}", pass);
                break;
            }
            current = eliminated;
        }

        return current;
    }
}
