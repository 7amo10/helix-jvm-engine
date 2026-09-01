---
id: tui-dashboard
title: Terminal UI (TUI) Dashboard
sidebar_position: 3
---

# Interactive Terminal UI (TUI) Dashboard

Helix features a terminal dashboard built with **Lanterna** that visualizes runtime HotSpot JVM metrics and cache health without leaving the terminal.

---

## Launching the Dashboard

```bash
./scripts/start-helix.sh profile --tui
```

---

## Dashboard Layout & Grid Components

```
+-----------------------------------------------------------------------------------------+
| HELIX JVM SCRIPTING ENGINE - LIVE DASHBOARD                                    [Ctrl+C] |
+-----------------------------------------------------------------------------------------+
| SYSTEM HEALTH               | JVM MEMORY & GC             | TIERED CACHE TELEMETRY      |
| --------------------------- | --------------------------- | --------------------------- |
| Status:       HEALTHY       | Heap Used:    245 MB / 4 GB | L1 Strong Hits:  1,420,500  |
| Uptime:       01h 24m 12s   | Metaspace:    38.4 MB       | L2 Soft Hits:    14,200     |
| Threads:      18 active     | GC Pause:     1.8 ms avg    | L3 Weak Hits:    420        |
| CPU Usage:    14.2%         | Alloc Rate:   8.5 MB/s      | Total Misses:    12         |
+-----------------------------------------------------------------------------------------+
| JIT COMPILATION METRICS     | RECENT EVENT LOGS                                         |
| --------------------------- | --------------------------------------------------------- |
| Compiled Methods: 412       | [14:20:01] Compiled FraudDetectionRule_v1 (ASM: 1.8ms)   |
| Tier 4 (C2) Hot:  89        | [14:20:15] Executed batch scenario (10,000 items)         |
| Inlined Methods:  310       | [14:20:30] SoftReference sweep cleared 0 entries          |
+-----------------------------------------------------------------------------------------+
| ACTIONS: [ [F1] Trigger GC ]  [ [F2] Invalidate L1 ]  [ [F3] Dump JFR ]  [ [Q] Quit ]   |
+-----------------------------------------------------------------------------------------+
```

---

## Key Interactive Controls

- **`F1` / `[Trigger GC]`:** Triggers an explicit Garbage Collection pass and recalculates Metaspace reclamation.
- **`F2` / `[Invalidate L1]`:** Flushes Tier 1 Caffeine cache to observe L2/L3 promotion mechanics.
- **`F3` / `[Dump JFR]`:** Captures an immediate 60-second JDK Flight Recorder snapshot to disk.
- **`Q` / `Ctrl+C`:** Gracefully closes active executors, flushes metrics, and exits the dashboard.
