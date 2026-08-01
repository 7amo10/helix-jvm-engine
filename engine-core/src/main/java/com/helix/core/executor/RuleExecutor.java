package com.helix.core.executor;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.RuleExecutionException;

/**
 * Interface defining execution contract for compiled rules.
 */
public interface RuleExecutor {

    /**
     * Executes a compiled rule synchronously using the provided context.
     *
     * @param compiledRule rule instance to execute
     * @param context      execution context containing variable bindings
     * @return ExecutionResult containing output value or error details
     * @throws RuleExecutionException if execution fails unexpectedly
     */
    ExecutionResult execute(CompiledRule compiledRule, ExecutionContext context) throws RuleExecutionException;

    /**
     * Retrieves execution metrics for this executor instance.
     *
     * @return ExecutorMetrics tracking counters and execution timing
     */
    ExecutorMetrics getMetrics();
}
