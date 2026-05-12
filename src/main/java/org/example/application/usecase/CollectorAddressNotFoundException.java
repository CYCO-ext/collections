package org.example.application.usecase;

public class CollectorAddressNotFoundException extends RuntimeException {
    public CollectorAddressNotFoundException(String collectorId) {
        super("Collector address not found: " + collectorId);
    }
}
