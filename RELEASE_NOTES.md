## Helix JVM Scripting Engine & Deep Profiling Platform (v1.0.0-SNAPSHOT)

Helix is a high-performance dynamic script compilation engine, JVM profiler, and interactive terminal application designed for ultra-low latency rule evaluation.

---

### Feature Highlights & Merged Pull Requests

- **End-to-End Integration Test Suite ([#67](https://github.com/7amo10/helix-jvm-engine/issues/67))**: Full lifecycle test coverage across AST parser, ByteBuddy/ASM generators, tiered caches, and async executors.
- **Main CLI Application & Interactive TUI Dashboard ([#68](https://github.com/7amo10/helix-jvm-engine/issues/68))**: Picocli CLI runner with subcommands (`compile`, `execute`, `profile`, `experiment`) and Lanterna terminal dashboard.
- **Comprehensive Documentation & Architecture Flow ([#69](https://github.com/7amo10/helix-jvm-engine/issues/69), [#70](https://github.com/7amo10/helix-jvm-engine/issues/70))**: System architecture diagrams, badges, and detailed READMEs for all 5 submodules.
- **Aggregate Javadoc Generation ([#71](https://github.com/7amo10/helix-jvm-engine/issues/71))**: HTML5 Javadoc generation across public engine APIs.
- **GitHub Actions CI/CD & JMH Benchmarking Gate ([#72](https://github.com/7amo10/helix-jvm-engine/issues/72))**: Multi-OS build workflows with continuous JMH regression monitoring.
- **Enterprise Rules Catalog ([#73](https://github.com/7amo10/helix-jvm-engine/issues/73))**: 10+ production JSON rule templates (fraud detection, credit scoring, pricing).
- **Performance Tuning & Mechanical Sympathy Guides ([#74](https://github.com/7amo10/helix-jvm-engine/issues/74))**: HotSpot GC tuning matrix, AppCDS setup, and JOL memory footprint analysis.
- **Distribution Packaging ([#75](https://github.com/7amo10/helix-jvm-engine/issues/75))**: Maven assembly distribution ZIP containing standalone executables, Java Agent, and docs.
- **Production Readiness Verification ([#76](https://github.com/7amo10/helix-jvm-engine/issues/76))**: 100% build & test suite verification across all submodules.

---

### How to Use the Release Package

#### 1. Download & Extract Distribution
```bash
unzip helix-jvm-engine-1.0.0-SNAPSHOT-bin.zip
cd helix-jvm-engine-1.0.0-SNAPSHOT
```

#### 2. Compile & Execute Business Rules
```bash
# Compile JSON Rule
./scripts/start-helix.sh compile --rule examples/rules/fraud-detection.json --output json

# Execute Rule against Input Context
./scripts/start-helix.sh execute --rule examples/rules/credit-approval.json --context examples/rules/sample-context.json --mode async --output json
```

#### 3. Launch Interactive TUI Dashboard
```bash
./scripts/start-helix.sh profile --tui
```

#### 4. Execute JVM Behavior Experiments
```bash
./scripts/run-experiment.sh jit
./scripts/run-experiment.sh metaspace
```
