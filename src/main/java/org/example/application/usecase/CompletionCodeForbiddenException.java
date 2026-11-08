package org.example.application.usecase;

public class CompletionCodeForbiddenException extends RuntimeException {
    public CompletionCodeForbiddenException() {
        super("Only the owning generator may retrieve the completion code");
    }
}
