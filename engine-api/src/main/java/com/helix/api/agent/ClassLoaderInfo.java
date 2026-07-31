package com.helix.api.agent;

import java.time.Instant;

/**
 * Record holding metadata about a registered ClassLoader in the JVM.
 */
public record ClassLoaderInfo(
        String name,
        int loadedClassCount,
        long totalMemoryBytes,
        String parentName,
        Instant createdTimestamp
) {
    public ClassLoaderInfo {
        if (createdTimestamp == null) {
            createdTimestamp = Instant.now();
        }
    }
}
