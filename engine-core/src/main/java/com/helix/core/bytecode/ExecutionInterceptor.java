package com.helix.core.bytecode;

import com.helix.api.ExecutionContext;

/**
 * Contract for evaluating AST nodes or executing rule logic dynamically during runtime interception.
 */
public interface ExecutionInterceptor {

    /**
     * Evaluates the rule for a given context.
     *
     * @param context execution context
     * @return result of evaluation
     * @throws Exception if evaluation fails
     */
    Object evaluate(ExecutionContext context) throws Exception;
}
