package com.helix.core.cache.l4;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable metadata and bytecode container for distributed serialization and cache storage.
 */
public final class SerializedRuleData {

    private final String ruleName;
    private final String version;
    private final byte[] bytecode;
    private final long compiledTimestamp;
    private final String fingerprint;

    public SerializedRuleData(String ruleName, String version, byte[] bytecode, long compiledTimestamp) {
        this(ruleName, version, bytecode, compiledTimestamp, "");
    }

    public SerializedRuleData(String ruleName, String version, byte[] bytecode, long compiledTimestamp, String fingerprint) {
        this.ruleName = Objects.requireNonNull(ruleName, "ruleName cannot be null");
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.bytecode = Objects.requireNonNull(bytecode, "bytecode cannot be null").clone();
        this.compiledTimestamp = compiledTimestamp;
        this.fingerprint = fingerprint != null ? fingerprint : "";
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getVersion() {
        return version;
    }

    public byte[] getBytecode() {
        return bytecode.clone();
    }

    public long getCompiledTimestamp() {
        return compiledTimestamp;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SerializedRuleData that = (SerializedRuleData) o;
        return compiledTimestamp == that.compiledTimestamp &&
                ruleName.equals(that.ruleName) &&
                version.equals(that.version) &&
                Arrays.equals(bytecode, that.bytecode) &&
                fingerprint.equals(that.fingerprint);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(ruleName, version, compiledTimestamp, fingerprint);
        result = 31 * result + Arrays.hashCode(bytecode);
        return result;
    }

    @Override
    public String toString() {
        return "SerializedRuleData{" +
                "ruleName='" + ruleName + '\'' +
                ", version='" + version + '\'' +
                ", bytecodeLength=" + bytecode.length +
                ", compiledTimestamp=" + compiledTimestamp +
                ", fingerprint='" + fingerprint + '\'' +
                '}';
    }
}
