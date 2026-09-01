---
id: metaspace-reclamation
title: Metaspace Growth & GC Unloading
sidebar_position: 3
---

# Metaspace Growth & ClassLoader GC Unloading

This experiment profiles native Metaspace allocation as hundreds of dynamic rule classes are compiled and verifies that Metaspace is fully reclaimed when `RuleClassLoader` instances are dereferenced.

---

## Experiment Mechanics

1. **Phase 1 (Allocation & Growth):** Compiles $N$ unique rules, each defined in a dedicated `RuleClassLoader`. Monitors Metaspace growth from base (~33 MB) upward.
2. **Phase 2 (Dereferencing):** Clears the cache and dereferences the ClassLoader instances.
3. **Phase 3 (GC Trigger & Verification):** Invokes `System.gc()` and verifies that Metaspace usage drops back to initial baseline levels.

---

## Experiment Output Example

```bash
./scripts/start-helix.sh experiment --name metaspace --output json
```

```json
{
  "experiment" : "METASPACE",
  "status" : "SUCCESS",
  "scenario" : "Metaspace Growth vs Classloader GC Unloading",
  "metaspaceReclaimedMB" : "12.4 MB"
}
```

:::tip Production Protection
Always configure `-XX:MaxMetaspaceSize=256m` in production to prevent unbounded native memory expansion if dynamic rules are loaded without proper cache bounds.
:::
