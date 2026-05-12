package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.RouteModels.RouteOptimizationResult;
import org.example.application.route.RouteModels.RoutePlan;
import org.example.application.route.RouteModels.RouteStop;
import org.example.application.route.SavedRouteModels.SaveRouteSuggestionCommand;
import org.example.application.route.SavedRouteModels.SavedRouteResult;
import org.example.application.route.SavedRouteModels.SavedRouteStatus;
import org.example.application.route.SavedRouteModels.SavedRouteSuggestion;
import org.example.domain.entity.CollectionRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class SaveRouteSuggestionUseCase {

    @Inject
    CollectionRequestPort collectionRequestPort;

    @Inject
    SavedRoutePort savedRoutePort;

    public Uni<SavedRouteResult> save(SaveRouteSuggestionCommand command) {
        validate(command);
        List<RouteStopRef> stopRefs = assignedStopRefs(command.suggestion());
        if (stopRefs.isEmpty()) {
            throw new IllegalArgumentException("route suggestion must contain at least one assigned stop");
        }
        List<String> assignedIds = distinctAssignedIds(stopRefs);
        String collectorId = command.collectorId().trim();
        String fingerprint = fingerprint(collectorId, stopRefs);

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

    private List<RouteStopRef> assignedStopRefs(RouteOptimizationResult suggestion) {
        List<RouteStopRef> refs = new ArrayList<>();
        for (RoutePlan route : suggestion.routes()) {
            if (route.stops() == null) {
                continue;
            }
            for (RouteStop stop : route.stops()) {
                if (stop.collectionRequestId() == null || stop.collectionRequestId().isBlank()) {
                    throw new IllegalArgumentException("route stop collectionRequestId is required");
                }
                refs.add(new RouteStopRef(route.vehicleIndex(), stop.sequence(), stop.collectionRequestId().trim()));
            }
        }
        refs.sort(Comparator.comparingInt(RouteStopRef::vehicleIndex).thenComparingInt(RouteStopRef::sequence));
        return refs;
    }

    private List<String> distinctAssignedIds(List<RouteStopRef> stopRefs) {
        Set<String> ids = new LinkedHashSet<>();
        for (RouteStopRef stopRef : stopRefs) {
            ids.add(stopRef.collectionRequestId());
        }
        return List.copyOf(ids);
    }

    private String fingerprint(String collectorId, List<RouteStopRef> stopRefs) {
        String input = collectorId + "|" + stopRefs.stream()
                .map(ref -> ref.vehicleIndex() + ":" + ref.sequence() + ":" + ref.collectionRequestId())
                .collect(Collectors.joining("|"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private record RouteStopRef(int vehicleIndex, int sequence, String collectionRequestId) {
    }
}
