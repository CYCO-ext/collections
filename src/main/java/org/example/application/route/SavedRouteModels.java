package org.example.application.route;

import org.example.application.route.RouteModels.RouteOptimizationResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class SavedRouteModels {
    private SavedRouteModels() {
    }

    public enum SavedRouteStatus {
        OPEN,
        CLOSED
    }

    public record SaveRouteSuggestionCommand(
            String collectorId,
            String source,
            RouteOptimizationResult suggestion
    ) {
    }

    public record SavedRouteSuggestion(
            String id,
            String collectorId,
            SavedRouteStatus status,
            String fingerprint,
            List<String> assignedCollectionRequestIds,
            RouteOptimizationResult suggestion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime closedAt
    ) {
        public static SavedRouteSuggestion create(
                String collectorId,
                SavedRouteStatus status,
                String fingerprint,
                List<String> assignedCollectionRequestIds,
                RouteOptimizationResult suggestion,
                LocalDateTime now
        ) {
            return new SavedRouteSuggestion(
                    UUID.randomUUID().toString(),
                    collectorId,
                    status,
                    fingerprint,
                    List.copyOf(assignedCollectionRequestIds),
                    suggestion,
                    now,
                    now,
                    status == SavedRouteStatus.CLOSED ? now : null
            );
        }

        public SavedRouteSuggestion close(LocalDateTime closedAt) {
            if (status == SavedRouteStatus.CLOSED) {
                return this;
            }
            return new SavedRouteSuggestion(
                    id,
                    collectorId,
                    SavedRouteStatus.CLOSED,
                    fingerprint,
                    assignedCollectionRequestIds,
                    suggestion,
                    createdAt,
                    closedAt,
                    closedAt
            );
        }
    }

    public record SavedRouteResult(
            String id,
            String collectorId,
            SavedRouteStatus status,
            String fingerprint,
            List<String> assignedCollectionRequestIds,
            RouteOptimizationResult suggestion,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime closedAt
    ) {
        public static SavedRouteResult from(SavedRouteSuggestion route) {
            return new SavedRouteResult(
                    route.id(),
                    route.collectorId(),
                    route.status(),
                    route.fingerprint(),
                    route.assignedCollectionRequestIds(),
                    route.suggestion(),
                    route.createdAt(),
                    route.updatedAt(),
                    route.closedAt()
            );
        }
    }
}
