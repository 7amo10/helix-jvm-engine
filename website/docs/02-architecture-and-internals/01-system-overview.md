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

### 2. `engine-core` (Compilation, Execution & Caching)
- **Parser & AST:** Converts JSON rule representations into structured `ASTNode` hierarchies (`BinaryOpNode`, `LiteralNode`, `VariableNode`).
- **Optimizers:** Applies constant folding and algebraic simplification passes.
- **Generators:** Implements `ByteBuddyRuleGenerator` and `AsmRuleGenerator`.
- **ClassLoaders:** `ClassLoaderManager` handles dynamic class generation and Metaspace safety.
- **Cache:** `TieredRuleCache` manages 3-tiered JVM reference lifecycles.
- **Executors:** `SyncExecutor`, `AsyncExecutor`, and `BatchExecutor`.
- **CLI & TUI:** Picocli command dispatcher and Lanterna ANSI terminal dashboard.

### 3. `engine-profiler` (Observability & Telemetry)
- Tracks HotSpot compilation logs (`-XX:+PrintCompilation`).
- Evaluates Safepoint Time-To-Safepoint (TTSP) delays.
- Connects with JDK Flight Recorder (`JfrRecordingManager`).

### 4. `engine-agent` (Bytecode Instrumentation)
- Attachable `-javaagent` using `ByteBuddyAgent` and ASM `ClassFileTransformer`.
- Performs real-time memory inspection with Java Object Layout (JOL).
- Exposes runtime metrics via JMX MBeans (`HelixEngineMXBean`).

### 5. `engine-experiments` (Benchmarks & Research)
- Reproducible JVM behavior experiments: `metaspace`, `jit`, `gc`, `layout`, `safepoint`.
- JMH microbenchmark suites measuring compilation latency and execution throughput.
