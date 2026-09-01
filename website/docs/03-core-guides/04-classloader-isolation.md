---
id: classloader-isolation
title: ClassLoader Isolation Patterns
sidebar_position: 4
---

# ClassLoader Isolation Patterns

Dynamic classloading requires strict lifecycle management to prevent memory leakage and dependency collisions.

---

## Configuring ClassLoaderManager

```java
import com.helix.core.classloader.ClassLoaderManager;
import com.helix.core.classloader.IsolationMode;

// 1. Initialize manager with desired isolation mode
ClassLoaderManager manager = new ClassLoaderManager(IsolationMode.HIERARCHICAL);

// 2. Fetch or create a dedicated tenant loader
ClassLoader financeLoader = manager.getOrCreateClassLoader("FINANCE", "FraudDetectionRule");

// 3. Clean up when unloading tenant or resetting rules
manager.releaseNamespace("FINANCE");
manager.close();
```

---

## Best Practices for Zero Metaspace Leakage

1. **Always Use Weak / Soft References for Long-Lived Storage:** If you retain rules across user sessions, hold them in `TieredRuleCache` rather than static variables.
2. **Namespace Multi-Tenant Rules:** Group rules by business domain or organization ID using `HIERARCHICAL` isolation to allow batch invalidation.
3. **Monitor Metaspace GC Sweeps:** Ensure HotSpot is configured with `-XX:+ExplicitGCInvokesConcurrent` if your application invokes programmatic cleanups.
