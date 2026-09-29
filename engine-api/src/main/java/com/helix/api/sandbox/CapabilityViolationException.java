package com.helix.api.sandbox;

import java.util.Objects;

/**
 * Thrown when an AST expression or class-loading operation violates capability policies
 * enforced for untrusted or sandboxed rules.
 * <p>
 * Contains structured metadata indicating the offending class and method to enable
 * clear diagnostics and structured REST API error responses.
 */
public class CapabilityViolationException extends RuntimeException {

    private final String violatingClass;
    private final String violatingMethod;

    /**
     * Constructs a new CapabilityViolationException with default diagnostic message.
     *
     * @param violatingClass  the fully qualified class name that was restricted
     * @param violatingMethod the method name that was restricted
     */
    public CapabilityViolationException(String violatingClass, String violatingMethod) {
        super(formatMessage(violatingClass, violatingMethod, null));
        this.violatingClass = normalize(violatingClass);
        this.violatingMethod = normalize(violatingMethod);
    }

    /**
     * Constructs a new CapabilityViolationException with a custom diagnostic message.
     *
     * @param violatingClass  the fully qualified class name that was restricted
     * @param violatingMethod the method name that was restricted
     * @param message         custom descriptive message
     */
    public CapabilityViolationException(String violatingClass, String violatingMethod, String message) {
        super(message);
        this.violatingClass = normalize(violatingClass);
        this.violatingMethod = normalize(violatingMethod);
    }

    /**
     * Constructs a new CapabilityViolationException with an underlying cause.
     *
     * @param violatingClass  the fully qualified class name that was restricted
     * @param violatingMethod the method name that was restricted
     * @param cause           the underlying root cause
     */
    public CapabilityViolationException(String violatingClass, String violatingMethod, Throwable cause) {
        super(formatMessage(violatingClass, violatingMethod, cause != null ? cause.getMessage() : null), cause);
        this.violatingClass = normalize(violatingClass);
        this.violatingMethod = normalize(violatingMethod);
    }

    /**
     * Returns the name of the class that violated capability policies.
     *
     * @return fully qualified class name or "unknown" if unspecified
     */
    public String getViolatingClass() {
        return violatingClass;
    }

    /**
     * Returns the name of the method that violated capability policies.
     *
     * @return method name or "unknown" if unspecified
     */
    public String getViolatingMethod() {
        return violatingMethod;
    }

    private static String normalize(String value) {
        return (value == null || value.trim().isEmpty()) ? "unknown" : value.trim();
    }

    private static String formatMessage(String className, String methodName, String detail) {
        String c = normalize(className);
        String m = normalize(methodName);
        StringBuilder sb = new StringBuilder("Capability violation: Operation '")
                .append(c).append(".").append(m)
                .append("' is restricted in sandboxed rule execution mode.");
        if (detail != null && !detail.trim().isEmpty()) {
            sb.append(" Detail: ").append(detail.trim());
        }
        return sb.toString();
    }
}
