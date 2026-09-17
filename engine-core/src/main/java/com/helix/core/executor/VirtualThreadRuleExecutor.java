package com.helix.core.executor;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.RuleExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * High-throughput rule executor backed by Java 21 Project Loom virtual threads and structured concurrency.
 * <p>
 * Provides lightweight concurrency without OS carrier thread starvation and implements fail-fast coordinated
 * fan-out using {@link StructuredTaskScope.ShutdownOnFailure}.
 */
public class VirtualThreadRuleExecutor implements RuleExecutor, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadRuleExecutor.class);
    public static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(30);

    private final ExecutorService virtualThreadExecutor;
    private final Duration defaultTimeout;
    private final VirtualThreadExecutorMetrics metrics;
    private final boolean ownsExecutor;

    public VirtualThreadRuleExecutor() {
        this(DEFAULT_DEADLINE);
    }

    public VirtualThreadRuleExecutor(Duration defaultTimeout) {
        this(defaultTimeout, new VirtualThreadExecutorMetrics());
    }

    public VirtualThreadRuleExecutor(Duration defaultTimeout, VirtualThreadExecutorMetrics metrics) {
        this(createVirtualExecutor(), defaultTimeout, metrics, true);
    }

    public VirtualThreadRuleExecutor(ExecutorService virtualThreadExecutor, Duration defaultTimeout,
                                     VirtualThreadExecutorMetrics metrics) {
        this(virtualThreadExecutor, defaultTimeout, metrics, false);
    }

    private VirtualThreadRuleExecutor(ExecutorService virtualThreadExecutor, Duration defaultTimeout,
                                      VirtualThreadExecutorMetrics metrics, boolean ownsExecutor) {
        this.virtualThreadExecutor = Objects.requireNonNull(virtualThreadExecutor, "virtualThreadExecutor cannot be null");
        this.defaultTimeout = defaultTimeout != null ? defaultTimeout : DEFAULT_DEADLINE;
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
        this.ownsExecutor = ownsExecutor;
    }

    private static ExecutorService createVirtualExecutor() {
        ThreadFactory factory = Thread.ofVirtual().name("helix-vt-worker-", 0).factory();
        return Executors.newThreadPerTaskExecutor(factory);
    }

    @Override
    public ExecutionResult execute(CompiledRule compiledRule, ExecutionContext context) throws RuleExecutionException {
        return execute(compiledRule, context, defaultTimeout);
    }

    /**
     * Executes a compiled rule with an explicit deadline timeout.
     *
     * @param compiledRule rule instance to evaluate
     * @param context      context containing bindings
     * @param timeout      deadline duration (null or zero means no timeout)
     * @return ExecutionResult containing output or error
     * @throws RuleExecutionException if execution fails, times out, or is interrupted
     */
    public ExecutionResult execute(CompiledRule compiledRule, ExecutionContext context, Duration timeout)
            throws RuleExecutionException {
        Objects.requireNonNull(compiledRule, "compiledRule cannot be null");
        ExecutionContext ctx = context != null ? context : new ExecutionContext();
        long startNanos = System.nanoTime();
        metrics.taskStarted();

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            StructuredTaskScope.Subtask<ExecutionResult> subtask = scope.fork(() -> {
                long taskStart = System.nanoTime();
                try {
                    ExecutionResult result = compiledRule.execute(ctx);
                    metrics.recordExecution(result.isSuccess(), System.nanoTime() - taskStart);
                    return result;
                } catch (Throwable t) {
                    metrics.recordExecution(false, System.nanoTime() - taskStart);
                    log.error("Rule execution threw unhandled exception for rule: {}", compiledRule.getName(), t);
                    throw new RuleExecutionException("Failed to execute rule '" + compiledRule.getName() + "': " + t.getMessage(), t);
                }
            });

            if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
                scope.joinUntil(Instant.now().plus(timeout));
            } else {
                scope.join();
            }

            scope.throwIfFailed(ex -> {
                if (ex instanceof RuleExecutionException ree) {
                    return ree;
                }
                return new RuleExecutionException("Rule execution failed: " + ex.getMessage(), ex);
            });

            return subtask.get();
        } catch (TimeoutException e) {
            long duration = System.nanoTime() - startNanos;
            metrics.recordTimeout(duration);
            throw new RuleExecutionException("Rule '" + compiledRule.getName() + "' execution timed out after "
                    + (timeout != null ? timeout.toMillis() : 0) + " ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = System.nanoTime() - startNanos;
            metrics.recordExecution(false, duration);
            throw new RuleExecutionException("Rule '" + compiledRule.getName() + "' execution interrupted", e);
        } finally {
            metrics.taskFinished();
        }
    }

    /**
     * Executes a rule asynchronously on a virtual thread using default deadline.
     *
     * @param compiledRule rule instance
     * @param context      execution context
     * @return CompletableFuture holding ExecutionResult
     */
    public CompletableFuture<ExecutionResult> executeAsync(CompiledRule compiledRule, ExecutionContext context) {
        return executeAsync(compiledRule, context, defaultTimeout);
    }

    /**
     * Executes a rule asynchronously on a virtual thread with an explicit timeout.
     *
     * @param compiledRule rule instance
     * @param context      execution context
     * @param timeout      deadline duration
     * @return CompletableFuture holding ExecutionResult
     */
    public CompletableFuture<ExecutionResult> executeAsync(CompiledRule compiledRule, ExecutionContext context,
                                                           Duration timeout) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return execute(compiledRule, context, timeout);
            } catch (RuleExecutionException e) {
                return ExecutionResult.failure(e, 0);
            }
        }, virtualThreadExecutor);
    }

    /**
     * Fan-out evaluation: executes a single rule across multiple contexts concurrently using
     * {@link StructuredTaskScope.ShutdownOnFailure} with default timeout.
     *
     * @param compiledRule rule instance
     * @param contexts     list of execution contexts
     * @return list of ExecutionResults preserving input order
     * @throws RuleExecutionException if any subtask fails or execution times out
     */
    public List<ExecutionResult> executeAll(CompiledRule compiledRule, List<ExecutionContext> contexts)
            throws RuleExecutionException {
        return executeAll(compiledRule, contexts, defaultTimeout);
    }

    /**
     * Fan-out evaluation: executes a single rule across multiple contexts concurrently using
     * {@link StructuredTaskScope.ShutdownOnFailure} with specified deadline timeout.
     *
     * @param compiledRule rule instance
     * @param contexts     list of execution contexts
     * @param timeout      deadline duration
     * @return list of ExecutionResults preserving input order
     * @throws RuleExecutionException if any subtask fails or execution times out
     */
    public List<ExecutionResult> executeAll(CompiledRule compiledRule, List<ExecutionContext> contexts,
                                           Duration timeout) throws RuleExecutionException {
        Objects.requireNonNull(compiledRule, "compiledRule cannot be null");
        if (contexts == null || contexts.isEmpty()) {
            return List.of();
        }

        long startNanos = System.nanoTime();
        metrics.taskStarted();

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<ExecutionResult>> subtasks = new ArrayList<>(contexts.size());
            for (ExecutionContext ctx : contexts) {
                ExecutionContext c = ctx != null ? ctx : new ExecutionContext();
                subtasks.add(scope.fork(() -> {
                    long taskStart = System.nanoTime();
                    try {
                        ExecutionResult res = compiledRule.execute(c);
                        metrics.recordExecution(res.isSuccess(), System.nanoTime() - taskStart);
                        return res;
                    } catch (Throwable t) {
                        metrics.recordExecution(false, System.nanoTime() - taskStart);
                        throw new RuleExecutionException("Evaluation failed for rule '" + compiledRule.getName() + "': " + t.getMessage(), t);
                    }
                }));
            }

            if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
                scope.joinUntil(Instant.now().plus(timeout));
            } else {
                scope.join();
            }

            scope.throwIfFailed(ex -> {
                if (ex instanceof RuleExecutionException ree) {
                    return ree;
                }
                return new RuleExecutionException("Structured task scope failure: " + ex.getMessage(), ex);
            });

            return subtasks.stream().map(StructuredTaskScope.Subtask::get).toList();
        } catch (TimeoutException e) {
            long duration = System.nanoTime() - startNanos;
            metrics.recordTimeout(duration);
            throw new RuleExecutionException("Batch execution for rule '" + compiledRule.getName() + "' timed out after "
                    + (timeout != null ? timeout.toMillis() : 0) + " ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = System.nanoTime() - startNanos;
            metrics.recordExecution(false, duration);
            throw new RuleExecutionException("Batch execution interrupted", e);
        } finally {
            metrics.taskFinished();
        }
    }

    /**
     * Parallel rule evaluation: executes multiple rules concurrently against the same context using
     * {@link StructuredTaskScope.ShutdownOnFailure}.
     *
     * @param rules   list of rules to evaluate
     * @param context context containing bindings
     * @param timeout deadline duration
     * @return list of ExecutionResults preserving rule order
     * @throws RuleExecutionException if any subtask fails or execution times out
     */
    public List<ExecutionResult> executeAllRules(List<CompiledRule> rules, ExecutionContext context,
                                                Duration timeout) throws RuleExecutionException {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        ExecutionContext ctx = context != null ? context : new ExecutionContext();
        long startNanos = System.nanoTime();
        metrics.taskStarted();

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<ExecutionResult>> subtasks = new ArrayList<>(rules.size());
            for (CompiledRule rule : rules) {
                Objects.requireNonNull(rule, "rule cannot be null in rules list");
                subtasks.add(scope.fork(() -> {
                    long taskStart = System.nanoTime();
                    try {
                        ExecutionResult res = rule.execute(ctx);
                        metrics.recordExecution(res.isSuccess(), System.nanoTime() - taskStart);
                        return res;
                    } catch (Throwable t) {
                        metrics.recordExecution(false, System.nanoTime() - taskStart);
                        throw new RuleExecutionException("Evaluation failed for rule '" + rule.getName() + "': " + t.getMessage(), t);
                    }
                }));
            }

            if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
                scope.joinUntil(Instant.now().plus(timeout));
            } else {
                scope.join();
            }

            scope.throwIfFailed(ex -> {
                if (ex instanceof RuleExecutionException ree) {
                    return ree;
                }
                return new RuleExecutionException("Structured task scope failure: " + ex.getMessage(), ex);
            });

            return subtasks.stream().map(StructuredTaskScope.Subtask::get).toList();
        } catch (TimeoutException e) {
            long duration = System.nanoTime() - startNanos;
            metrics.recordTimeout(duration);
            throw new RuleExecutionException("Parallel rules execution timed out after "
                    + (timeout != null ? timeout.toMillis() : 0) + " ms", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = System.nanoTime() - startNanos;
            metrics.recordExecution(false, duration);
            throw new RuleExecutionException("Parallel rules execution interrupted", e);
        } finally {
            metrics.taskFinished();
        }
    }

    @Override
    public VirtualThreadExecutorMetrics getMetrics() {
        return metrics;
    }

    public ExecutorService getExecutorService() {
        return virtualThreadExecutor;
    }

    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    @Override
    public void close() {
        if (ownsExecutor && !virtualThreadExecutor.isShutdown()) {
            virtualThreadExecutor.shutdown();
            try {
                if (!virtualThreadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    virtualThreadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                virtualThreadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("VirtualThreadRuleExecutor shut down successfully.");
        }
    }
}
