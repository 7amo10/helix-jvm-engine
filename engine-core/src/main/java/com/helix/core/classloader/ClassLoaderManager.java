package com.helix.core.classloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for orchestrating RuleClassLoader instances across ISOLATED, SHARED, and HIERARCHICAL isolation modes.
 */
public class ClassLoaderManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ClassLoaderManager.class);

    private final IsolationMode isolationMode;
    private final ClassLoaderMetrics metrics;
    private final ClassLoaderLeakDetector leakDetector;
    private final Map<String, RuleClassLoader> activeLoaders = new ConcurrentHashMap<>();
    private final SharedUtilityClassLoader sharedUtilityParent;
    private volatile RuleClassLoader globalSharedLoader;

    public ClassLoaderManager() {
        this(IsolationMode.HIERARCHICAL);
    }

    public ClassLoaderManager(IsolationMode isolationMode) {
        this(isolationMode, new ClassLoaderMetrics(), new ClassLoaderLeakDetector());
    }

    public ClassLoaderManager(IsolationMode isolationMode, ClassLoaderMetrics metrics, ClassLoaderLeakDetector leakDetector) {
        this.isolationMode = Objects.requireNonNull(isolationMode, "isolationMode cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
        this.leakDetector = Objects.requireNonNull(leakDetector, "leakDetector cannot be null");
        this.sharedUtilityParent = SharedUtilityClassLoader.getInstance();
    }

    public IsolationMode getIsolationMode() {
        return isolationMode;
    }

    public ClassLoaderMetrics getMetrics() {
        return metrics;
    }

    public ClassLoaderLeakDetector getLeakDetector() {
        return leakDetector;
    }

    /**
     * Gets or creates a RuleClassLoader based on the configured IsolationMode.
     *
     * @param category rule category (used in HIERARCHICAL mode)
     * @param ruleName rule name (used in ISOLATED mode)
     * @return RuleClassLoader instance
     */
    public synchronized RuleClassLoader getOrCreateClassLoader(String category, String ruleName) {
        return switch (isolationMode) {
            case ISOLATED -> {
                String loaderId = "isolated_" + sanitize(ruleName) + "_" + System.nanoTime();
                RuleClassLoader loader = new RuleClassLoader(loaderId, ClassLoaderManager.class.getClassLoader(), metrics);
                activeLoaders.put(loaderId, loader);
                leakDetector.track(loader);
                yield loader;
            }
            case SHARED -> {
                if (globalSharedLoader == null || globalSharedLoader.isClosed()) {
                    String loaderId = "shared_global";
                    globalSharedLoader = new RuleClassLoader(loaderId, sharedUtilityParent, metrics);
                    activeLoaders.put(loaderId, globalSharedLoader);
                    leakDetector.track(globalSharedLoader);
                }
                yield globalSharedLoader;
            }
            case HIERARCHICAL -> {
                String catKey = (category == null || category.isBlank()) ? "DEFAULT" : category.toUpperCase();
                String loaderId = "hierarchical_" + catKey;
                yield activeLoaders.computeIfAbsent(loaderId, id -> {
                    RuleClassLoader loader = new RuleClassLoader(id, sharedUtilityParent, metrics);
                    leakDetector.track(loader);
                    return loader;
                });
            }
        };
    }

    public synchronized void closeLoader(String loaderId) {
        RuleClassLoader loader = activeLoaders.remove(loaderId);
        if (loader != null) {
            loader.close();
            log.info("Closed RuleClassLoader: {}", loaderId);
        }
    }

    public Map<String, RuleClassLoader> getActiveLoaders() {
        return Collections.unmodifiableMap(activeLoaders);
    }

    @Override
    public synchronized void close() {
        activeLoaders.values().forEach(RuleClassLoader::close);
        activeLoaders.clear();
        if (globalSharedLoader != null) {
            globalSharedLoader.close();
            globalSharedLoader = null;
        }
        log.info("ClassLoaderManager closed and cleaned up all active ClassLoaders.");
    }

    private String sanitize(String str) {
        return str == null ? "anon" : str.replaceAll("[^a-zA-Z0-9_]", "_");
    }
}
