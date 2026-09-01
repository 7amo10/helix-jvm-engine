---
id: safepoint-monitoring
title: Safepoint Pauses & TTSP Delays
sidebar_position: 5
---

# JVM Safepoints & Time-To-Safepoint (TTSP)

A **Safepoint** is a JVM-wide state where all application threads pause execution so HotSpot can perform global operations such as GC sweeps, biased lock revocation, or on-stack replacement (OSR).

---

## The TTSP Latency Problem

When a safepoint is requested:
1. The JVM signals all worker threads to reach a safepoint check (usually loop headers or method returns).
2. **Time-To-Safepoint (TTSP)** is the delay between requesting the safepoint and the last thread actually stopping.
3. Uncounted loops (loops without safepoint polls) can cause catastrophic latency spikes (e.g. 100ms+ pause times).

```mermaid
sequenceDiagram
    participant JVM as HotSpot Runtime
    participant T1 as Worker Thread 1
    participant T2 as Worker Thread 2 (Uncounted Loop)
    
    JVM->>T1: Safepoint Requested
    T1->>T1: Stops at Poll (~0.1ms)
    JVM->>T2: Safepoint Requested
    Note over T2: Still executing uncounted loop...
    T2->>T2: Finally exits loop (~12ms delay)
    Note over JVM,T2: Total TTSP = 12.1ms (STW Pause!)
    JVM->>JVM: Executes GC Operation
    JVM->>T1: Resumes Execution
    JVM->>T2: Resumes Execution
```

---

## Experiment Output Example

```bash
./scripts/run-experiment.sh safepoint
```

```text
[INFO] Running Safepoint Experiment 'G1CollectForAllocation'...
[INFO] Observed Safepoint Metrics:
       - Time-To-Safepoint (TTSP): 0.450 ms
       - Time At Safepoint:        3.500 ms
       - Total Pause Duration:     3.950 ms
```
