package com.helix.api.stream;

/**
 * Functional handler for processing streamed rule events.
 */
@FunctionalInterface
public interface RuleStreamHandler {

    /**
     * Handles an incoming rule event and returns the stream result.
     *
     * @param event incoming rule event
     * @return outcome of evaluation
     * @throws Exception if processing fails
     */
    StreamResult handle(RuleEvent event) throws Exception;
}
