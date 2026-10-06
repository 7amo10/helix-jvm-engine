package com.helix.experiments.benchmarks;

import org.openjdk.jmh.annotations.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark suite measuring single-core cryptographic SHA-256 event JSON hashing throughput
 * and Merkle leaf computation/chaining speed for audit trails.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@State(Scope.Thread)
@Fork(0)
public class AuditHashingBenchmark {

    private MessageDigest digest;
    private byte[] eventJsonBytes;
    private byte[] prevHashBytes;
    private byte[] eventHashBytes;

    @Setup
    public void setup() throws NoSuchAlgorithmException {
        this.digest = MessageDigest.getInstance("SHA-256");

        String sampleEventJson = """
                {"durationNanos":124500,"evaluatedAt":"2026-10-01T12:00:00Z","executionResult":"PASSED","recordSeq":10001,"ruleName":"BenchmarkSecurityRule","ruleVersion":"1.0.0","tenantId":"tenant-alpha"}
                """.trim();
        this.eventJsonBytes = sampleEventJson.getBytes(StandardCharsets.UTF_8);

        // Precompute sample hashes for Merkle leaf chaining benchmark
        byte[] rawEventHash = digest.digest(eventJsonBytes);
        digest.reset();
        String eventHashHex = HexFormat.of().formatHex(rawEventHash);
        String prevHashHex = HexFormat.of().formatHex(digest.digest("genesis-seed".getBytes(StandardCharsets.UTF_8)));
        digest.reset();

        this.prevHashBytes = prevHashHex.getBytes(StandardCharsets.UTF_8);
        this.eventHashBytes = eventHashHex.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Measures SHA-256 hashing throughput of canonical audit event JSON.
     * Acceptance criterion: > 1,000,000 events/sec.
     */
    @Benchmark
    public byte[] benchmarkSha256EventHashing() {
        digest.reset();
        return digest.digest(eventJsonBytes);
    }

    /**
     * Measures Merkle leaf computation and chaining speed: SHA-256(prev_hash || event_hash).
     */
    @Benchmark
    public byte[] benchmarkMerkleLeafChaining() {
        digest.reset();
        digest.update(prevHashBytes);
        digest.update(eventHashBytes);
        return digest.digest();
    }
}
