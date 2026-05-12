package org.example.application.usecase;

public class CollectorNotFoundException extends RuntimeException {
    public CollectorNotFoundException(String collectorId) {
        super("Collector not found: " + collectorId);
    }
}
