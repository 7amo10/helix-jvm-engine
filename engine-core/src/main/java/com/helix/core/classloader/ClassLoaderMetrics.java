package com.helix.core.classloader;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ClassLoaderMetrics {

    private final AtomicInteger activeClassLoaders = new AtomicInteger(0);
    private final AtomicLong totalClassesLoaded = new AtomicLong(0);
    private final AtomicLong totalClassLoadersClosed = new AtomicLong(0);

    public void incrementCreatedLoaders() {
        activeClassLoaders.incrementAndGet();
    }

    public void incrementClosedLoaders() {
        activeClassLoaders.decrementAndGet();
        totalClassLoadersClosed.incrementAndGet();
    }

    public void incrementClassesLoaded() {
        totalClassesLoaded.incrementAndGet();
    }

    public int getActiveClassLoaders() {
        return activeClassLoaders.get();
    }

    public long getTotalClassesLoaded() {
        return totalClassesLoaded.get();
    }

    public long getTotalClassLoadersClosed() {
        return totalClassLoadersClosed.get();
    }
}
