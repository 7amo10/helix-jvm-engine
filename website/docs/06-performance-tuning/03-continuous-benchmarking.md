---
id: continuous-benchmarking
title: JMH Benchmarks & CI Regression Gates
sidebar_position: 3
---

# Continuous Benchmarking with JMH

Because Helix generates dynamic bytecode, the project enforces automated **JMH (Java Microbenchmark Harness)** throughput and latency checks on every pull request.

---

## Benchmark Suite Overview

The `engine-experiments` module contains 3 JMH benchmark suites:
1. **`CompilationBenchmark`:** Measures compilation latency for simple and complex rules across ByteBuddy vs ASM generators.
2. **`ExecutionBenchmark`:** Measures evaluation throughput (ops/second) across cold and hot JIT tiers.
3. **`CacheBenchmark`:** Measures L1/L2/L3 cache lookup latencies (sub-12ns to 80ns).

---

## Running JMH Benchmarks Locally

```bash
mvn exec:java -pl engine-experiments \
    -Dexec.mainClass="com.helix.experiments.benchmarks.BenchmarkRunner"
```

### Typical Benchmark Output

```text
Benchmark                                           Mode  Cnt    Score   Error  Units
CompilationBenchmark.benchmarkComplexRuleAsm        avgt    2  217.468          us/op
CompilationBenchmark.benchmarkComplexRuleByteBuddy  avgt    2  764.912          us/op
CompilationBenchmark.benchmarkSimpleRuleAsm         avgt    2  199.178          us/op
CompilationBenchmark.benchmarkSimpleRuleByteBuddy   avgt    2  535.981          us/op
```

---

## Automated GitHub Actions Regression Gate

The `.github/workflows/benchmark-pr.yml` workflow runs benchmarks on every pull request. If any commit introduces a statistically significant performance drop (> 5% regression in throughput), the CI build fails and a GitHub Actions bot posts a comparative delta report directly on the pull request.
