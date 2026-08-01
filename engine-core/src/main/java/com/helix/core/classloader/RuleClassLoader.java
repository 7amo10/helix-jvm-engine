package com.helix.core.classloader;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom URLClassLoader for dynamically defining and executing compiled rule bytecode.
 */
public class RuleClassLoader extends URLClassLoader implements AutoCloseable {

    private final String loaderId;
    private final Map<String, Class<?>> loadedClasses = new ConcurrentHashMap<>();
    private final ClassLoaderMetrics metrics;
    private volatile boolean closed = false;

    public RuleClassLoader(String loaderId, ClassLoader parent) {
        this(loaderId, parent, new ClassLoaderMetrics());
    }

    public RuleClassLoader(String loaderId, ClassLoader parent, ClassLoaderMetrics metrics) {
        super(new URL[0], parent);
        this.loaderId = Objects.requireNonNull(loaderId, "loaderId cannot be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics cannot be null");
        this.metrics.incrementCreatedLoaders();
    }

    public String getLoaderId() {
        return loaderId;
    }

    /**
     * Defines a new class from raw bytecode.
     *
     * @param className FQCN of the class
     * @param byteCode  raw byte array of class bytecode
     * @return loaded Class object
     * @throws ClassLoadingException if defining class fails or loader is closed
     */
    public synchronized Class<?> defineRule(String className, byte[] byteCode) throws ClassLoadingException {
        if (closed) {
            throw new ClassLoadingException("Cannot define rule class '" + className + "' on closed RuleClassLoader: " + loaderId);
        }
        Objects.requireNonNull(className, "className cannot be null");
        Objects.requireNonNull(byteCode, "byteCode cannot be null");

        if (loadedClasses.containsKey(className)) {
            return loadedClasses.get(className);
        }

        try {
            Class<?> clazz = defineClass(className, byteCode, 0, byteCode.length);
            loadedClasses.put(className, clazz);
            metrics.incrementClassesLoaded();
            return clazz;
        } catch (Throwable t) {
            throw new ClassLoadingException("Failed to define class '" + className + "' in loader: " + loaderId, t);
        }
    }

    public Map<String, Class<?>> getLoadedClasses() {
        return Collections.unmodifiableMap(loadedClasses);
    }

    public boolean isClosed() {
        return closed;
    }

    public ClassLoaderMetrics getMetrics() {
        return metrics;
    }

    @Override
    public void close() {
        if (!closed) {
            synchronized (this) {
                if (!closed) {
                    try {
                        super.close();
                    } catch (Exception ignored) {
                    }
                    loadedClasses.clear();
                    closed = true;
                    metrics.incrementClosedLoaders();
                }
            }
        }
    }
}
