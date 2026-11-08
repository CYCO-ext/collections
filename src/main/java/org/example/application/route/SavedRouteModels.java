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

    public enum RouteAssignmentStatus {
        ASSIGNED,
        IN_PROGRESS,
        COMPLETED,
        PARTIALLY_COMPLETED,
        FAILED
    }

    public record RouteAssignment(
            int vehicleIndex,
            String deliverymanId,
            String collectorId,
            RouteAssignmentStatus status,
            LocalDateTime assignedAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            List<String> assignedCollectionRequestIds,
            List<String> completedCollectionRequestIds
    ) {
        public RouteAssignment {
            assignedCollectionRequestIds = assignedCollectionRequestIds == null ? List.of() : List.copyOf(assignedCollectionRequestIds);
            completedCollectionRequestIds = completedCollectionRequestIds == null ? List.of() : List.copyOf(completedCollectionRequestIds);
        }
    }

    public record SaveRouteSuggestionCommand(
            String collectorId,
            String source,
            RouteOptimizationResult suggestion
    ) {
    }

    public record MoveRouteRequestCommand(
            String savedRouteId,
            String collectionRequestId,
            Integer sourceVehicleIndex,
            Integer targetVehicleIndex
    ) {
    }

    public record SavedRouteSuggestion(
            String id,
            String collectorId,
            SavedRouteStatus status,
            String fingerprint,
            List<String> assignedCollectionRequestIds,
            List<RouteAssignment> assignments,
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
                    List.of(),
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
                    assignments == null ? List.of() : List.copyOf(assignments),
                    suggestion,
                    createdAt,
                    closedAt,
                    closedAt
            );
        }

        public SavedRouteSuggestion updateRoute(
                String fingerprint,
                List<String> assignedCollectionRequestIds,
                RouteOptimizationResult suggestion,
                LocalDateTime updatedAt
        ) {
            return new SavedRouteSuggestion(
                    id,
                    collectorId,
                    status,
                    fingerprint,
                    List.copyOf(assignedCollectionRequestIds),
                    assignments == null ? List.of() : List.copyOf(assignments),
                    suggestion,
                    createdAt,
                    updatedAt,
                    closedAt
            );
        }

        public SavedRouteSuggestion withAssignment(RouteAssignment assignment, LocalDateTime updatedAt) {
            List<RouteAssignment> nextAssignments = new java.util.ArrayList<>(assignments == null ? List.of() : assignments);
            nextAssignments.removeIf(existing -> existing.vehicleIndex() == assignment.vehicleIndex());
            nextAssignments.add(assignment);
            return new SavedRouteSuggestion(
                    id,
                    collectorId,
                    status,
                    fingerprint,
                    assignedCollectionRequestIds,
                    List.copyOf(nextAssignments),
                    suggestion,
                    createdAt,
                    updatedAt,
                    closedAt
            );
        }

        public SavedRouteSuggestion withSuggestion(RouteOptimizationResult updatedSuggestion) {
            return new SavedRouteSuggestion(
                    id,
                    collectorId,
                    status,
                    fingerprint,
                    assignedCollectionRequestIds,
                    assignments == null ? List.of() : List.copyOf(assignments),
                    updatedSuggestion,
                    createdAt,
                    updatedAt,
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
            List<RouteAssignment> assignments,
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
                    route.assignments() == null ? List.of() : route.assignments(),
                    route.suggestion(),
                    route.createdAt(),
                    route.updatedAt(),
                    route.closedAt()
            );
        }
    }
}
