---
id: tiered-cache-internals
title: Multi-Tiered Cache Internals
sidebar_position: 4
---

import TieredCacheSvg from '@site/static/img/diagrams/tiered-cache.svg';

# Multi-Tiered Rule Cache

Helix implements a 3-tiered caching architecture (`TieredRuleCache`) designed around JVM reference strength mechanics to balance raw execution throughput against heap safety.

---

## Cache Tier Architecture Diagram

<div style={{textAlign: 'center', margin: '2rem 0'}}>
  <TieredCacheSvg style={{maxWidth: '100%', height: 'auto', borderRadius: '8px'}} />
</div>

---

## Detailed Tier Mechanics

### Tier 1: Strong Reference Cache (Caffeine)
- **Reference Strength:** Strong references (`java.lang.Object`).
- **Hit Latency:** `< 12 ns`.
- **Eviction Policy:** Window TinyLFU eviction algorithm based on frequency and recency.
- **Role:** Holds the hottest active rules directly in Old Generation memory for zero GC interference.

### Tier 2: Soft Reference Cache (`SoftReference<CompiledRule>`)
- **Reference Strength:** Soft references.
- **Hit Latency:** `< 45 ns`.
- **GC Behavior:** Softly reachable objects survive minor GC sweeps. The HotSpot JVM only reclaims Soft References when free heap space drops critically low:
  ```text
  Retention Time (ms) = Free Heap (MB) * SoftRefLRUPolicyMSPerMB
  ```
- **Role:** Acts as an elastic buffer that expands when heap is plentiful and contracts safely under memory spikes.

### Tier 3: Weak Reference Cache (`WeakReference<CompiledRule>`)
- **Reference Strength:** Weak references.
- **Hit Latency:** `< 80 ns`.
- **GC Behavior:** Reclaimed during the very next minor GC cycle if no strong references remain.
- **Role:** Safety net for short-lived, transient rule evaluations.

---

## Cache Lookup Logic

```java
public Optional<CompiledRule> get(CacheKey key) {
    // 1. Try Tier 1 Strong Cache
    CompiledRule rule = l1StrongCache.getIfPresent(key);
    if (rule != null) {
        metrics.recordL1Hit();
        return Optional.of(rule);
    }

    // 2. Try Tier 2 Soft Cache
    SoftReference<CompiledRule> softRef = l2SoftCache.get(key);
    if (softRef != null && (rule = softRef.get()) != null) {
        metrics.recordL2Hit();
        l1StrongCache.put(key, rule); // Promote back to L1
        return Optional.of(rule);
    }

    // 3. Try Tier 3 Weak Cache
    WeakReference<CompiledRule> weakRef = l3WeakCache.get(key);
    if (weakRef != null && (rule = weakRef.get()) != null) {
        metrics.recordL3Hit();
        l1StrongCache.put(key, rule); // Promote back to L1
        return Optional.of(rule);
    }

    metrics.recordCacheMiss();
    return Optional.empty();
}
```
