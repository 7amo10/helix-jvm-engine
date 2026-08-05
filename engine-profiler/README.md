# Helix - Engine Profiler Module (`engine-profiler`)

The `engine-profiler` module provides real-time HotSpot JVM runtime profiling, HotSpot JIT compiler tier monitoring, JDK Flight Recorder (JFR) event management, and engine health diagnostics.

---

## Core Components

### 1. `JitCompilationMonitor`
Monitors method JIT compilation state and tier transitions (Tier 1 C1 -> Tier 4 C2) and method inlining decisions using `CompilationMXBean` and diagnostic beans:

```java
JitCompilationMonitor jitMonitor = new JitCompilationMonitor();
JitReport report = jitMonitor.inspectMethod("com.helix.CompiledRuleClass", "eval");
System.out.println("Highest JIT Tier: " + report.getHighestTier());
```

### 2. `JfrRecordingManager`
Programmatically controls JDK Flight Recorder sessions to record execution events, allocation pressure, and safepoints:

```java
JfrRecordingManager jfrManager = new JfrRecordingManager();
Path recordingPath = jfrManager.startRecording("HelixProfileSession", 10_000);
// Run workload...
jfrManager.stopAndDump(recordingPath);
```

### 3. `EngineHealthCheck`
Evaluates system memory footprint, thread deadlock state, and cache degradation:

```java
EngineHealthCheck healthCheck = new EngineHealthCheck(cache, classLoaderManager);
HealthReport report = healthCheck.performCheck();
if (!report.isHealthy()) {
    System.err.println("Diagnostics Warning: " + report.getDiagnosticDetails());
}
```
