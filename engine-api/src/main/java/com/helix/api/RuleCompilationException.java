package com.helix.api;

/**
 * Exception thrown when a rule fails to compile.
 */
public class RuleCompilationException extends Exception {

    public RuleCompilationException(String message) {
        super(message);
    }

    public RuleCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
