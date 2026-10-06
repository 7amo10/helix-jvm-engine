package com.helix.experiments.benchmarks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class Phase4BenchmarkSanityTest {

    @Test
    void testSandboxBenchmarkClassIsInstantiable() {
        assertDoesNotThrow(() -> {
            SandboxOverheadBenchmark benchmark = new SandboxOverheadBenchmark();
            benchmark.setup();
            benchmark.benchmarkTrustedCompilation();
            benchmark.benchmarkSandboxedCompilation();
            benchmark.benchmarkAstCapabilityWalkOnly();
        });
    }

    @Test
    void testAuditHashingBenchmarkClassIsInstantiable() {
        assertDoesNotThrow(() -> {
            AuditHashingBenchmark benchmark = new AuditHashingBenchmark();
            benchmark.setup();
            benchmark.benchmarkSha256EventHashing();
            benchmark.benchmarkMerkleLeafChaining();
        });
    }
}
