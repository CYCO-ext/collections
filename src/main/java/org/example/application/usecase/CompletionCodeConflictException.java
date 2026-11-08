package org.example.application.usecase;

public class CompletionCodeConflictException extends RuntimeException {
    private final String code;

    public CompletionCodeConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
