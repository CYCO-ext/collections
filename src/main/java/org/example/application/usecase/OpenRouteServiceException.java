package org.example.application.usecase;

public class OpenRouteServiceException extends RuntimeException {
    private final int statusCode;
    private final boolean timeout;

    public OpenRouteServiceException(String message, int statusCode, boolean timeout) {
        super(message);
        this.statusCode = statusCode;
        this.timeout = timeout;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean timeout() {
        return timeout;
    }
}
