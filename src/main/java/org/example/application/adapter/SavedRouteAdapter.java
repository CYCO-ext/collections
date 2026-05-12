package org.example.application.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.SavedRouteModels.SavedRouteSuggestion;
import org.example.infrastructure.repository.SavedRouteRepository;

import java.time.LocalDateTime;
import java.util.List;

@Singleton
public class SavedRouteAdapter implements SavedRoutePort {

    @Inject
    SavedRouteRepository repository;

    @Override
    public Uni<Void> save(SavedRouteSuggestion route) {
        return repository.save(route);
    }

    @Override
    public Uni<SavedRouteSuggestion> findByFingerprint(String fingerprint) {
        return repository.findByFingerprint(fingerprint);
    }

    @Override
    public Uni<List<SavedRouteSuggestion>> findAllOrderByCreatedAtDesc() {
        return repository.findAllOrderByCreatedAtDesc();
    }

    @Override
    public Uni<List<SavedRouteSuggestion>> findOpenContainingCollectionRequest(String collectionRequestId) {
        return repository.findOpenContainingCollectionRequest(collectionRequestId);
    }

    @Override
    public Uni<Void> close(String savedRouteId, LocalDateTime closedAt) {
        return repository.close(savedRouteId, closedAt);
    }
}
