package org.example.application.usecase;

public class CompletionCodeUnauthenticatedException extends RuntimeException {
    public CompletionCodeUnauthenticatedException() {
        super("Authentication is required");
    }
}
