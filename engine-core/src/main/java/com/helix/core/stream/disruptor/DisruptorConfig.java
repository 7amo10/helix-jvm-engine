package com.helix.core.stream.disruptor;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;

/**
 * Configuration options for the LMAX Disruptor streaming engine.
 */
public class DisruptorConfig {

    public static final int DEFAULT_RING_BUFFER_SIZE = 65536;

    private final int ringBufferSize;
    private final WaitStrategy waitStrategy;
    private final ProducerType producerType;
    private final ThreadFactory threadFactory;

    public DisruptorConfig(int ringBufferSize, WaitStrategy waitStrategy, ProducerType producerType, ThreadFactory threadFactory) {
        if (ringBufferSize <= 0 || Integer.bitCount(ringBufferSize) != 1) {
            throw new IllegalArgumentException("ringBufferSize must be a positive power of 2 (e.g. 1024, 65536). Provided: " + ringBufferSize);
        }
        this.ringBufferSize = ringBufferSize;
        this.waitStrategy = Objects.requireNonNull(waitStrategy, "waitStrategy cannot be null");
        this.producerType = Objects.requireNonNull(producerType, "producerType cannot be null");
        this.threadFactory = threadFactory != null ? threadFactory : Thread.ofVirtual().name("helix-disruptor-worker-", 0).factory();
    }

    public static DisruptorConfig defaultConfig() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }

    public WaitStrategy getWaitStrategy() {
        return waitStrategy;
    }

    public ProducerType getProducerType() {
        return producerType;
    }

    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    public static class Builder {
        private int ringBufferSize = DEFAULT_RING_BUFFER_SIZE;
        private WaitStrategy waitStrategy = new YieldingWaitStrategy();
        private ProducerType producerType = ProducerType.MULTI;
        private ThreadFactory threadFactory = Thread.ofVirtual().name("helix-disruptor-worker-", 0).factory();

        public Builder ringBufferSize(int ringBufferSize) {
            this.ringBufferSize = ringBufferSize;
            return this;
        }

        public Builder waitStrategy(WaitStrategy waitStrategy) {
            this.waitStrategy = waitStrategy;
            return this;
        }

        public Builder yieldingWaitStrategy() {
            this.waitStrategy = new YieldingWaitStrategy();
            return this;
        }

        public Builder busySpinWaitStrategy() {
            this.waitStrategy = new BusySpinWaitStrategy();
            return this;
        }

        public Builder blockingWaitStrategy() {
            this.waitStrategy = new BlockingWaitStrategy();
            return this;
        }

        public Builder producerType(ProducerType producerType) {
            this.producerType = producerType;
            return this;
        }

        public Builder singleProducer() {
            this.producerType = ProducerType.SINGLE;
            return this;
        }

        public Builder multiProducer() {
            this.producerType = ProducerType.MULTI;
            return this;
        }

        public Builder threadFactory(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
            return this;
        }

        public DisruptorConfig build() {
            return new DisruptorConfig(ringBufferSize, waitStrategy, producerType, threadFactory);
        }
    }
}
