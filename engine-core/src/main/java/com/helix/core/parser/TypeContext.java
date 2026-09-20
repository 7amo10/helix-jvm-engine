package com.helix.core.parser;

import com.helix.api.ml.ModelRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Symbol table mapping variable names to their declared/inferred Java type classes,
 * optionally holding a {@link ModelRegistry} for compile-time model feature verification.
 */
public class TypeContext {

    private final Map<String, Class<?>> variableTypes;
    private final ModelRegistry modelRegistry;

    public TypeContext() {
        this(null, null);
    }

    public TypeContext(Map<String, Class<?>> variableTypes) {
        this(variableTypes, null);
    }

    public TypeContext(Map<String, Class<?>> variableTypes, ModelRegistry modelRegistry) {
        this.variableTypes = variableTypes != null ? new HashMap<>(variableTypes) : new HashMap<>();
        this.modelRegistry = modelRegistry;
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

    public Optional<ModelRegistry> getModelRegistry() {
        return Optional.ofNullable(modelRegistry);
    }
}
