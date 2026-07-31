package com.helix.api;

/**
 * Exception thrown when a compiled rule fails during execution.
 */
public class RuleExecutionException extends Exception {

    public RuleExecutionException(String message) {
        super(message);
    }

    public RuleExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
