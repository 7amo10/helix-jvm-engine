package com.helix.core.cache.l4;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class BytecodeCodecTest {

    @Test
    @DisplayName("Encode and decode roundtrip preserves all fields and bytecode integrity")
    void testEncodeAndDecodeBytecodePayload() {
        String ruleName = "FraudVelocityRule";
        String version = "1.0.0";
        byte[] sampleBytecode = "CAFEBABE_MOCK_BYTECODE_STREAM_FOR_HELIX".getBytes(StandardCharsets.UTF_8);
        long compiledTimestamp = System.currentTimeMillis();
        String fingerprint = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        SerializedRuleData data = new SerializedRuleData(ruleName, version, sampleBytecode, compiledTimestamp, fingerprint);
        byte[] serialized = BytecodeCodec.encode(data);

        assertNotNull(serialized);
        assertTrue(serialized.length > sampleBytecode.length);

        SerializedRuleData decoded = BytecodeCodec.decode(serialized);
        assertEquals(ruleName, decoded.getRuleName());
        assertEquals(version, decoded.getVersion());
        assertArrayEquals(sampleBytecode, decoded.getBytecode());
        assertEquals(compiledTimestamp, decoded.getCompiledTimestamp());
        assertEquals(fingerprint, decoded.getFingerprint());
        assertEquals(data, decoded);
        assertEquals(data.hashCode(), decoded.hashCode());
    }

    @Test
    @DisplayName("Rejects payload with invalid magic header")
    void testInvalidMagicHeaderThrowsException() {
        byte[] corrupted = new byte[]{0x00, 0x00, 0x00, 0x00, 0x01, 0x02};
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> BytecodeCodec.decode(corrupted)
        );
        assertTrue(exception.getMessage().contains("Invalid bytecode payload magic header"));
    }

    @Test
    @DisplayName("Rejects truncated or corrupted payload gracefully")
    void testCorruptedPayloadThrowsException() {
        byte[] valid = BytecodeCodec.encode(new SerializedRuleData(
                "TestRule", "1.0.0", new byte[]{1, 2, 3, 4}, System.currentTimeMillis(), "hash123"
        ));
        byte[] truncated = Arrays.copyOf(valid, valid.length / 2);

        assertThrows(
                IllegalArgumentException.class,
                () -> BytecodeCodec.decode(truncated)
        );
    }

    @Test
    @DisplayName("Deterministic SHA-256 rule hashing")
    void testCalculateSha256RuleHash() {
        String jsonRule = "{\"name\":\"TestRule\",\"expression\":\"amount > 100\"}";
        String hash1 = BytecodeCodec.calculateRuleHash(jsonRule);
        String hash2 = BytecodeCodec.calculateRuleHash(jsonRule);

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);

        String hasherResult = RuleKeyHasher.hashRule(jsonRule);
        assertEquals(hash1, hasherResult);
    }

    @Test
    @DisplayName("Hashing accounts for engine build version differences")
    void testRuleKeyHasherWithEngineVersion() {
        String jsonRule = "{\"name\":\"TestRule\",\"expression\":\"amount > 100\"}";
        String hashV1 = RuleKeyHasher.hashRule(jsonRule, "1.0.0");
        String hashV2 = RuleKeyHasher.hashRule(jsonRule, "2.0.0");

        assertNotEquals(hashV1, hashV2);
        assertEquals(64, hashV1.length());
        assertEquals(64, hashV2.length());
    }

    @Test
    @DisplayName("Large bytecode payload roundtrip (128 KB)")
    void testLargeBytecodePayload() {
        byte[] largeBytecode = new byte[128 * 1024];
        Arrays.fill(largeBytecode, (byte) 0x42);

        SerializedRuleData data = new SerializedRuleData(
                "LargeRule", "2.1.0", largeBytecode, System.currentTimeMillis(), "largehash"
        );

        byte[] encoded = BytecodeCodec.encode(data);
        SerializedRuleData decoded = BytecodeCodec.decode(encoded);

        assertEquals("LargeRule", decoded.getRuleName());
        assertArrayEquals(largeBytecode, decoded.getBytecode());
    }

    @Test
    @DisplayName("Null arguments validation")
    void testNullArgumentsValidation() {
        assertThrows(NullPointerException.class, () -> BytecodeCodec.encode(null));
        assertThrows(NullPointerException.class, () -> BytecodeCodec.decode(null));
        assertThrows(NullPointerException.class, () -> RuleKeyHasher.hashRule(null));
    }
}
