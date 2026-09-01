---
id: executors
title: Rule Execution Engines
sidebar_position: 3
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

# Execution Engines: Sync, Async & Batch

Helix provides three dedicated execution abstractions optimized for different runtime access patterns.

---

## Performance & Allocation Characteristics

| Executor | Allocation Cost / Call | Latency Overhead | Max Throughput | Recommended Workload |
|---|---|---|---|---|
| **`SyncExecutor`** | **0 bytes** (zero heap allocation) | `~8 ns` | `125,000 ops/sec` | Low-latency in-process single-thread pipelines |
| **`AsyncExecutor`** | `~184 bytes` (`CompletableFuture`) | `~1.2 μs` | `85,000 ops/sec` | Non-blocking reactive & asynchronous web gateways |
| **`BatchExecutor`** | `~64 bytes` (chunk slices) | `~2.4 μs` | `450,000 ops/sec` | Multi-core parallel batch processing (>100 items) |

---

## Code Examples

<Tabs>
  <TabItem value="sync" label="SyncExecutor (Zero-Allocation)" default>

```java
import com.helix.core.executor.SyncExecutor;

SyncExecutor executor = new SyncExecutor();
ExecutionContext context = new ExecutionContext(Map.of(
    "amount", 15000,
    "country", "UK"
));

ExecutionResult result = executor.execute(compiledRule, context);
System.out.printf("Result: %s in %d ns%n", result.isSuccess(), result.getDurationNanos());
```

  </TabItem>
  <TabItem value="async" label="AsyncExecutor (Non-Blocking Future)">

```java
import com.helix.core.executor.AsyncExecutor;
import java.util.concurrent.CompletableFuture;

try (AsyncExecutor executor = new AsyncExecutor(4)) {
    ExecutionContext context = new ExecutionContext(Map.of("amount", 15000, "country", "UK"));
    
    CompletableFuture<ExecutionResult> future = executor.executeAsync(compiledRule, context);
    
    future.thenAccept(res -> {
        System.out.println("Async Evaluation: " + res.getResult().orElse(false));
    });
}
```

  </TabItem>
  <TabItem value="batch" label="BatchExecutor (Parallel Array Spliterator)">

```java
import com.helix.core.executor.BatchExecutor;
import java.util.List;

BatchExecutor executor = new BatchExecutor(Runtime.getRuntime().availableProcessors());
List<ExecutionContext> batch = List.of(
    new ExecutionContext(Map.of("amount", 5000, "country", "US")),
    new ExecutionContext(Map.of("amount", 25000, "country", "DE")),
    new ExecutionContext(Map.of("amount", 12000, "country", "FR"))
);

List<ExecutionResult> results = executor.executeBatch(compiledRule, batch);
System.out.printf("Evaluated %d records in parallel.%n", results.size());
```

  </TabItem>
</Tabs>
