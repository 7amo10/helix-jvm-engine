---
id: system-overview
title: System Architecture & Submodules
sidebar_position: 1
---

import SystemArchitectureSvg from '@site/static/img/diagrams/system-architecture.svg';

# Helix System Architecture

Helix is architected as a modular multi-subsystem engine designed to isolate API contracts, dynamic bytecode generation, low-level JVM profiling, and memory safety.

---

## End-to-End System Architecture

<div style={{textAlign: 'center', margin: '2rem 0'}}>
  <SystemArchitectureSvg style={{maxWidth: '100%', height: 'auto', borderRadius: '8px'}} />
</div>

---

## Submodule Responsibilities

The codebase is partitioned into 5 independent Maven submodules:

```
helix-parent/
├── engine-api/          # Public API contracts & execution models
├── engine-core/         # Parser, AST optimizer, ASM/ByteBuddy generators, Executors, Cache, CLI/TUI
├── engine-profiler/     # JIT compilation monitors, GC log parsers, JFR managers, Health checks
├── engine-agent/        # Java Agent transformer, JOL memory analyzers, JMX MBeans
└── engine-experiments/  # JVM behavior experiment scenarios & JMH benchmark suites
```

### 1. `engine-api` (Contracts & Interfaces)
- Zero third-party runtime dependencies.
- Exposes `Rule`, `CompiledRule`, `ExecutionContext`, `ExecutionResult`, and `RuleEngine`.
- Guaranteed backward compatibility for embedding services.

### 2. `engine-core` (Compilation, Execution, Caching & Tooling)
- **Native Parser & AST:** Native, zero-dependency recursive-descent lexer and operator-precedence AST parser with compile-time AST optimization passes (`AstBuilder`, `AstOptimizer`).
- **Optimizers:** Applies constant folding, dead-code elimination, and algebraic simplification passes.
- **Generators:** Direct JVM bytecode generation via `ByteBuddyRuleGenerator` and raw ASM `AsmRuleGenerator`.
- **Dynamic Debugging:** Non-intrusive probe injection (`DebugClassVisitor`, `DebugProbe`) and local variable / operand stack frame inspection (`FrameInspector`).
- **ClassLoaders:** `ClassLoaderManager` handles dynamic class generation and Metaspace safety with hierarchical class unloading.
- **Cache:** `TieredRuleCache` manages 3-tiered JVM reference lifecycles (L1 Strong, L2 Soft, L3 Weak).
- **Executors:** `SyncExecutor`, `AsyncExecutor`, `BatchExecutor`, and Java 21 Loom `VirtualThreadRuleExecutor` with `StructuredTaskScope`.
- **Interactive Tooling:** JLine 3 Terminal REPL (`ReplCommand`) with syntax highlighting, history, multiline editing, and live bytecode disassembly (`:disasm`).
- **Programmatic Facade:** `HelixEngines` factory providing clean embedding APIs (`DefaultRuleEngine`, `DefaultProfiler`).

### 3. `engine-profiler` (Observability, Telemetry & Flame Graphs)
- **In-Memory Folded Stack Aggregator:** `FlameGraphAggregator` supporting Brendan Gregg folded format, JFR sample ingestion, and CPU execution time / heap allocation dimensions.
- **Terminal Flame Graph Renderer:** Unicode ASCII box-drawing flame graph visualizer (`AsciiFlameRenderer`) with full-screen Lanterna TUI integration (`[F] Flame Graph` view).
- **Export Formats:** Exports interactive vector SVG and standalone HTML flame graphs with search and zoom capabilities.
- **HotSpot JIT Telemetry:** Tracks JIT compilation events (`-XX:+PrintCompilation`) and tiered compiler transitions (Tier 1-4).
- **Safepoints & JFR:** Evaluates Safepoint Time-To-Safepoint (TTSP) delays and connects with JDK Flight Recorder (`JfrRecordingManager`).

### 4. `engine-agent` (Bytecode Instrumentation)
- Attachable `-javaagent` using `ByteBuddyAgent` and ASM `ClassFileTransformer`.
- Performs real-time memory inspection with Java Object Layout (JOL).
- Exposes runtime metrics via JMX MBeans (`HelixEngineMXBean`).

### 5. `engine-experiments` (Benchmarks & Research)
- Reproducible JVM behavior experiments: `metaspace`, `jit`, `gc`, `layout`, `safepoint`.
- JMH microbenchmark suites measuring compilation latency, execution throughput, and thread scalability under virtual and platform threads.
