package com.helix.core.cache.l4;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPubSub;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Redis Pub/Sub subscriber listening on cluster invalidation channels.
 */
public class CacheInvalidationSubscriber extends JedisPubSub {

    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationSubscriber.class);
    private final Consumer<String> onInvalidate;
    private final java.util.concurrent.CountDownLatch subscribedLatch = new java.util.concurrent.CountDownLatch(1);

    public CacheInvalidationSubscriber(Consumer<String> onInvalidate) {
        this.onInvalidate = Objects.requireNonNull(onInvalidate, "onInvalidate callback cannot be null");
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        log.info("Successfully subscribed to Redis channel '{}' (active channels: {})", channel, subscribedChannels);
        subscribedLatch.countDown();
    }

    public boolean awaitSubscribed(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        return subscribedLatch.await(timeout, unit);
    }

    @Override
    public void onMessage(String channel, String message) {
        if (message != null && !message.isBlank()) {
            String ruleName = message.trim();
            log.debug("Received cluster cache invalidation on channel '{}' for rule: {}", channel, ruleName);
            try {
                onInvalidate.accept(ruleName);
            } catch (Exception e) {
                log.error("Error executing cache invalidation handler for rule: {}", ruleName, e);
            }
        }
    }
}
