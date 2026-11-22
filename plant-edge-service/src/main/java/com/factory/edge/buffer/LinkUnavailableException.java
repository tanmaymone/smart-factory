package com.factory.edge.buffer;

public class LinkUnavailableException extends Exception {

    public LinkUnavailableException(String message) {
        super(message);
    }

    public LinkUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
