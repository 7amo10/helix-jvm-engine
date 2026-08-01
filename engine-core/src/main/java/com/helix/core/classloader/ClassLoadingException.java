package com.helix.core.classloader;

public class ClassLoadingException extends Exception {

    public ClassLoadingException(String message) {
        super(message);
    }

    public ClassLoadingException(String message, Throwable cause) {
        super(message, cause);
    }
}
