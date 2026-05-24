package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.RouteModels.RouteOptimizationResult;
import org.example.application.route.RouteModels.RoutePlan;
import org.example.application.route.RouteModels.RouteStop;
import org.example.application.route.SavedRouteModels.MoveRouteRequestCommand;
import org.example.application.route.SavedRouteModels.SavedRouteResult;
import org.example.application.route.SavedRouteModels.SavedRouteStatus;
import org.example.application.route.SavedRouteModels.SavedRouteSuggestion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Singleton
public class MoveRouteRequestUseCase {

    @Inject
    SavedRoutePort savedRoutePort;

    @Inject
    SavedRouteFingerprintService fingerprintService;

    @Inject
    RoutePlanRecalculator routePlanRecalculator;

    @Inject
    BestRouteInsertionCalculator insertionCalculator;

    public Uni<SavedRouteResult> move(MoveRouteRequestCommand command) {
        validate(command);
        return savedRoutePort.findById(command.savedRouteId().trim())
                .onItem().ifNull().failWith(() -> new SavedRouteSuggestionNotFoundException(command.savedRouteId().trim()))
                .flatMap(savedRoute -> move(savedRoute, command));
    }

    private Uni<SavedRouteResult> move(SavedRouteSuggestion savedRoute, MoveRouteRequestCommand command) {
        if (SavedRouteStatus.CLOSED.equals(savedRoute.status())) {
            return Uni.createFrom().failure(new RouteMoveValidationException("closed saved routes cannot be changed"));
        }
        RouteOptimizationResult suggestion = savedRoute.suggestion();
        if (suggestion == null || suggestion.routes() == null || suggestion.routes().isEmpty()) {
            return Uni.createFrom().failure(new RouteMoveValidationException("saved route suggestion has no routes"));
        }

        List<RoutePlan> routes = sortedRoutes(suggestion.routes());
        StopLocation source = findStop(routes, command.collectionRequestId().trim());
        if (source == null) {
            return Uni.createFrom().failure(new RouteMoveValidationException("collection request is not assigned to saved route: " + command.collectionRequestId().trim()));
        }
        if (source.duplicate()) {
            return Uni.createFrom().failure(new RouteMoveValidationException("collection request is assigned more than once: " + command.collectionRequestId().trim()));
        }
        if (command.sourceVehicleIndex() != null && command.sourceVehicleIndex() != source.route().vehicleIndex()) {
            return Uni.createFrom().failure(new RouteMoveValidationException("sourceVehicleIndex does not match current route vehicle"));
        }

        RoutePlan targetRoute = routeByVehicleIndex(routes, command.targetVehicleIndex());
        if (targetRoute == null) {
            return Uni.createFrom().failure(new RouteMoveValidationException("target vehicle not found: " + command.targetVehicleIndex()));
        }
        if (source.route().vehicleIndex() == targetRoute.vehicleIndex()) {
            return Uni.createFrom().failure(new RouteMoveValidationException("collection request is already assigned to target vehicle"));
        }
        if (targetRoute.totalLoad() + source.stop().demand() > targetRoute.capacity()) {
            return Uni.createFrom().failure(new RouteMoveValidationException("move would exceed target vehicle capacity"));
        }

        List<RoutePlan> updatedRoutes = moveStop(routes, source, targetRoute);
        RouteOptimizationResult updatedSuggestion = new RouteOptimizationResult(
                suggestion.status(),
                suggestion.solver(),
                updatedRoutes,
                suggestion.unassigned()
        );
        String fingerprint = fingerprintService.fingerprint(savedRoute.collectorId(), updatedSuggestion);
        List<String> assignedIds = fingerprintService.distinctAssignedIds(updatedSuggestion);
        SavedRouteSuggestion updatedRoute = savedRoute.updateRoute(fingerprint, assignedIds, updatedSuggestion, LocalDateTime.now());

        return savedRoutePort.findByFingerprintExcludingId(fingerprint, savedRoute.id())
                .flatMap(existing -> {
                    if (existing != null) {
                        return Uni.createFrom().failure(new DuplicateSavedRouteException());
                    }
                    return savedRoutePort.update(updatedRoute).replaceWith(SavedRouteResult.from(updatedRoute));
                });
    }

    private void validate(MoveRouteRequestCommand command) {
        if (command == null) {
            throw new RouteMoveValidationException("move route request is required");
        }
        if (command.savedRouteId() == null || command.savedRouteId().isBlank()) {
            throw new RouteMoveValidationException("savedRouteId is required");
        }
        if (command.collectionRequestId() == null || command.collectionRequestId().isBlank()) {
            throw new RouteMoveValidationException("collectionRequestId is required");
        }
        if (command.targetVehicleIndex() == null) {
            throw new RouteMoveValidationException("targetVehicleIndex is required");
        }
        if (command.targetVehicleIndex() < 0) {
            throw new RouteMoveValidationException("targetVehicleIndex must be greater than or equal to zero");
        }
    }

    private List<RoutePlan> sortedRoutes(List<RoutePlan> routes) {
        return routes.stream()
                .sorted(Comparator.comparingInt(RoutePlan::vehicleIndex))
                .toList();
    }

    private RoutePlan routeByVehicleIndex(List<RoutePlan> routes, int vehicleIndex) {
        return routes.stream()
                .filter(route -> route.vehicleIndex() == vehicleIndex)
                .findFirst()
                .orElse(null);
    }

    private StopLocation findStop(List<RoutePlan> routes, String collectionRequestId) {
        StopLocation found = null;
        for (RoutePlan route : routes) {
            if (route.stops() == null) {
                continue;
            }
            for (RouteStop stop : route.stops()) {
                if (collectionRequestId.equals(stop.collectionRequestId())) {
                    if (found != null) {
                        return found.asDuplicate();
                    }
                    found = new StopLocation(route, stop, false);
                }
            }
        }
        return found;
    }

    private List<RoutePlan> moveStop(List<RoutePlan> routes, StopLocation source, RoutePlan targetRoute) {
        List<RoutePlan> updatedRoutes = new ArrayList<>();
        for (RoutePlan route : routes) {
            if (route.vehicleIndex() == source.route().vehicleIndex()) {
                List<RouteStop> sourceStops = new ArrayList<>(route.stops() == null ? List.of() : route.stops());
                sourceStops.removeIf(stop -> source.stop().collectionRequestId().equals(stop.collectionRequestId()));
                updatedRoutes.add(routePlanRecalculator.recalculate(route, sourceStops));
                continue;
            }
            if (route.vehicleIndex() == targetRoute.vehicleIndex()) {
                List<RouteStop> targetStops = new ArrayList<>(route.stops() == null ? List.of() : route.stops());
                int insertionIndex = insertionCalculator.bestInsertionIndex(targetStops, source.stop());
                targetStops.add(insertionIndex, source.stop());
                updatedRoutes.add(routePlanRecalculator.recalculate(route, targetStops));
                continue;
            }
            updatedRoutes.add(route);
        }
        return updatedRoutes.stream()
                .sorted(Comparator.comparingInt(RoutePlan::vehicleIndex))
                .toList();
    }

    private record StopLocation(RoutePlan route, RouteStop stop, boolean duplicate) {
        private StopLocation asDuplicate() {
            return new StopLocation(route, stop, true);
        }
    }
}
