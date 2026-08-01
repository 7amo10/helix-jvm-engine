package com.helix.core.executor;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.RuleExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Synchronous implementation of {@link RuleExecutor} executing rules in the calling thread.
 */
public class SyncExecutor implements RuleExecutor {

    private static final Logger log = LoggerFactory.getLogger(SyncExecutor.class);
    private final ExecutorMetrics metrics;

    public SyncExecutor() {
        this(new ExecutorMetrics());
    }

    public SyncExecutor(ExecutorMetrics metrics) {
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
    }

    @Override
    public ExecutionResult execute(CompiledRule compiledRule, ExecutionContext context) throws RuleExecutionException {
        Objects.requireNonNull(compiledRule, "compiledRule cannot be null");
        ExecutionContext ctx = context != null ? context : new ExecutionContext();

        long startTime = System.nanoTime();
        try {
            ExecutionResult result = compiledRule.execute(ctx);
            long duration = System.nanoTime() - startTime;
            metrics.recordExecution(result.isSuccess(), duration);
            return result;
        } catch (Throwable t) {
            long duration = System.nanoTime() - startTime;
            metrics.recordExecution(false, duration);
            log.error("Rule execution threw unhandled exception for rule: {}", compiledRule.getName(), t);
            throw new RuleExecutionException("Failed to execute rule '" + compiledRule.getName() + "': " + t.getMessage(), t);
        }
    }

    @Override
    public ExecutorMetrics getMetrics() {
        return metrics;
    }
}
