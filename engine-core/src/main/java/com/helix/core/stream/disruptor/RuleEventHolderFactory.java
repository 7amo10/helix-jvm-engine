package com.helix.core.stream.disruptor;

import com.lmax.disruptor.EventFactory;

/**
 * Factory for pre-allocating padded {@link RuleEventHolder} instances within the Disruptor RingBuffer.
 */
public class RuleEventHolderFactory implements EventFactory<RuleEventHolder> {

    @Override
    public RuleEventHolder newInstance() {
        return new RuleEventHolder();
    }
}
