package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.SavedRouteModels.SavedRouteSuggestion;
import org.example.domain.entity.CollectionRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class CloseSavedRoutesUseCase {

    @Inject
    SavedRoutePort savedRoutePort;

    @Inject
    CollectionRequestPort collectionRequestPort;

    public Uni<Void> closeRoutesContaining(String collectionRequestId) {
        if (collectionRequestId == null || collectionRequestId.isBlank()) {
            return Uni.createFrom().voidItem();
        }
        return savedRoutePort.findOpenContainingCollectionRequest(collectionRequestId.trim())
                .flatMap(routes -> closeNext(routes, 0));
    }

    private Uni<Void> closeNext(List<SavedRouteSuggestion> routes, int index) {
        if (routes == null || index >= routes.size()) {
            return Uni.createFrom().voidItem();
        }
        SavedRouteSuggestion route = routes.get(index);
        return collectionRequestPort.findByIds(route.assignedCollectionRequestIds())
                .flatMap(requests -> {
                    if (allAssignedRequestsCompleted(route, requests)) {
                        return savedRoutePort.close(route.id(), LocalDateTime.now());
                    }
                    return Uni.createFrom().voidItem();
                })
                .flatMap(ignored -> closeNext(routes, index + 1));
    }

    private boolean allAssignedRequestsCompleted(SavedRouteSuggestion route, List<CollectionRequest> requests) {
        Map<String, CollectionRequest> byId = requests.stream()
                .collect(Collectors.toMap(CollectionRequest::getId, Function.identity()));
        return route.assignedCollectionRequestIds().stream()
                .map(byId::get)
                .allMatch(request -> request != null && CollectionRequest.Status.COMPLETED.equals(request.getStatus()));
    }
}
