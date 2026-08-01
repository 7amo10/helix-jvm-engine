package com.helix.agent;

public class AgentInitializationException extends RuntimeException {

    public AgentInitializationException(String message) {
        super(message);
    }

    public AgentInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
