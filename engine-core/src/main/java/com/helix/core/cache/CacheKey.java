package com.helix.core.cache;

import com.helix.api.Rule;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable cache key combining rule name, version, and input schema hash.
 */
public final class CacheKey {

    private final String ruleName;
    private final String version;
    private final String schemaHash;

    public CacheKey(Rule rule) {
        Objects.requireNonNull(rule, "rule cannot be null");
        this.ruleName = Objects.requireNonNull(rule.getName(), "rule name cannot be null");
        this.version = rule.getVersion() != null ? rule.getVersion() : "1.0.0";
        this.schemaHash = computeSchemaHash(rule.getInputSchema());
    }

    public CacheKey(String ruleName, String version, Map<String, Class<?>> inputSchema) {
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.version = version != null ? version : "1.0.0";
        this.schemaHash = computeSchemaHash(inputSchema);
    }

    private static String computeSchemaHash(Map<String, Class<?>> schema) {
        if (schema == null || schema.isEmpty()) {
            return "empty";
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        schema.forEach((k, v) -> sorted.put(k, v != null ? v.getName() : "null"));
        return sorted.toString();
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getVersion() {
        return version;
    }

    public String getSchemaHash() {
        return schemaHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CacheKey cacheKey = (CacheKey) o;
        return Objects.equals(ruleName, cacheKey.ruleName) &&
               Objects.equals(version, cacheKey.version) &&
               Objects.equals(schemaHash, cacheKey.schemaHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleName, version, schemaHash);
    }

    @Override
    public String toString() {
        return "CacheKey{" + ruleName + ":" + version + ":" + schemaHash + "}";
    }
}
