package org.example.application.usecase;

public class CollectionCancellationForbiddenException extends RuntimeException {
    public CollectionCancellationForbiddenException(String message) {
        super(message);
    }
}
