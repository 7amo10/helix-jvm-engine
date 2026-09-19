package com.helix.core.stream.disruptor;

import com.helix.api.ExecutionContext;
import com.helix.api.stream.RuleEvent;
import com.helix.api.stream.StreamListener;
import com.helix.api.stream.StreamResult;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Pre-allocated ring buffer event slot with cache line padding to prevent false sharing
 * across multi-core CPU architectures.
 */
abstract class RuleEventHolderPaddingPre {
    public long p1, p2, p3, p4, p5, p6, p7;
}

abstract class RuleEventHolderData extends RuleEventHolderPaddingPre {
    protected String eventId;
    protected String topic;
    protected String ruleName;
    protected ExecutionContext context;
    protected RuleEvent event;
    protected CompletableFuture<StreamResult> future;
    protected StreamListener listener;
    protected long timestamp;
    protected long sequence;
}

public class RuleEventHolder extends RuleEventHolderData {

    public long p8, p9, p10, p11, p12, p13, p14;

    private final MutableRuleEvent internalEvent = new MutableRuleEvent();

    public void set(String eventId, String topic, String ruleName, ExecutionContext context,
                    RuleEvent event, CompletableFuture<StreamResult> future,
                    StreamListener listener, long timestamp, long sequence) {
        this.eventId = eventId;
        this.topic = topic;
        this.ruleName = ruleName;
        this.context = context;
        this.future = future;
        this.listener = listener;
        this.timestamp = timestamp;
        this.sequence = sequence;

        if (event != null) {
            this.event = event;
        } else {
            this.internalEvent.set(eventId, topic, ruleName, context, timestamp);
            this.event = this.internalEvent;
        }
    }

    public void setEvent(RuleEvent ruleEvent, CompletableFuture<StreamResult> future,
                         StreamListener listener, long sequence) {
        this.eventId = ruleEvent.getEventId();
        this.topic = ruleEvent.getTopic();
        this.ruleName = ruleEvent.getRuleName();
        this.context = ruleEvent.getContext();
        this.event = ruleEvent;
        this.future = future;
        this.listener = listener;
        this.timestamp = ruleEvent.getTimestamp();
        this.sequence = sequence;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTopic() {
        return topic;
    }

    public String getRuleName() {
        return ruleName;
    }

    public ExecutionContext getContext() {
        return context;
    }

    public RuleEvent getEvent() {
        return event != null ? event : internalEvent;
    }

    public CompletableFuture<StreamResult> getFuture() {
        return future;
    }

    public StreamListener getListener() {
        return listener;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getSequence() {
        return sequence;
    }

    /**
     * Clears references after processing to eliminate GC retention in the ring buffer.
     */
    public void clear() {
        this.eventId = null;
        this.topic = null;
        this.ruleName = null;
        this.context = null;
        this.event = null;
        this.future = null;
        this.listener = null;
        this.timestamp = 0L;
        this.sequence = 0L;
        this.internalEvent.clear();
    }

    /**
     * Pre-allocated mutable RuleEvent that avoids heap allocation in steady-state streaming.
     */
    public static class MutableRuleEvent extends RuleEvent {
        private String eventId;
        private String topic;
        private String ruleName;
        private ExecutionContext context;
        private long timestamp;

        public MutableRuleEvent() {
            super("0", "default", null, null, 0L, Collections.emptyMap());
        }

        public void set(String eventId, String topic, String ruleName, ExecutionContext context, long timestamp) {
            this.eventId = eventId;
            this.topic = topic;
            this.ruleName = ruleName;
            this.context = context;
            this.timestamp = timestamp;
        }

        public void clear() {
            this.eventId = null;
            this.topic = null;
            this.ruleName = null;
            this.context = null;
            this.timestamp = 0L;
        }

        @Override
        public String getEventId() {
            return eventId;
        }

        @Override
        public String getTopic() {
            return topic;
        }

        @Override
        public String getRuleName() {
            return ruleName;
        }

        @Override
        public ExecutionContext getContext() {
            return context;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }
    }
}
