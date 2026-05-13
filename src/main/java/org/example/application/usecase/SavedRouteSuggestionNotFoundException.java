package org.example.application.usecase;

public class SavedRouteSuggestionNotFoundException extends RuntimeException {
    public SavedRouteSuggestionNotFoundException(String savedRouteId) {
        super("Saved route suggestion not found: " + savedRouteId);
    }
}
