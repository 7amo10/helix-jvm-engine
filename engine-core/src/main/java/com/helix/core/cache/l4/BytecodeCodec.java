package com.helix.core.cache.l4;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Binary serialization codec for compiled JVM rule bytecode and associated metadata.
 * Uses a compact binary framing format with the magic header 0x48454C58 ("HELX").
 */
public final class BytecodeCodec {

    public static final int MAGIC_HEADER = 0x48454C58; // "HELX"
    public static final short FORMAT_VERSION_1 = 1;

    private BytecodeCodec() {}

    /**
     * Serializes a SerializedRuleData instance into a binary payload.
     *
     * @param data rule metadata and bytecode
     * @return serialized binary array
     */
    public static byte[] encode(SerializedRuleData data) {
        Objects.requireNonNull(data, "SerializedRuleData cannot be null");

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(baos)) {

            out.writeInt(MAGIC_HEADER);
            out.writeShort(FORMAT_VERSION_1);
            out.writeUTF(data.getRuleName());
            out.writeUTF(data.getVersion());
            out.writeLong(data.getCompiledTimestamp());
            out.writeUTF(data.getFingerprint());

            byte[] bytecode = data.getBytecode();
            out.writeInt(bytecode.length);
            out.write(bytecode);
            out.flush();

            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode rule bytecode payload", e);
        }
    }

    /**
     * Deserializes a binary payload into a SerializedRuleData instance.
     *
     * @param bytes serialized binary array
     * @return deserialized SerializedRuleData
     * @throws IllegalArgumentException if payload has invalid magic header or is corrupted/truncated
     */
    public static SerializedRuleData decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "Bytecode payload bytes cannot be null");

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             DataInputStream in = new DataInputStream(bais)) {

            int magic = in.readInt();
            if (magic != MAGIC_HEADER) {
                throw new IllegalArgumentException(
                        "Invalid bytecode payload magic header: 0x" + Integer.toHexString(magic).toUpperCase()
                );
            }

            short formatVersion = in.readShort();
            if (formatVersion != FORMAT_VERSION_1) {
                throw new IllegalArgumentException("Unsupported format version: " + formatVersion);
            }

            String ruleName = in.readUTF();
            String version = in.readUTF();
            long timestamp = in.readLong();
            String fingerprint = in.readUTF();

            int length = in.readInt();
            if (length < 0 || length > 50 * 1024 * 1024) { // 50MB sanity limit
                throw new IllegalArgumentException("Invalid bytecode length: " + length);
            }

            byte[] bytecode = new byte[length];
            in.readFully(bytecode);

            return new SerializedRuleData(ruleName, version, bytecode, timestamp, fingerprint);
        } catch (IOException e) {
            throw new IllegalArgumentException("Corrupted bytecode payload", e);
        }
    }

    /**
     * Calculates a deterministic SHA-256 hash for rule content using RuleKeyHasher.
     *
     * @param ruleContent raw rule definition or expression
     * @return 64-character lowercase hexadecimal hash
     */
    public static String calculateRuleHash(String ruleContent) {
        return RuleKeyHasher.hashRule(ruleContent);
    }
}
