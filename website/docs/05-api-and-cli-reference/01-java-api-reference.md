---
id: java-api-reference
title: Core Java API Reference
sidebar_position: 1
---

# Core Java API Reference

This page documents the foundational programmatic interfaces provided by `engine-api` and `engine-core`.

---

## 1. `RuleCompiler`

Compiles JSON rule specifications into JVM executable `CompiledRule` objects.

```java
package com.helix.core;

public class RuleCompiler {
    public enum GeneratorType { BYTE_BUDDY, ASM }

    public RuleCompiler();
    public RuleCompiler(GeneratorType generatorType);

    public CompiledRule compile(String jsonRule) throws CompilationException;
    public CompiledRule compile(File jsonFile) throws CompilationException;
    public CompilationMetrics getLastMetrics();
}
```

---

## 2. `CompiledRule`

The native executable contract generated for every dynamic rule.

```java
package com.helix.api;

public interface CompiledRule {
    String getName();
    String getVersion();
    boolean eval(ExecutionContext context);
}
```

---

## 3. `ExecutionContext`

Immutable context container holding typed input variables.

```java
package com.helix.api;

public class ExecutionContext {
    public ExecutionContext(Map<String, Object> variables);
    public Object get(String key);
    public int getInt(String key);
    public double getDouble(String key);
    public boolean getBoolean(String key);
    public String getString(String key);
}
```

---

## 4. `TieredRuleCache`

3-tiered cache management system with hit rate telemetry.

```java
package com.helix.core.cache;

public class TieredRuleCache implements AutoCloseable {
    public TieredRuleCache(int l1MaxCapacity, long expireAfterMinutes, TimeUnit timeUnit);
    public Optional<CompiledRule> get(CacheKey key);
    public void put(CacheKey key, CompiledRule rule);
    public void invalidate(CacheKey key);
    public CacheMetrics getMetrics();
    public void close();
}
```
