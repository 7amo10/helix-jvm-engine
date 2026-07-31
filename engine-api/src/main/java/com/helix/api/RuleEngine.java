package com.helix.api;

import java.util.concurrent.CompletableFuture;

/**
 * Primary interface for compiling and executing rules within the Helix platform.
 */
public interface RuleEngine {

    /**
     * Compiles a raw rule into an executable rule instance.
     *
     * @param rule the raw rule definition
     * @return compiled rule instance
     * @throws RuleCompilationException if compilation fails
     */
    CompiledRule compile(Rule rule) throws RuleCompilationException;

    /**
     * Synchronously executes a compiled rule against the provided context.
     *
     * @param rule    the compiled rule
     * @param context the execution context
     * @return the execution result
     * @throws RuleExecutionException if execution fails
     */
    ExecutionResult execute(CompiledRule rule, ExecutionContext context) throws RuleExecutionException;

    /**
     * Asynchronously executes a compiled rule against the provided context.
     *
     * @param rule    the compiled rule
     * @param context the execution context
     * @return a CompletableFuture wrapping the execution result
     */
    CompletableFuture<ExecutionResult> executeAsync(CompiledRule rule, ExecutionContext context);
}
