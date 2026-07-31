# Helix - JVM Scripting Engine & Profiler

**Unravel the JVM, one bytecode at a time.**

A production-grade JVM internals exploration platform that combines a dynamic rules engine with deep profiling capabilities.

## Project Status

**Version:** 1.0.0-SNAPSHOT  
**Development Timeline:** 7 Days (Aug 1-7, 2026)  
**Current Phase:** Planning Complete, Ready for Implementation  

## Overview

Helix is a multi-module Maven project for learning JVM internals through hands-on experimentation:

- Dynamic bytecode generation using ByteBuddy and ASM
- Hierarchical ClassLoader strategies
- Three-tier cache with Strong, Soft, and Weak references
- Java Agent instrumentation with ASM transformation
- JIT and GC profiling with async-profiler and JFR
- JVM experiments (Metaspace, JIT, GC, Object Layout)
- Performance benchmarks with JMH

## Architecture

### Modules

```
helix-jvm-engine/
├── engine-api          # Public interfaces (dependency-free)
├── engine-core         # Rules engine, bytecode generation, execution
├── engine-profiler     # JIT/GC monitoring, async-profiler, JFR
├── engine-agent        # Java Agent for instrumentation
└── engine-experiments  # JVM experiments and JMH benchmarks
```

### Technology Stack

- **Java:** 17+ (LTS)
- **Build:** Maven 3.9+
- **Bytecode:** ByteBuddy 1.14+, ASM 9.5+
- **Caching:** Caffeine 3.1+
- **Testing:** JUnit 5, JMH
- **Profiling:** async-profiler, JOL (Java Object Layout)
- **Observability:** SLF4J, Logback, JMX, JFR

## Getting Started

(Will be updated as implementation progresses)

### Prerequisites

- JDK 17 or higher
- Maven 3.9+
- Git

### Build

```bash
git clone https://github.com/yourusername/helix-jvm-engine.git
cd helix-jvm-engine
mvn clean install
```

## License

Apache License 2.0

## Acknowledgments

Inspired by "Optimizing Java" by Benjamin J. Evans, James Gough, Chris Newland

