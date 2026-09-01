---
id: jit-compilation
title: HotSpot JIT Compilation & Inlining
sidebar_position: 2
---

# HotSpot JIT Tiered Compilation & Inlining

The HotSpot JVM employs a multi-tiered JIT compilation strategy to balance fast application startup with peak execution throughput.

---

## Compilation Tier Progression

```mermaid
stateDiagram-v2
    [*] --> Tier0: First Execution
    Tier0: Tier 0 - Interpreter (Slow, ~250ns)
    
    Tier0 --> Tier1: Invocation Threshold (100)
    Tier1: Tier 1-3 - C1 Client Compiler (Fast startup, ~35ns)
    
    Tier1 --> Tier4: Hot Loop Threshold (15,000)
    Tier4: Tier 4 - C2 Server Compiler (Peak optimization, ~8ns)
```

---

## Method Inlining Threshold Rules

HotSpot makes inlining decisions based on compiled bytecode size:

| Inlining Setting | Bytecode Threshold | Decision Behavior |
|---|---|---|
| `-XX:MaxInlineSize=35` | $\le$ 35 bytes | **Always Inlined** regardless of execution frequency. |
| `-XX:FreqInlineSize=325` | $\le$ 325 bytes | **Inlined** if the method callsite is marked hot by C1 profiling counters. |
| *Large Methods* | $>$ 325 bytes | **Inlining Rejected**. Invocation incurs standard call dispatch overhead. |

---

## Experiment Output Example

```bash
./scripts/start-helix.sh experiment --name jit --output json
```

```json
{
  "experiment" : "JIT",
  "status" : "SUCCESS",
  "scenario" : "HotSpot Tiered Compilation Progression",
  "highestTierAchieved" : "Tier 4 (C2)",
  "inliningDecision" : "Inlined (size <= 35B)"
}
```
