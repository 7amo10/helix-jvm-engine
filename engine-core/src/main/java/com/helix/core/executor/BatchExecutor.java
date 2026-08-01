package com.helix.core.executor;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.RuleExecutionException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;

/**
 * Parallel batch rule executor for bulk context processing using parallel streams and configurable parallelism.
 */
public class BatchExecutor {

    private final SyncExecutor syncExecutor;
    private final int parallelism;

    public BatchExecutor() {
        this(new SyncExecutor(), Runtime.getRuntime().availableProcessors());
    }

    public BatchExecutor(int parallelism) {
        this(new SyncExecutor(), parallelism);
    }

    public BatchExecutor(SyncExecutor syncExecutor, int parallelism) {
        this.syncExecutor = Objects.requireNonNull(syncExecutor, "syncExecutor cannot be null");
        this.parallelism = Math.max(1, parallelism);
    }

    /**
     * Executes a compiled rule against a list of execution contexts in parallel.
     *
     * @param compiledRule rule instance
     * @param contexts     list of contexts to process
     * @return List of ExecutionResult objects preserving input order
     */
    public List<ExecutionResult> executeBatch(CompiledRule compiledRule, List<ExecutionContext> contexts) {
        Objects.requireNonNull(compiledRule, "compiledRule cannot be null");
        if (contexts == null || contexts.isEmpty()) {
            return List.of();
        }

        ForkJoinPool customPool = new ForkJoinPool(parallelism);
        try {
            return customPool.submit(() ->
                    contexts.parallelStream()
                            .map(ctx -> {
                                try {
                                    return syncExecutor.execute(compiledRule, ctx);
                                } catch (RuleExecutionException e) {
                                    return ExecutionResult.failure(e, 0);
                                }
                            })
                            .toList()
            ).get();
        } catch (Exception e) {
            return contexts.stream()
                    .map(ctx -> ExecutionResult.failure(new RuleExecutionException("Batch execution error: " + e.getMessage(), e), 0))
                    .toList();
        } finally {
            customPool.shutdown();
        }
    }

    public ExecutorMetrics getMetrics() {
        return syncExecutor.getMetrics();
    }

    public int getParallelism() {
        return parallelism;
    }
}
