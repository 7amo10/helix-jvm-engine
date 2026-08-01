package com.helix.core.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe EventBus supporting synchronous and asynchronous event publishing and type-safe subscriptions.
 */
public class EventBus implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final Map<EventType, List<EventListener<EngineEvent>>> listeners = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor;
    private final boolean ownsExecutorService;

    public EventBus() {
        this(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "helix-event-bus-worker");
            t.setDaemon(true);
            return t;
        }), true);
    }

    public EventBus(ExecutorService asyncExecutor) {
        this(asyncExecutor, false);
    }

    private EventBus(ExecutorService asyncExecutor, boolean ownsExecutorService) {
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor cannot be null");
        this.ownsExecutorService = ownsExecutorService;
    }

    public void subscribe(EventType eventType, EventListener<EngineEvent> listener) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void unsubscribe(EventType eventType, EventListener<EngineEvent> listener) {
        if (eventType != null && listener != null) {
            List<EventListener<EngineEvent>> typeListeners = listeners.get(eventType);
            if (typeListeners != null) {
                typeListeners.remove(listener);
            }
        }
    }

    public void publish(EngineEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        List<EventListener<EngineEvent>> typeListeners = listeners.get(event.type());
        if (typeListeners != null) {
            for (EventListener<EngineEvent> listener : typeListeners) {
                try {
                    listener.onEvent(event);
                } catch (Throwable t) {
                    log.error("Unhandled exception in event listener for event: {}", event.type(), t);
                }
            }
        }
    }

    public CompletableFuture<Void> publishAsync(EngineEvent event) {
        return CompletableFuture.runAsync(() -> publish(event), asyncExecutor);
    }

    @Override
    public void close() {
        listeners.clear();
        if (ownsExecutorService && !asyncExecutor.isShutdown()) {
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    asyncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("EventBus executor shut down.");
        }
    }
}
