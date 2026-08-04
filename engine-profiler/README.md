# Helix - Engine Profiler Module

The `engine-profiler` module provides comprehensive JVM observability, JIT compilation tracking, GC log analysis, custom JDK Flight Recorder (JFR) event recording, async-profiler integration, real-time terminal dashboard, and health monitoring for the Helix JVM engine.

---

## Key Features

1. **JIT Compilation Monitor (`com.helix.profiler.jit`)**
   - Parses HotSpot `-XX:+PrintCompilation` diagnostic logs.
   - Tracks tiered compilation progression (Tiers 1-3 C1, Tier 4 C2).
   - Monitors On-Stack Replacement (OSR) and deoptimization events (`made not entrant`, `made zombie`).
   - Analyzes inlining decisions from `-XX:+PrintInlining`.

2. **GC & Safepoint Log Analyzer (`com.helix.profiler.gc`)**
   - Parses JDK 17 Unified GC log streams (`-Xlog:gc*`).
   - Tracks GC pause times, collection types (Young, Full, G1 Evacuation), and reclaimed memory KB.
   - Parses safepoint latency logs (`-Xlog:safepoint`).

3. **async-profiler & HTML Flame Graphs (`com.helix.profiler.async`)**
   - Manages CPU, allocation (`ALLOC`), and lock (`LOCK`) profiling sessions.
   - Generates responsive HTML flame graphs (`FlameGraphGenerator`).
   - Compares baseline vs current profiles (`ProfileComparator`) to detect performance regressions or improvements.

4. **Custom JFR Events & Recording Manager (`com.helix.profiler.jfr`)**
   - Emits custom JFR events:
     - `com.helix.RuleExecution`
     - `com.helix.RuleCompilation`
     - `com.helix.ClassLoaderCreated`
     - `com.helix.CacheEviction`
     - `com.helix.MemoryAnalysis`
   - Programmatically controls JFR sessions (`JfrRecordingManager`) and exports `.jfr` files.

5. **Metrics & Live Terminal Dashboard (`com.helix.profiler.metrics` & `com.helix.profiler.interactive`)**
   - Thread-safe operational counters, gauges, and execution timers with p50, p95, p99 percentile calculations.
   - Live Terminal Dashboard (`LiveDashboard`) refreshing every 1 second.

6. **Health Check System (`com.helix.profiler.health`)**
   - Evaluates Metaspace usage, ClassLoader leaks, cache hit rates, and GC pressure to return structured `HealthStatus` and `HealthIssue` reports.

---

## Required Recommended JVM Flags

To enable full profiling capabilities, run the JVM with the following flags:

```bash
# JIT Compilation & Inlining Logging
-XX:+PrintCompilation -XX:+UnlockDiagnosticVMOptions -XX:+PrintInlining

# Unified GC & Safepoint Logging
-Xlog:gc*,safepoint:file=target/gc.log:time,uptime,pid:filecount=5,filesize=10M

# Dynamic JFR & Agent Attach (JDK 17+)
-XX:+UnlockCommercialFeatures -XX:+EnableDynamicAgentLoading
```

---

## Usage Examples

### 1. Generating HTML Flame Graph

```java
AsyncProfilerIntegration profiler = new AsyncProfilerIntegration();
profiler.start(ProfileMode.CPU);

// Run rule execution workload...
profiler.recordSample("com.helix.core.RuleCompiler;parse", 100);
profiler.recordSample("com.helix.core.executor.SyncExecutor;execute", 250);

profiler.dumpHtml(Path.of("target/cpu-flamegraph.html"), "Helix CPU Profile");
```

### 2. Recording Custom JFR Events

```java
JfrRecordingManager manager = new JfrRecordingManager();
manager.startRecording("HelixSession");

JfrEventRecorder recorder = new JfrEventRecorder();
recorder.recordExecution("MyRule", "1.0", true, 5000000L, null);

manager.stopRecording();
manager.dumpRecording(Path.of("target/recording.jfr"));
```

---

## Flame Graph Interpretation Guide

- **Width of Bars**: Represents the proportion of CPU samples or memory allocations spent in that method call frame. Wider bars indicate higher CPU or memory resource consumption.
- **Vertical Stack**: Represents the call stack hierarchy (bottom is parent frame, top is leaf method executing instruction).
- **Color Gradients**: Warm gradients highlight active rule execution paths and bytecode generation frames.
