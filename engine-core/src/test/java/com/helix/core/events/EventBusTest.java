package com.helix.core.events;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
    }

    @AfterEach
    void tearDown() {
        eventBus.close();
    }

    @Test
    @DisplayName("Should publish event synchronously to subscribed listener")
    void testSyncEventPublishing() {
        AtomicReference<EngineEvent> received = new AtomicReference<>();
        eventBus.subscribe(EventType.COMPILATION_COMPLETED, received::set);

        EngineEvent event = new EngineEvent(EventType.COMPILATION_COMPLETED, "TestRule", Map.of("duration", 123L));
        eventBus.publish(event);

        assertNotNull(received.get());
        assertEquals("TestRule", received.get().ruleName());
        assertEquals(123L, received.get().payload().get("duration"));
    }

    @Test
    @DisplayName("Should publish event asynchronously to subscribed listener")
    void testAsyncEventPublishing() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<EngineEvent> received = new AtomicReference<>();

        eventBus.subscribe(EventType.EXECUTION_COMPLETED, ev -> {
            received.set(ev);
            latch.countDown();
        });

        EngineEvent event = new EngineEvent(EventType.EXECUTION_COMPLETED, "AsyncRule");
        CompletableFuture<Void> future = eventBus.publishAsync(event);

        future.get(3, TimeUnit.SECONDS);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(received.get());
        assertEquals("AsyncRule", received.get().ruleName());
    }

    @Test
    @DisplayName("Should stop sending events after listener unsubscribes")
    void testUnsubscribe() {
        AtomicInteger counter = new AtomicInteger(0);
        EventListener<EngineEvent> listener = ev -> counter.incrementAndGet();

        eventBus.subscribe(EventType.CACHE_HIT, listener);
        eventBus.publish(new EngineEvent(EventType.CACHE_HIT, "Rule1"));
        assertEquals(1, counter.get());

        eventBus.unsubscribe(EventType.CACHE_HIT, listener);
        eventBus.publish(new EngineEvent(EventType.CACHE_HIT, "Rule1"));
        assertEquals(1, counter.get());
    }
}
