package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.SavedRouteModels.SaveRouteSuggestionCommand;
import org.example.application.route.SavedRouteModels.SavedRouteResult;
import org.example.application.route.SavedRouteModels.SavedRouteStatus;
import org.example.application.route.SavedRouteModels.SavedRouteSuggestion;
import org.example.domain.entity.CollectionRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class SaveRouteSuggestionUseCase {

    @Inject
    CollectionRequestPort collectionRequestPort;

    @Inject
    SavedRoutePort savedRoutePort;

    @Inject
    SavedRouteFingerprintService fingerprintService;

    public Uni<SavedRouteResult> save(SaveRouteSuggestionCommand command) {
        validate(command);
        List<SavedRouteFingerprintService.RouteStopRef> stopRefs = fingerprintService.assignedStopRefs(command.suggestion());
        if (stopRefs.isEmpty()) {
            throw new IllegalArgumentException("route suggestion must contain at least one assigned stop");
        }
        List<String> assignedIds = fingerprintService.distinctAssignedIds(stopRefs);
        String collectorId = command.collectorId().trim();
        String fingerprint = fingerprintService.fingerprint(collectorId, stopRefs);

        return savedRoutePort.findByFingerprint(fingerprint)
                .flatMap(existing -> {
                    if (existing != null) {
                        return Uni.createFrom().failure(new DuplicateSavedRouteException());
                    }
                    return collectionRequestPort.findByIds(assignedIds)
                            .flatMap(requests -> validateAndPersist(command, collectorId, assignedIds, fingerprint, requests));
                });
    }

    private Uni<SavedRouteResult> validateAndPersist(
            SaveRouteSuggestionCommand command,
            String collectorId,
            List<String> assignedIds,
            String fingerprint,
            List<CollectionRequest> requests
    ) {
        Map<String, CollectionRequest> byId = requests.stream()
                .collect(Collectors.toMap(CollectionRequest::getId, Function.identity()));
        for (String assignedId : assignedIds) {
            CollectionRequest request = byId.get(assignedId);
            if (request == null) {
                return Uni.createFrom().failure(new IllegalArgumentException("collection request not found: " + assignedId));
            }
            if (request.getSelectedCollectorId() != null && !collectorId.equals(request.getSelectedCollectorId())) {
                return Uni.createFrom().failure(new IllegalArgumentException("collection request does not belong to collector: " + assignedId));
            }
        }

        boolean allCompleted = assignedIds.stream()
                .map(byId::get)
                .allMatch(request -> CollectionRequest.Status.COMPLETED.equals(request.getStatus()));
        LocalDateTime now = LocalDateTime.now();
        SavedRouteSuggestion route = SavedRouteSuggestion.create(
                collectorId,
                allCompleted ? SavedRouteStatus.CLOSED : SavedRouteStatus.OPEN,
                fingerprint,
                assignedIds,
                command.suggestion(),
                now
        );
        return savedRoutePort.save(route).replaceWith(SavedRouteResult.from(route));
    }

    private void validate(SaveRouteSuggestionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("save route request is required");
        }
        if (command.collectorId() == null || command.collectorId().isBlank()) {
            throw new IllegalArgumentException("collectorId is required");
        }
        if (command.suggestion() == null) {
            throw new IllegalArgumentException("route suggestion is required");
        }
        if (command.suggestion().routes() == null || command.suggestion().routes().isEmpty()) {
            throw new IllegalArgumentException("route suggestion must contain at least one route");
        }
    }
}
