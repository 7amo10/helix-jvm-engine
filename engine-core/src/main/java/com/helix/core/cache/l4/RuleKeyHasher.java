package com.helix.core.cache.l4;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Deterministic SHA-256 key generator for compiled rules across distributed cluster nodes.
 */
public final class RuleKeyHasher {

    private static final String DEFAULT_ENGINE_VERSION = "1.0.0";

    private RuleKeyHasher() {}

    /**
     * Computes a deterministic SHA-256 hash for the given rule content.
     *
     * @param ruleContent raw rule JSON or expression string
     * @return 64-character lowercase hexadecimal SHA-256 hash
     */
    public static String hashRule(String ruleContent) {
        return hashRule(ruleContent, DEFAULT_ENGINE_VERSION);
    }

    /**
     * Computes a deterministic SHA-256 hash incorporating the engine build version.
     * This prevents incompatible bytecode from being loaded if the engine compiler evolves.
     *
     * @param ruleContent   raw rule JSON or expression string
     * @param engineVersion engine compiler version
     * @return 64-character lowercase hexadecimal SHA-256 hash
     */
    public static String hashRule(String ruleContent, String engineVersion) {
        Objects.requireNonNull(ruleContent, "ruleContent cannot be null");
        String version = engineVersion != null ? engineVersion : DEFAULT_ENGINE_VERSION;
        String canonicalInput = version + ":" + ruleContent;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonicalInput.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }
}
