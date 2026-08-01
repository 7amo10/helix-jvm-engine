package com.helix.core.bytecode;

/**
 * Exception thrown when dynamic bytecode generation fails.
 */
public class BytecodeGenerationException extends Exception {

    public BytecodeGenerationException(String message) {
        super(message);
    }

    public BytecodeGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
