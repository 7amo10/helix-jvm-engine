---
id: hotspot-jvm-matrix
title: HotSpot JVM Tuning Matrix
sidebar_position: 1
---

# Production HotSpot JVM Tuning Matrix

Operating dynamic bytecode engines requires careful JVM flag tuning to avoid long GC pauses and Metaspace starvation.

---

## Recommended Production Flags

```bash
java -server \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:InitiatingHeapOccupancyPercent=45 \
     -XX:MaxMetaspaceSize=256m \
     -XX:SoftRefLRUPolicyMSPerMB=1000 \
     -XX:CompileThreshold=10000 \
     -XX:MaxInlineSize=35 \
     -XX:FreqInlineSize=325 \
     -XX:+TieredCompilation \
     -XX:+UseCompressedOops \
     -jar engine-core-1.0.0-SNAPSHOT.jar execute ...
```

---

## Flag Justification & Mechanical Sympathy

| JVM Flag | Recommended Value | Engineering Rationale |
|---|---|---|
| `-XX:+UseG1GC` | Default | Predictable low-latency GC pauses for mixed short-lived contexts and long-lived compiled class references. |
| `-XX:MaxGCPauseMillis=200` | `100` - `200` | Bounds Stop-The-World (STW) pause times to meet strict API latency SLAs. |
| `-XX:MaxMetaspaceSize=256m` | `256m` | Hard cap preventing uncollected ClassLoaders from exhausting physical host memory. |
| `-XX:SoftRefLRUPolicyMSPerMB` | `1000` | Retains L2 soft cache entries for 1 second per free MB of heap space before GC reclamation. |
| `-XX:CompileThreshold=10000` | `10000` | Invocation count at which C2 triggers aggressive server optimizations. |
| `-XX:MaxInlineSize=35` | `35` (bytes) | Restricts non-frequent bytecode method inlining to prevent I-Cache thrashing. |
| `-XX:FreqInlineSize=325` | `325` (bytes) | Allows hot `CompiledRule::eval` methods to inline directly into call sites. |
