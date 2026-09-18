package com.helix.api.stream;

/**
 * Asynchronous callback listener for stream evaluation results.
 */
@FunctionalInterface
public interface StreamListener {

    /**
     * Invoked when an event evaluation produces a stream result.
     *
     * @param result the evaluation result
     */
    void onResult(StreamResult result);
}
