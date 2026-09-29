package com.helix.api.sandbox;

/**
 * Declares the trust level of the submitting tenant for a rule definition.
 * Governs whether the rule compilation pipeline enforces the capability sandbox.
 */
public enum TenantTrustLevel {

    /**
     * Internal / system-level rules authored by trusted internal developers.
     * Bypasses AST capability inspection and executes with standard JVM permissions.
     */
    TRUSTED(false),

    /**
     * External, partner, or multi-tenant rules authored by untrusted parties.
     * Subjected to strict AST-level capability policy validation and isolated ClassLoader execution.
     */
    UNTRUSTED(true);

    private final boolean sandboxed;

    TenantTrustLevel(boolean sandboxed) {
        this.sandboxed = sandboxed;
    }

    /**
     * Returns true if this trust level requires sandboxed execution.
     *
     * @return true if sandboxed, false if trusted
     */
    public boolean isSandboxed() {
        return sandboxed;
    }

    /**
     * Maps a boolean sandboxed flag to the corresponding {@link TenantTrustLevel}.
     *
     * @param sandboxed true for UNTRUSTED, false for TRUSTED
     * @return UNTRUSTED if sandboxed is true, otherwise TRUSTED
     */
    public static TenantTrustLevel fromBoolean(boolean sandboxed) {
        return sandboxed ? UNTRUSTED : TRUSTED;
    }

    /**
     * Parses a string representation into a {@link TenantTrustLevel}.
     * Case-insensitive. Null or blank input defaults to {@link #TRUSTED} for backward compatibility.
     *
     * @param value string name of trust level
     * @return resolved TenantTrustLevel
     * @throws IllegalArgumentException if value is non-blank but does not match any trust level
     */
    public static TenantTrustLevel fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return TRUSTED;
        }
        String normalized = value.trim().toUpperCase();
        return valueOf(normalized);
    }
}
