package com.helix.api;

/**
 * Represents a compiled, executable rule instance.
 */
public interface CompiledRule {

    /**
     * Gets the name of the rule that was compiled.
     *
     * @return rule name
     */
    String getName();

    /**
     * Gets the version of the rule that was compiled.
     *
     * @return rule version
     */
    String getVersion();

    /**
     * Executes the compiled rule against the given execution context.
     *
     * @param context the execution context containing input variables
     * @return the result of executing the rule
     * @throws RuleExecutionException if an error occurs during execution
     */
    ExecutionResult execute(ExecutionContext context) throws RuleExecutionException;
}
