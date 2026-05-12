package org.example.application.usecase;

public class DuplicateSavedRouteException extends RuntimeException {
    public DuplicateSavedRouteException() {
        super("Route suggestion already saved");
    }
}
