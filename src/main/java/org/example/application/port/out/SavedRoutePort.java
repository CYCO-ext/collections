package org.example.application.port.out;

import io.smallrye.mutiny.Uni;
import org.example.application.route.SavedRouteModels.SavedRouteSuggestion;

import java.time.LocalDateTime;
import java.util.List;

public interface SavedRoutePort {
    Uni<Void> save(SavedRouteSuggestion route);

    Uni<SavedRouteSuggestion> findByFingerprint(String fingerprint);

    Uni<List<SavedRouteSuggestion>> findAllOrderByCreatedAtDesc();

    Uni<List<SavedRouteSuggestion>> findOpenContainingCollectionRequest(String collectionRequestId);

    Uni<Void> close(String savedRouteId, LocalDateTime closedAt);
}
