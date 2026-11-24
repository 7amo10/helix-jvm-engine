package com.helix.core.executor;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.core.batch.BatchDispatcher;

import java.util.List;
import java.util.Objects;

/**
 * Parallel batch rule executor for bulk context processing using parallel streams and configurable parallelism.
 * Integrates transparently with {@link BatchDispatcher} for SIMD vector-accelerated batch evaluation.
 */
public class BatchExecutor {

    private final SyncExecutor syncExecutor;
    private final int parallelism;
    private final BatchDispatcher batchDispatcher;

    public BatchExecutor() {
        this(new SyncExecutor(), Runtime.getRuntime().availableProcessors());
    }

    public BatchExecutor(int parallelism) {
        this(new SyncExecutor(), parallelism);
    }

    public BatchExecutor(SyncExecutor syncExecutor, int parallelism) {
        this(syncExecutor, parallelism, new BatchDispatcher(syncExecutor, parallelism));
    }

    public BatchExecutor(SyncExecutor syncExecutor, int parallelism, BatchDispatcher batchDispatcher) {
        this.syncExecutor = Objects.requireNonNull(syncExecutor, "syncExecutor cannot be null");
        this.parallelism = Math.max(1, parallelism);
        this.batchDispatcher = Objects.requireNonNull(batchDispatcher, "batchDispatcher cannot be null");
    }

    /**
     * Executes a compiled rule against a list of execution contexts in parallel.
     *
     * @param compiledRule rule instance
     * @param contexts     list of contexts to process
     * @return List of ExecutionResult objects preserving input order
     */
    public List<ExecutionResult> executeBatch(CompiledRule compiledRule, List<ExecutionContext> contexts) {
        return batchDispatcher.executeBatch(compiledRule, contexts);
    }

    /**
     * Executes a rule definition against a list of execution contexts with dynamic SIMD routing.
     *
     * @param rule     rule definition
     * @param contexts list of contexts to process
     * @return DispatchResult containing outcomes, execution results, and evaluation mode
     */
    public BatchDispatcher.DispatchResult executeBatch(Rule rule, List<ExecutionContext> contexts) {
        return batchDispatcher.dispatch(rule, contexts);
    }

    /**
     * Executes a raw expression string against a list of execution contexts with dynamic SIMD routing.
     *
     * @param expression expression string
     * @param contexts   list of contexts to process
     * @return DispatchResult containing outcomes, execution results, and evaluation mode
     */
    public BatchDispatcher.DispatchResult executeBatch(String expression, List<ExecutionContext> contexts) {
        return batchDispatcher.dispatch(expression, contexts);
    }

    /**
     * Dispatches a compiled rule through the underlying batch dispatcher.
     *
     * @param compiledRule compiled rule instance
     * @param contexts     list of contexts
     * @return DispatchResult instance
     */
    public BatchDispatcher.DispatchResult dispatch(CompiledRule compiledRule, List<ExecutionContext> contexts) {
        return batchDispatcher.dispatch(compiledRule, contexts);
    }

    /**
     * Dispatches a rule definition through the underlying batch dispatcher.
     *
     * @param rule     rule definition
     * @param contexts list of contexts
     * @return DispatchResult instance
     */
    public BatchDispatcher.DispatchResult dispatch(Rule rule, List<ExecutionContext> contexts) {
        return batchDispatcher.dispatch(rule, contexts);
    }

    /**
     * Dispatches a raw expression string through the underlying batch dispatcher.
     *
     * @param expression expression string
     * @param contexts   list of contexts
     * @return DispatchResult instance
     */
    public BatchDispatcher.DispatchResult dispatch(String expression, List<ExecutionContext> contexts) {
        return batchDispatcher.dispatch(expression, contexts);
    }

    public BatchDispatcher getBatchDispatcher() {
        return batchDispatcher;
    }

    public ExecutorMetrics getMetrics() {
        return syncExecutor.getMetrics();
    }

    public int getParallelism() {
        return parallelism;
    }
}
