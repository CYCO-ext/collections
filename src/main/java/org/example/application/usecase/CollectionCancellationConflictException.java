package org.example.application.usecase;

public class CollectionCancellationConflictException extends RuntimeException {
    public CollectionCancellationConflictException(String message) {
        super(message);
    }
}
