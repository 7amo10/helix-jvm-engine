package com.helix.core.parser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Symbol table mapping variable names to their declared/inferred Java type classes.
 */
public class TypeContext {

    private final Map<String, Class<?>> variableTypes;

    public TypeContext() {
        this.variableTypes = new HashMap<>();
    }

    public TypeContext(Map<String, Class<?>> variableTypes) {
        this.variableTypes = variableTypes != null ? new HashMap<>(variableTypes) : new HashMap<>();
    }

    public void registerVariable(String name, Class<?> type) {
        Objects.requireNonNull(name, "variable name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        this.variableTypes.put(name, type);
    }

    public Optional<Class<?>> getVariableType(String name) {
        return Optional.ofNullable(this.variableTypes.get(name));
    }

    public Map<String, Class<?>> getVariableTypes() {
        return Collections.unmodifiableMap(variableTypes);
    }
}
