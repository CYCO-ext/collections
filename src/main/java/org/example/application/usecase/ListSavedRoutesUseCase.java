package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.DeliveryRouteAssignmentPort;
import org.example.application.port.out.AddressPort;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.RouteModels.RoutePlan;
import org.example.application.route.RouteModels.RouteStop;
import org.example.application.route.SavedRouteModels.RouteAssignment;
import org.example.application.route.SavedRouteModels.RouteAssignmentStatus;
import org.example.application.route.SavedRouteModels.SavedRouteSuggestion;
import org.example.application.route.SavedRouteModels.SavedRouteResult;

import java.time.LocalDateTime;
import java.util.List;

@Singleton
public class ListSavedRoutesUseCase {

    @Inject
    SavedRoutePort savedRoutePort;

    @Inject
    DeliveryRouteAssignmentPort deliveryRouteAssignmentPort;

    @Inject
    AddressPort addressPort;

    public Uni<List<SavedRouteResult>> list() {
        return savedRoutePort.findAllOrderByCreatedAtDesc()
                .flatMap(routes -> Uni.join().all(routes.stream()
                        .map(route -> enrichAddresses(route).flatMap(this::hydrateAssignments))
                        .toList()).andFailFast())
                .onItem().transform(hydratedRoutes -> hydratedRoutes.stream()
                        .map(SavedRouteResult::from)
                        .toList());
    }

    private Uni<SavedRouteSuggestion> enrichAddresses(SavedRouteSuggestion route) {
        if (route.suggestion() == null || route.suggestion().routes() == null) {
            return Uni.createFrom().item(route);
        }
        return Uni.join().all(route.suggestion().routes().stream()
                        .map(this::enrichPlanAddresses)
                        .toList()).andFailFast()
                .map(routes -> route.withSuggestion(route.suggestion().withRoutes(routes)));
    }

    private Uni<RoutePlan> enrichPlanAddresses(RoutePlan plan) {
        return Uni.join().all(plan.stops().stream()
                        .map(this::enrichStopAddress)
                        .toList()).andFailFast()
                .map(plan::withStops);
    }

    private Uni<RouteStop> enrichStopAddress(RouteStop stop) {
        if (stop.street() != null && !stop.street().isBlank()) {
            return Uni.createFrom().item(stop);
        }
        if (stop.addressId() == null || stop.addressId().isBlank() || addressPort == null) {
            return Uni.createFrom().item(stop);
        }
        return addressPort.findById(stop.addressId())
                .map(stop::withAddress)
                .onFailure().recoverWithItem(stop);
    }

    private Uni<SavedRouteSuggestion> hydrateAssignments(SavedRouteSuggestion route) {
        List<RoutePlan> plans = route.suggestion() == null || route.suggestion().routes() == null
                ? List.of()
                : route.suggestion().routes();
        return hydrateAssignment(route, plans, 0);
    }

    private Uni<SavedRouteSuggestion> hydrateAssignment(SavedRouteSuggestion route, List<RoutePlan> plans, int index) {
        if (index >= plans.size()) {
            return Uni.createFrom().item(route);
        }
        RoutePlan plan = plans.get(index);
        return deliveryRouteAssignmentPort.findOpenBySavedRouteIdAndVehicleIndex(route.id(), plan.vehicleIndex())
                .flatMap(assignment -> {
                    if (assignment == null) {
                        return hydrateAssignment(route, plans, index + 1);
                    }
                    RouteAssignment savedRouteAssignment = new RouteAssignment(
                            assignment.vehicleIndex(),
                            assignment.deliverymanId(),
                            assignment.collectorId(),
                            RouteAssignmentStatus.valueOf(assignment.status().name()),
                            assignment.assignedAt(),
                            assignment.startedAt(),
                            assignment.completedAt(),
                            assignment.assignedCollectionRequestIds(),
                            assignment.completedCollectionRequestIds()
                    );
                    SavedRouteSuggestion updated = route.withAssignment(savedRouteAssignment, route.updatedAt() == null
                            ? LocalDateTime.now()
                            : route.updatedAt());
                    return hydrateAssignment(updated, plans, index + 1);
                });
    }
}
