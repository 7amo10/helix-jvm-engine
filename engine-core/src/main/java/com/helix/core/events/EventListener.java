package com.helix.core.events;

@FunctionalInterface
public interface EventListener<T extends EngineEvent> {

    void onEvent(T event);
}
