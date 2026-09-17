package com.helix.core;

import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.api.Rule;
import com.helix.api.RuleCompilationException;
import com.helix.api.RuleEngine;
import com.helix.api.RuleExecutionException;
import com.helix.core.executor.AsyncExecutor;
import com.helix.core.executor.SyncExecutor;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Default implementation of {@link RuleEngine} orchestrating compilation and execution.
 */
public class DefaultRuleEngine implements RuleEngine, AutoCloseable {

    private final RuleCompiler compiler;
    private final SyncExecutor syncExecutor;
    private final AsyncExecutor asyncExecutor;

    public DefaultRuleEngine() {
        this.compiler = new RuleCompiler();
        this.syncExecutor = new SyncExecutor();
        this.asyncExecutor = new AsyncExecutor();
    }

    public DefaultRuleEngine(RuleCompiler compiler, SyncExecutor syncExecutor, AsyncExecutor asyncExecutor) {
        this.compiler = Objects.requireNonNull(compiler, "compiler cannot be null");
        this.syncExecutor = Objects.requireNonNull(syncExecutor, "syncExecutor cannot be null");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor cannot be null");
    }

    @Override
    public CompiledRule compile(Rule rule) throws RuleCompilationException {
        return compiler.compile(rule);
    }

    @Override
    public ExecutionResult execute(CompiledRule rule, ExecutionContext context) throws RuleExecutionException {
        return syncExecutor.execute(rule, context);
    }

    @Override
    public CompletableFuture<ExecutionResult> executeAsync(CompiledRule rule, ExecutionContext context) {
        return asyncExecutor.executeAsync(rule, context);
    }

    @Override
    public void close() {
        asyncExecutor.close();
    }
}
