package com.helix.core.parser;

/**
 * Exception thrown when AST type validation fails or incompatible types are encountered.
 */
public class TypeMismatchException extends Exception {

    public TypeMismatchException(String message) {
        super(message);
    }

    public TypeMismatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
