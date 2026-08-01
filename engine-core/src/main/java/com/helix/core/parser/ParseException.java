package com.helix.core.parser;

/**
 * Exception thrown when JSON parsing or schema validation of a rule fails.
 */
public class ParseException extends Exception {

    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
