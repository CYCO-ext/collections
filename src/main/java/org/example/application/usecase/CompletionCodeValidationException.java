package org.example.application.usecase;

public class CompletionCodeValidationException extends RuntimeException {
    private final String code;
    private final int status;
    private final Integer retryAfterSeconds;

    public CompletionCodeValidationException(String code, String message, int status, Integer retryAfterSeconds) {
        super(message);
        this.code = code;
        this.status = status;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }

    public Integer retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
