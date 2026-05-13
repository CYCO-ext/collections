package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.SavedRoutePort;

@Singleton
public class DeleteSavedRouteSuggestionUseCase {

    @Inject
    SavedRoutePort savedRoutePort;

    public Uni<Void> delete(String savedRouteId) {
        String normalizedId = normalizeId(savedRouteId);
        return savedRoutePort.deleteById(normalizedId)
                .flatMap(deleted -> {
                    if (Boolean.TRUE.equals(deleted)) {
                        return Uni.createFrom().voidItem();
                    }
                    return Uni.createFrom().failure(new SavedRouteSuggestionNotFoundException(normalizedId));
                });
    }

    private String normalizeId(String savedRouteId) {
        if (savedRouteId == null || savedRouteId.isBlank()) {
            throw new IllegalArgumentException("saved route id is required");
        }
        return savedRouteId.trim();
    }
}
