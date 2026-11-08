package org.example.application.usecase;

public class ConcurrentCollectionRequestModificationException extends RuntimeException {
    public ConcurrentCollectionRequestModificationException(String requestId) {
        super("Collection request changed concurrently: " + requestId);
    }
}
