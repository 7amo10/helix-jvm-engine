package com.helix.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Holds the output outcome of a rule execution, including execution state, result value, duration, and potential errors.
 */
public class ExecutionResult {

    private final boolean success;
    private final Object result;
    private final Throwable error;
    private final long executionTimeNanos;

    private ExecutionResult(boolean success, Object result, Throwable error, long executionTimeNanos) {
        this.success = success;
        this.result = result;
        this.error = error;
        this.executionTimeNanos = executionTimeNanos;
    }

    /**
     * Creates a successful ExecutionResult.
     *
     * @param result             the output value of the rule
     * @param executionTimeNanos execution duration in nanoseconds
     * @return successful ExecutionResult instance
     */
    public static ExecutionResult success(Object result, long executionTimeNanos) {
        return new ExecutionResult(true, result, null, executionTimeNanos);
    }

    /**
     * Creates a failed ExecutionResult.
     *
     * @param error              the exception encountered during execution
     * @param executionTimeNanos execution duration in nanoseconds
     * @return failed ExecutionResult instance
     */
    public static ExecutionResult failure(Throwable error, long executionTimeNanos) {
        return new ExecutionResult(false, null, Objects.requireNonNull(error, "error cannot be null"), executionTimeNanos);
    }

    /**
     * Checks if execution was successful.
     *
     * @return true if succeeded, false if failed
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Gets the result value.
     *
     * @return an Optional containing the result, or empty if execution failed or returned null
     */
    public Optional<Object> getResult() {
        return Optional.ofNullable(result);
    }

    /**
     * Gets the result value cast to expected type.
     *
     * @param type target type class
     * @param <T>  target type
     * @return an Optional containing the cast value
     */
    public <T> Optional<T> getResult(Class<T> type) {
        if (result == null) {
            return Optional.empty();
        }
        return Optional.of(type.cast(result));
    }

    /**
     * Gets the error if execution failed.
     *
     * @return an Optional containing the error, or empty if successful
     */
    public Optional<Throwable> getError() {
        return Optional.ofNullable(error);
    }

    /**
     * Gets the execution duration in nanoseconds.
     *
     * @return nanoseconds elapsed
     */
    public long getExecutionTimeNanos() {
        return executionTimeNanos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionResult that = (ExecutionResult) o;
        return success == that.success &&
                executionTimeNanos == that.executionTimeNanos &&
                Objects.equals(result, that.result) &&
                Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(success, result, error, executionTimeNanos);
    }

    @Override
    public String toString() {
        return "ExecutionResult{" +
                "success=" + success +
                ", result=" + result +
                ", error=" + error +
                ", executionTimeNanos=" + executionTimeNanos +
                '}';
    }
}
