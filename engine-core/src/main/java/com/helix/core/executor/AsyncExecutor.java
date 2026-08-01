package com.helix.core.executor;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.RuleExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Asynchronous rule executor using {@link CompletableFuture} backed by a configurable ThreadPoolExecutor.
 */
public class AsyncExecutor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AsyncExecutor.class);

    private final SyncExecutor syncExecutor;
    private final ExecutorService executorService;
    private final boolean ownsExecutorService;

    public AsyncExecutor() {
        this(new ExecutorConfiguration());
    }

    public AsyncExecutor(ExecutorConfiguration config) {
        this(new SyncExecutor(), createExecutorService(config), true);
    }

    public AsyncExecutor(SyncExecutor syncExecutor, ExecutorService executorService) {
        this(syncExecutor, executorService, false);
    }

    private AsyncExecutor(SyncExecutor syncExecutor, ExecutorService executorService, boolean ownsExecutorService) {
        this.syncExecutor = Objects.requireNonNull(syncExecutor, "syncExecutor cannot be null");
        this.executorService = Objects.requireNonNull(executorService, "executorService cannot be null");
        this.ownsExecutorService = ownsExecutorService;
    }

    public CompletableFuture<ExecutionResult> executeAsync(CompiledRule compiledRule, ExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return syncExecutor.execute(compiledRule, context);
            } catch (RuleExecutionException e) {
                return ExecutionResult.failure(e, 0);
            }
        }, executorService);
    }

    public CompletableFuture<ExecutionResult> executeAsync(CompiledRule compiledRule, ExecutionContext context, long timeout, TimeUnit unit) {
        return executeAsync(compiledRule, context)
                .orTimeout(timeout, unit)
                .exceptionally(ex -> ExecutionResult.failure(new RuleExecutionException("Async rule execution timed out or failed: " + ex.getMessage(), ex), 0));
    }

    public ExecutorMetrics getMetrics() {
        return syncExecutor.getMetrics();
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    @Override
    public void close() {
        if (ownsExecutorService && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("AsyncExecutor thread pool shut down successfully.");
        }
    }

    private static ExecutorService createExecutorService(ExecutorConfiguration config) {
        AtomicInteger threadCount = new AtomicInteger(1);
        return new ThreadPoolExecutor(
                config.getCorePoolSize(),
                config.getMaxPoolSize(),
                config.getKeepAliveTimeSeconds(),
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(config.getQueueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, config.getThreadNamePrefix() + threadCount.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
