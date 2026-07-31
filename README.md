# Helix - JVM Scripting Engine & Profiler

**Unravel the JVM, one bytecode at a time.**

A production-grade JVM internals exploration platform that combines a dynamic rules engine with deep profiling capabilities.

## Project Status

**Version:** 1.0.0-SNAPSHOT  
**Development Timeline:** 7 Days (7 Sprints)  
**Current Phase:** Sprint 1 - Foundation & API Layer  

---

## Overview

Helix is a multi-module Maven project for exploring and mastering JVM internals through hands-on experimentation:

- **Dynamic Bytecode Generation:** Built with ByteBuddy (primary) and ASM (experimental low-level).
- **Custom ClassLoader Hierarchy:** Isolated, shared, and hierarchical ClassLoaders.
- **Tiered Cache Engine:** L1 Strong (Caffeine), L2 SoftReference, L3 WeakReference.
- **Java Agent Instrumentation:** ASM bytecode transformations and JOL memory layout analysis.
- **JIT & GC Observability:** Async-profiler integration, custom JFR events, and JMX telemetry.
- **JVM Experiments & Benchmarks:** JMH microbenchmarks for Metaspace, GC pressure, and JIT compilation.

---

## Architecture

### Modules

```
helix-jvm-engine/
├── engine-api          # Public interfaces and telemetry events (dependency-free)
├── engine-core         # Rule compilation, AST parser, ClassLoaders, execution engine
├── engine-profiler     # JIT/GC monitoring, async-profiler, JFR events
├── engine-agent        # Java Agent for bytecode instrumentation & memory layout
└── engine-experiments  # JVM experiments, memory stress tests, and JMH benchmarks
```

### Technology Stack

- **Java:** 17+ (LTS)
- **Build:** Maven 3.9+
- **Bytecode Generation:** ByteBuddy 1.14.9, ASM 9.5
- **Caching:** Caffeine 3.1.8
- **Testing & Benchmarks:** JUnit 5.10.0, JMH 1.37
- **Profiling & Memory:** async-profiler, JOL (Java Object Layout) 0.17
- **Observability:** SLF4J 2.0.9, Logback 1.4.11, JMX, JFR

---

## Git & Branching Strategy

This project strictly follows modern Agile/PM Git best practices:

- **`main`**: Production-ready, stable releases.
- **`develop`**: Primary integration branch where all completed features and task PRs are merged.
- **`task/<sprint>.<task>-<description>`**: Dedicated feature/task branches created off `develop` for each specific task (e.g., `task/1.1-project-structure-setup`).

---

## Getting Started

### Prerequisites

- JDK 17 or higher (`java -version`)
- Maven 3.9+ (`mvn -version`)
- Git (`git --version`)

### Build & Run Tests

```bash
# Clone repository
git clone https://github.com/7amo10/helix-jvm-engine.git
cd helix-jvm-engine

# Checkout develop branch
git checkout develop

# Build all modules & run test suite
mvn clean install
```

---

## License

Apache License 2.0

## Acknowledgments

Inspired by *"Optimizing Java"* by Benjamin J. Evans, James Gough, Chris Newland.
