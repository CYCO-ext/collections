package org.example.application.route;

import java.util.ArrayList;
import java.util.List;

public final class RouteModels {
    private RouteModels() {
    }

    public enum StartLocationType {
        COORDINATES,
        ADDRESS,
        COLLECTOR_ADDRESS
    }

    public enum SolverStatus {
        OPTIMAL,
        FEASIBLE,
        PARTIAL,
        INFEASIBLE,
        INVALID_INPUT
    }

    public enum UnassignedReason {
        MISSING_COORDINATES,
        MISSING_ADDRESS,
        NOT_IN_PROGRESS,
        MATERIAL_NOT_ACCEPTED,
        INVALID_DEMAND,
        OVER_CAPACITY,
        SOLVER_DROPPED,
        INFEASIBLE
    }

    public record RouteLocation(String id, String addressId, double latitude, double longitude) {
    }

    public record RouteVehicle(int index, double capacity) {
    }

    public record RouteFilters(List<String> materialIds, Double maxDistanceKmFromStart, Boolean onlyInProgress) {
        public List<String> materialIdsOrEmpty() {
            return materialIds == null ? List.of() : materialIds;
        }
    }

    public record RouteOptions(Integer timeLimitSeconds, Boolean allowDroppingStops, Long dropPenalty) {
        public boolean allowDroppingStopsOrDefault() {
            return allowDroppingStops != null && allowDroppingStops;
        }
    }

    public record StartLocation(StartLocationType type, String addressId, Double latitude, Double longitude) {
    }

    public record RouteOptimizationCommand(
            String collectorId,
            int vehicleCount,
            Double vehicleCapacity,
            List<Double> vehicleCapacities,
            StartLocation start,
            boolean endAtStart,
            List<String> candidateRequestIds,
            RouteFilters filters,
            RouteOptions options
    ) {
        public List<String> candidateRequestIdsOrEmpty() {
            return candidateRequestIds == null ? List.of() : candidateRequestIds;
        }

        public RouteFilters filtersOrDefault() {
            return filters == null ? new RouteFilters(List.of(), null, true) : filters;
        }

        public RouteOptions optionsOrDefault() {
            return options == null ? new RouteOptions(null, false, null) : options;
        }
    }

    public record RouteCandidateStop(
            String collectionRequestId,
            String addressId,
            List<String> materialIds,
            double demand,
            RouteLocation location
    ) {
    }

    public record RouteOptimizationProblem(
            RouteLocation depot,
            List<RouteVehicle> vehicles,
            List<RouteCandidateStop> stops,
            long[][] distanceMatrixMeters,
            RouteOptions options,
            int timeLimitSeconds,
            long dropPenalty
    ) {
    }

    public record RouteStop(
            int sequence,
            String collectionRequestId,
            String addressId,
            double latitude,
            double longitude,
            double demand,
            double accumulatedLoad,
            long distanceFromPreviousMeters
    ) {
    }

    public record RoutePlan(
            int vehicleIndex,
            double capacity,
            double totalLoad,
            long totalDistanceMeters,
            List<RouteStop> stops
    ) {
    }

    public record UnassignedRouteStop(String collectionRequestId, UnassignedReason reason) {
    }

    public record SolverMetadata(
            String engine,
            long elapsedMs,
            long objectiveDistanceMeters,
            int droppedStops
    ) {
    }

    public record RouteOptimizationResult(
            SolverStatus status,
            SolverMetadata solver,
            List<RoutePlan> routes,
            List<UnassignedRouteStop> unassigned
    ) {
        public RouteOptimizationResult withAdditionalUnassigned(List<UnassignedRouteStop> additional) {
            if (additional == null || additional.isEmpty()) {
                return this;
            }
            List<UnassignedRouteStop> merged = new ArrayList<>(additional);
            if (unassigned != null) {
                merged.addAll(unassigned);
            }
            SolverStatus mergedStatus = status;
            if (!merged.isEmpty() && (status == SolverStatus.FEASIBLE || status == SolverStatus.OPTIMAL)) {
                mergedStatus = SolverStatus.PARTIAL;
            }
            return new RouteOptimizationResult(mergedStatus, solver, routes, merged);
        }
    }
}
