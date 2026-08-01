package com.helix.core.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

public class ReferenceManager {

    private static final Logger log = LoggerFactory.getLogger(ReferenceManager.class);

    private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();

    public static class KeyedSoftReference<K, V> extends SoftReference<V> {
        private final K key;

        public KeyedSoftReference(K key, V value, ReferenceQueue<Object> q) {
            super(value, (ReferenceQueue<? super V>) q);
            this.key = key;
        }

        public K getKey() {
            return key;
        }
    }

    public static class KeyedWeakReference<K, V> extends WeakReference<V> {
        private final K key;

        public KeyedWeakReference(K key, V value, ReferenceQueue<Object> q) {
            super(value, (ReferenceQueue<? super V>) q);
            this.key = key;
        }

        public K getKey() {
            return key;
        }
    }

    public ReferenceQueue<Object> getReferenceQueue() {
        return referenceQueue;
    }

    public void processQueue() {
        Reference<?> ref;
        int clearedCount = 0;
        while ((ref = referenceQueue.poll()) != null) {
            clearedCount++;
        }
        if (clearedCount > 0) {
            log.debug("Processed {} garbage-collected reference entries from queue", clearedCount);
        }
    }
}
