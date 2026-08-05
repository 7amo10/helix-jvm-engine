# Helix - Engine API Module (`engine-api`)

The `engine-api` module defines the foundational interfaces, data records, and abstractions for the Helix JVM Scripting Engine and Profiler framework. It contains zero third-party framework dependencies, making it extremely lightweight and portable.

---

## Key Interfaces & Classes

### 1. `Rule`
Base interface representing an executable rule definition:
- `String getName()` - Returns rule unique identifier.
- `String getVersion()` - Returns rule semantic version string.
- `ExecutionResult execute(ExecutionContext context)` - Evaluates the rule against variable inputs.

### 2. `CompiledRule`
Extends `Rule` to represent a rule compiled directly into dynamic JVM bytecode.
- `byte[] getBytecode()` - Accesses generated class bytes.
- `Class<?> getCompiledClass()` - Returns loaded Java Class instance.

### 3. `ExecutionContext`
Thread-safe container holding input parameters and execution state:
```java
Map<String, Object> variables = Map.of("amount", 15000.0, "VIP", true);
ExecutionContext context = new ExecutionContext(variables);
```

### 4. `ExecutionResult`
Encapsulates execution outcome, duration in nanoseconds, result value, or error exceptions:
```java
ExecutionResult result = rule.execute(context);
if (result.isSuccess()) {
    Object value = result.getResult().orElse(null);
    long elapsedNanos = result.getExecutionTimeNanos();
} else {
    Throwable error = result.getError().orElse(null);
}
```

---

## Code Example

```java
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;

import java.util.Map;

public class ApiUsageExample {

    public void process(ExecutionContext context) {
        Double amount = (Double) context.get("amount");
        Boolean isVip = (Boolean) context.get("VIP");

        long start = System.nanoTime();
        boolean eligible = amount != null && amount > 10000.0 && Boolean.TRUE.equals(isVip);
        long duration = System.nanoTime() - start;

        ExecutionResult result = ExecutionResult.success(eligible, duration);
        System.out.println("Execution Result: " + result.getResult().orElse(false));
    }
}
```
