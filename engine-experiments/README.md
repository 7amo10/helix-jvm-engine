# Engine Experiments and Performance Benchmarks (engine-experiments)

The `engine-experiments` module provides JVM behavior experiments (Metaspace memory leaks, HotSpot JIT compilation tiers, GC reference pressure, object memory layouts, and safepoints) along with JMH microbenchmarks for rule compilation, rule execution, and cache lookups.

---

## JVM Performance Experiments

### 1. Metaspace Leak Experiment (MetaspaceLeakExperiment)
- **Class:** `com.helix.experiments.metaspace.MetaspaceLeakExperiment`
- **Description:** Demonstrates Metaspace growth caused by unclosed ClassLoader references versus proper ClassLoader cleanup and GC unloading.
- **Leak Scenario:** Retains strong references to dynamically generated class definitions and ClassLoader instances, causing Metaspace memory footprint to grow continuously.
- **Fixed Scenario:** Closes `URLClassLoader` instances, releases references, and invokes `System.gc()` to allow HotSpot class unloading and Metaspace reclamation.
- **JVM Flags:** `-XX:MaxMetaspaceSize=128m -Xlog:class+unload=info`

### 2. JIT Tiered Compilation Experiment (JitCompilationExperiment)
- **Class:** `com.helix.experiments.jit.JitCompilationExperiment`
- **Description:** Observes method compilation progression through HotSpot compilation tiers (Interpreter -> C1 -> C2) and checks inlining bytecode thresholds.
- **Tier Progression:** 
  - 100 invocations: Tier 1/3 C1 compilation.
  - 2,000 invocations: HotSpot profiling.
  - 15,000 invocations: Tier 4 C2 Server compilation.
- **Inlining Analysis:** Verifies method bytecode size against HotSpot `-XX:MaxInlineSize=35` and `-XX:FreqInlineSize=325` limits.
- **JVM Flags:** `-XX:+PrintCompilation -XX:+PrintInlining -XX:MaxInlineSize=35`

### 3. GC Stress and Reference Experiment (GcStressExperiment)
- **Class:** `com.helix.experiments.gc.GcStressExperiment`
- **Description:** Evaluates clearing of `SoftReference` instances under heap allocation pressure vs immediate collection of `WeakReference` instances.
- **WeakReference Behavior:** Weakly referenced objects are collected during any GC pass.
- **SoftReference Behavior:** Softly referenced objects are retained until memory pressure forces reclamation prior to OutOfMemoryError.
- **JVM Flags:** `-XX:SoftRefLRUPolicyMSPerMB=1000 -Xlog:gc*`

### 4. Object Layout Experiment (ObjectLayoutExperiment)
- **Class:** `com.helix.experiments.layout.ObjectLayoutExperiment`
- **Description:** Uses JOL (Java Object Layout) to inspect Mark/Klass word headers, field alignment padding, and Compressed OOPs footprint.
- **Header Structure:** 64-bit JVM Mark Word (8 bytes) + Klass Word (4 bytes compressed / 8 bytes uncompressed).
- **Alignment:** 8-byte object alignment padding.
- **JVM Flags:** `-XX:+UseCompressedOops` vs `-XX:-UseCompressedOops`

### 5. Safepoint Latency Experiment (SafepointExperiment)
- **Class:** `com.helix.experiments.safepoint.SafepointExperiment`
- **Description:** Measures Time-To-Safepoint (TTSP) and thread pause duration under background workload and GC execution.
- **Metrics:** TTSP (reaching safepoint duration), time spent at safepoint, and total pause time.
- **JVM Flags:** `-Xlog:safepoint=info`

---

## JMH Microbenchmarks

### 1. Compilation Benchmark (CompilationBenchmark)
- **Class:** `com.helix.experiments.benchmarks.CompilationBenchmark`
- **Metrics:** Rule compilation latency (microseconds) comparing simple vs complex rules with ByteBuddy vs ASM generators.

### 2. Execution Benchmark (ExecutionBenchmark)
- **Class:** `com.helix.experiments.benchmarks.ExecutionBenchmark`
- **Metrics:** Rule execution throughput (ops/sec) comparing cold vs hot JIT-compiled execution and 4-thread concurrent execution.

### 3. Cache Benchmark (CacheBenchmark)
- **Class:** `com.helix.experiments.benchmarks.CacheBenchmark`
- **Metrics:** L1/L2/L3 cache hit/miss latency (nanoseconds) and eviction overhead.

---

## Running Experiments and Benchmarks via CLI

### Run via Experiment Runner
```bash
mvn exec:java -pl engine-experiments -Dexec.mainClass="com.helix.experiments.ExperimentRunner" -Dexec.args="all"
```

### Run JMH Benchmarks
```bash
mvn exec:java -pl engine-experiments -Dexec.mainClass="com.helix.experiments.benchmarks.BenchmarkRunner"
```
