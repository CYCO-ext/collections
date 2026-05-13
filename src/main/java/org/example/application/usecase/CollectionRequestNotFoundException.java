package org.example.application.usecase;

public class CollectionRequestNotFoundException extends RuntimeException {
    public CollectionRequestNotFoundException(String requestId) {
        super("Request not found: " + requestId);
    }
}
