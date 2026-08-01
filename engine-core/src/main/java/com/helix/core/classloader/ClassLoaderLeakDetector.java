package com.helix.core.classloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for monitoring and detecting un-garbage-collected (leaked) ClassLoaders.
 */
public class ClassLoaderLeakDetector {

    private static final Logger log = LoggerFactory.getLogger(ClassLoaderLeakDetector.class);
    private final List<TrackedLoader> trackedLoaders = new ArrayList<>();

    public record TrackedLoader(String id, WeakReference<RuleClassLoader> reference, long createdTimestamp) {}

    public synchronized void track(RuleClassLoader loader) {
        if (loader != null) {
            trackedLoaders.add(new TrackedLoader(loader.getLoaderId(), new WeakReference<>(loader), System.currentTimeMillis()));
        }
    }

    public synchronized List<TrackedLoader> getLeakedLoaders() {
        System.gc(); // hint garbage collection
        List<TrackedLoader> leaks = new ArrayList<>();
        for (TrackedLoader tracked : trackedLoaders) {
            RuleClassLoader loader = tracked.reference().get();
            if (loader != null && loader.isClosed()) {
                leaks.add(tracked);
                log.warn("Potential ClassLoader leak detected: loaderId='{}' was closed but is still referenced in heap", tracked.id());
            }
        }
        return leaks;
    }

    public synchronized int getTrackedCount() {
        return trackedLoaders.size();
    }
}
