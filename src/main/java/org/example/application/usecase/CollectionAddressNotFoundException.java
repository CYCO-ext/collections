package org.example.application.usecase;

public class CollectionAddressNotFoundException extends RuntimeException {
    public CollectionAddressNotFoundException(String addressId) {
        super("Collection address not found: " + addressId);
    }
}
