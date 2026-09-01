---
id: overview
title: JVM Mechanical Sympathy & Experiments
sidebar_position: 1
---

# JVM Mechanical Sympathy & Experiments

Helix includes a dedicated research and verification harness (`engine-experiments`) designed to demonstrate, profile, and verify low-level JVM behavior under real-world pressure.

---

## The 5 Core JVM Behavior Experiments

| Experiment Name | JVM Internals Tested | Key Metrics Observed |
|---|---|---|
| **`jit`** | HotSpot Tiered Compilation (C1/C2) & Inlining | Compilation tier transitions (0 -> 3 -> 4), bytecode size inlining limits (35B / 325B) |
| **`metaspace`** | Metaspace Growth vs ClassLoader GC Unloading | Native memory MB growth, ClassLoader reachability, memory reclamation |
| **`gc`** | Soft vs Weak Reference Pressure | SoftReference heap survival vs immediate WeakReference minor GC sweeps |
| **`safepoint`** | Stop-The-World (STW) Pauses & TTSP | Time-To-Safepoint (TTSP) delay, thread synchronization latency |
| **`layout`** | Java Object Layout (JOL) Headers & OOPs | Mark Word (8B), Klass Pointer (4B), instance padding (8B boundary alignment) |

---

## Running Experiments via CLI

You can execute any experiment scenario using the bundled helper script or CLI:

```bash
# Run specific experiment
./scripts/run-experiment.sh jit
./scripts/run-experiment.sh metaspace
./scripts/run-experiment.sh gc
./scripts/run-experiment.sh safepoint
./scripts/run-experiment.sh layout

# Or run all experiments consecutively
./scripts/run-experiment.sh all
```
