package com.helix.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Encapsulates variables and state passed into a rule execution.
 */
public class ExecutionContext {

    private final Map<String, Object> variables;

    public ExecutionContext() {
        this.variables = new HashMap<>();
    }

    public ExecutionContext(Map<String, Object> variables) {
        this.variables = new HashMap<>(Objects.requireNonNull(variables, "variables cannot be null"));
    }

    /**
     * Sets a variable in the execution context.
     *
     * @param name  the variable name
     * @param value the variable value
     */
    public void setVariable(String name, Object value) {
        Objects.requireNonNull(name, "variable name cannot be null");
        this.variables.put(name, value);
    }

    /**
     * Retrieves a variable from the execution context.
     *
     * @param name the variable name
     * @return an Optional containing the value, or empty if not set
     */
    public Optional<Object> getVariable(String name) {
        return Optional.ofNullable(this.variables.get(name));
    }

    /**
     * Retrieves a typed variable from the execution context.
     *
     * @param name the variable name
     * @param type the expected type class
     * @param <T>  the target type
     * @return an Optional containing the cast value, or empty if not present
     * @throws ClassCastException if the value cannot be cast to the specified type
     */
    public <T> Optional<T> getVariable(String name, Class<T> type) {
        Object val = this.variables.get(name);
        if (val == null) {
            return Optional.empty();
        }
        return Optional.of(type.cast(val));
    }

    /**
     * Checks whether a variable exists in the context.
     *
     * @param name the variable name
     * @return true if present, false otherwise
     */
    public boolean hasVariable(String name) {
        return this.variables.containsKey(name);
    }

    /**
     * Returns an unmodifiable map of all variables in the context.
     *
     * @return unmodifiable map of variables
     */
    public Map<String, Object> getVariables() {
        return Collections.unmodifiableMap(this.variables);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExecutionContext context = (ExecutionContext) o;
        return Objects.equals(variables, context.variables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variables);
    }

    @Override
    public String toString() {
        return "ExecutionContext{" +
                "variables=" + variables +
                '}';
    }
}
