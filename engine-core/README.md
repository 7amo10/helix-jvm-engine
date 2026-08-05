# Helix - Engine Core Module (`engine-core`)

The `engine-core` module implements the central rule compilation pipeline, AST optimization passes, dynamic bytecode generation (via ByteBuddy and ASM), multi-tenant ClassLoader memory management, tiered caching, execution engines, and the Picocli/Lanterna CLI/TUI application.

---

## Core Architecture & Components

### 1. `RuleCompiler` & Bytecode Generation
Parses JSON rule definitions into abstract syntax trees (AST), applies constant folding and dead code elimination, and outputs dynamic bytecode:

```java
RuleCompiler compiler = new RuleCompiler();
String ruleJson = """
    {
        "name": "DiscountRule",
        "version": "1.0.0",
        "expression": "cartTotal > 100",
        "inputSchema": { "cartTotal": "double" }
    }
    """;
CompiledRule rule = compiler.compile(ruleJson);
```

### 2. `ClassLoaderManager` (Isolation Modes)
Manages `RuleClassLoader` lifetimes to prevent Metaspace leaks:
- `ISOLATED` - Dedicated ClassLoader per rule instantiation.
- `SHARED` - Global single ClassLoader for high density.
- `HIERARCHICAL` - Category-level loaders with `SharedUtilityClassLoader` parent.

```java
ClassLoaderManager manager = new ClassLoaderManager(IsolationMode.HIERARCHICAL);
RuleClassLoader loader = manager.getOrCreateClassLoader("RETAIL", "DiscountRule");
```

### 3. `TieredRuleCache`
Implements a 3-tiered reference hierarchy to balance cache speed and memory pressure:
- **L1 Cache:** Strong reference (Caffeine) for hot rule hits.
- **L2 Cache:** Soft reference retained until JVM memory pressure occurs.
- **L3 Cache:** Weak reference cleared upon garbage collection.

```java
TieredRuleCache cache = new TieredRuleCache(100, 10, TimeUnit.MINUTES);
cache.put(cacheKey, compiledRule);
Optional<CompiledRule> cached = cache.get(cacheKey);
```

### 4. Execution Engines
- `SyncExecutor` - Low-latency synchronous single-thread execution.
- `AsyncExecutor` - Asynchronous non-blocking evaluation returning `CompletableFuture<ExecutionResult>`.
- `BatchExecutor` - Parallel worker pool execution across collections of contexts.

---

## CLI & TUI Application

Run the application using the startup helper script:

```bash
./scripts/start-helix.sh execute --rule examples/rules/fraud-detection.json --context examples/rules/sample-context.json --mode async --output json
```

To launch the interactive real-time observability terminal dashboard:

```bash
./scripts/start-helix.sh profile --dashboard
```
