package com.helix.core.classloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Utility for monitoring and detecting un-garbage-collected (leaked) ClassLoaders.
 */
public class ClassLoaderLeakDetector {

    private static final Logger log = LoggerFactory.getLogger(ClassLoaderLeakDetector.class);
    private final List<TrackedLoader> trackedLoaders = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    public record TrackedLoader(String id, WeakReference<RuleClassLoader> reference, long createdTimestamp) {}

    public void track(RuleClassLoader loader) {
        if (loader != null) {
            lock.lock();
            try {
                trackedLoaders.add(new TrackedLoader(loader.getLoaderId(), new WeakReference<>(loader), System.currentTimeMillis()));
            } finally {
                lock.unlock();
            }
        }
    }

    public List<TrackedLoader> getLeakedLoaders() {
        System.gc(); // hint garbage collection
        List<TrackedLoader> leaks = new ArrayList<>();
        lock.lock();
        try {
            for (TrackedLoader tracked : trackedLoaders) {
                RuleClassLoader loader = tracked.reference().get();
                if (loader != null && loader.isClosed()) {
                    leaks.add(tracked);
                    log.warn("Potential ClassLoader leak detected: loaderId='{}' was closed but is still referenced in heap", tracked.id());
                }
            }
            return leaks;
        } finally {
            lock.unlock();
        }
    }

    public int getTrackedCount() {
        lock.lock();
        try {
            return trackedLoaders.size();
        } finally {
            lock.unlock();
        }
    }
}
