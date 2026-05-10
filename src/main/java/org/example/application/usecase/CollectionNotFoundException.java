package org.example.application.usecase;

public class CollectionNotFoundException extends RuntimeException {
    public CollectionNotFoundException(String id) {
        super("Collection request not found: " + id);
    }
}
