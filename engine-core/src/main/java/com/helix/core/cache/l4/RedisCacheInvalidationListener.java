package com.helix.core.cache.l4;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Background listener running in a dedicated Java 21 virtual thread,
 * subscribing to Redis Pub/Sub cluster invalidation messages.
 */
public class RedisCacheInvalidationListener implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheInvalidationListener.class);
    public static final String DEFAULT_CHANNEL = "helix:cache:invalidation";

    private final JedisPool jedisPool;
    private final String channel;
    private final CacheInvalidationSubscriber subscriber;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread listenerThread;

    public RedisCacheInvalidationListener(JedisPool jedisPool, Consumer<String> onInvalidate) {
        this(jedisPool, DEFAULT_CHANNEL, onInvalidate);
    }

    public RedisCacheInvalidationListener(JedisPool jedisPool, String channel, Consumer<String> onInvalidate) {
        this.jedisPool = Objects.requireNonNull(jedisPool, "jedisPool cannot be null");
        this.channel = channel != null ? channel : DEFAULT_CHANNEL;
        this.subscriber = new CacheInvalidationSubscriber(onInvalidate);
    }

    /**
     * Starts listening for Redis cache invalidation events in a dedicated virtual thread.
     */
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            listenerThread = Thread.ofVirtual()
                    .name("helix-redis-invalidation-listener")
                    .start(() -> {
                        log.info("Starting Redis cache invalidation listener on channel '{}'", channel);
                        while (running.get() && !Thread.currentThread().isInterrupted()) {
                            try (Jedis jedis = jedisPool.getResource()) {
                                jedis.subscribe(subscriber, channel);
                            } catch (Exception e) {
                                if (running.get()) {
                                    log.warn("Redis pub/sub connection lost on channel '{}', retrying in 500ms...", channel, e);
                                    try {
                                        Thread.sleep(500);
                                    } catch (InterruptedException ie) {
                                        Thread.currentThread().interrupt();
                                        break;
                                    }
                                }
                            }
                        }
                        log.info("Redis cache invalidation listener stopped on channel '{}'", channel);
                    });
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean awaitSubscribed(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        return subscriber.awaitSubscribed(timeout, unit);
    }

    @Override
    public synchronized void close() {
        if (running.compareAndSet(true, false)) {
            try {
                if (subscriber.isSubscribed()) {
                    subscriber.unsubscribe();
                }
            } catch (Exception e) {
                log.debug("Error during subscriber unsubscribe: {}", e.getMessage());
            }

            if (listenerThread != null) {
                listenerThread.interrupt();
            }
        }
    }
}
