---
id: java-api-reference
title: Core Java API Reference
sidebar_position: 1
---

# Core Java API Reference

This page documents the foundational programmatic interfaces provided by `engine-api` and `engine-core`.

---

## 1. `HelixEngines` (Programmatic Facade)

The primary entry point for embedding the Helix runtime in Java services:

```java
package com.helix.core;

import com.helix.api.RuleEngine;
import com.helix.profiler.DefaultProfiler;

public final class HelixEngines {
    // Creates a production-ready RuleEngine with tiered caching and default executors
    public static RuleEngine createDefault();

    // Creates an engine instance using a specific bytecode generator (ASM or BYTE_BUDDY)
    public static RuleEngine create(RuleCompiler.GeneratorType generatorType);

    // Creates a standalone profiler instance for HotSpot and flame graph telemetry
    public static DefaultProfiler createProfiler();
}
```

---

## 2. `RuleEngine` & `DefaultRuleEngine`

The centralized engine contract managing compilation, caching, and execution:

```java
package com.helix.api;

public interface RuleEngine extends AutoCloseable {
    CompiledRule compile(Rule rule) throws CompilationException;
    ExecutionResult execute(CompiledRule rule, ExecutionContext context);
    List<ExecutionResult> executeBatch(CompiledRule rule, List<ExecutionContext> contexts);
    void invalidateCache(String ruleName);
    EngineMetrics getMetrics();
    void close();
}
```

---

## 3. `RuleCompiler`

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

## 4. `CompiledRule`

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

## 5. `ExecutionContext` & `ExecutionResult`

Immutable context container and execution result metadata:

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

public class ExecutionResult {
    public boolean isSuccess();
    public Optional<Boolean> getResult();
    public long getDurationNanos();
    public Optional<Throwable> getError();
}
```

---

## 6. `VirtualThreadRuleExecutor`

Loom virtual thread executor with Java 21 `StructuredTaskScope`:

```java
package com.helix.core.executor;

public class VirtualThreadRuleExecutor implements AutoCloseable {
    public VirtualThreadRuleExecutor();
    
    public CompletableFuture<ExecutionResult> executeAsync(CompiledRule rule, ExecutionContext context);
    public List<ExecutionResult> executeBatch(CompiledRule rule, List<ExecutionContext> contexts);
    public long getLastBatchDurationNanos();
    public void close();
}
```

---

## 7. `TieredRuleCache`

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

---

## 8. `FlameGraphAggregator` & `AsciiFlameRenderer`

In-memory folded stack trace aggregation and terminal visualization:

```java
package com.helix.profiler.flamegraph;

public class FlameGraphAggregator {
    public void addSample(String foldedStack, long value);
    public String toFoldedFormat();
    public FlameNode getRootNode();
    public void exportSvg(File targetFile) throws IOException;
    public void exportHtml(File targetFile) throws IOException;
    public void reset();
}

public class AsciiFlameRenderer {
    public static String render(FlameNode root, int terminalWidth);
}
```

---

## 9. Dynamic Probing: `DebugProbe` & `FrameInspector`

Non-intrusive ASM bytecode probe injection:

```java
package com.helix.core.debug;

public interface DebugProbe {
    void onMethodEnter(String className, String methodName, FrameInspector frame);
    void onBranch(int opcode, boolean taken, FrameInspector frame);
    void onMethodExit(String className, String methodName, Object returnValue, long durationNs);
}

public class FrameInspector {
    public int getLocalVariableCount();
    public Object getLocalVariable(int slot);
    public String getLocalVariableName(int slot);
    public Object peekOperandStack();
    public int getOperandStackDepth();
}
```
