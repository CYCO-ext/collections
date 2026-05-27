package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.CollectorDiscoveryPort;
import org.example.application.port.out.OpenRouteServiceDirectionsPort;
import org.example.application.port.out.RouteMapPort;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.RouteMapModels.*;
import org.example.application.route.RouteModels.RoutePlan;
import org.example.application.route.RouteModels.RouteStop;
import org.example.application.route.SavedRouteModels.SavedRouteSuggestion;
import org.example.domain.entity.Address;
import org.example.domain.entity.Collector;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Singleton
public class GetSavedRouteMapUseCase {

    @Inject
    SavedRoutePort savedRoutePort;

    @Inject
    RouteMapPort routeMapPort;

    @Inject
    OpenRouteServiceDirectionsPort directionsPort;

    @Inject
    RouteMapFingerprintService fingerprintService;

    @Inject
    CollectorDiscoveryPort collectorDiscoveryPort;

    public Uni<SavedRouteMapResult> get(GetSavedRouteMapQuery query) {
        validate(query);
        String savedRouteId = query.savedRouteId().trim();
        return savedRoutePort.findById(savedRouteId)
                .onItem().ifNull().failWith(() -> new SavedRouteSuggestionNotFoundException(savedRouteId))
                .flatMap(savedRoute -> buildMaps(savedRoute, query.vehicleIndex()));
    }

    private Uni<SavedRouteMapResult> buildMaps(SavedRouteSuggestion savedRoute, Integer requestedVehicleIndex) {
        List<RoutePlan> routes = selectedRoutes(savedRoute, requestedVehicleIndex);
        return collectorDiscoveryPort.findCollectorById(savedRoute.collectorId())
                .onItem().ifNull().failWith(() -> new RouteMapValidationException("collector not found: " + savedRoute.collectorId()))
                .flatMap(collector -> buildMaps(savedRoute, routes, collector));
    }

    private Uni<SavedRouteMapResult> buildMaps(SavedRouteSuggestion savedRoute, List<RoutePlan> routes, Collector collector) {
        Uni<List<VehicleRouteMapResult>> accumulated = Uni.createFrom().item(new ArrayList<>());
        for (RoutePlan route : routes) {
            accumulated = accumulated.flatMap(results -> mapForRoute(savedRoute.id(), route, collector.getAddress())
                    .onItem().transform(result -> {
                        results.add(result);
                        return results;
                    }));
        }
        return accumulated.onItem().transform(results -> new SavedRouteMapResult(
                savedRoute.id(),
                RouteMapProvider.OPEN_ROUTE_SERVICE,
                "driving-car",
                List.copyOf(results)
        ));
    }

    private Uni<VehicleRouteMapResult> mapForRoute(String savedRouteId, RoutePlan route, Address startAddress) {
        List<RouteCoordinate> coordinates = coordinates(route, startAddress);
        if (coordinates.size() < 2) {
            return Uni.createFrom().failure(new RouteMapValidationException("vehicle route must contain a start point and at least one stop coordinate: " + route.vehicleIndex()));
        }
        String fingerprint = fingerprintService.fingerprint(savedRouteId, route.vehicleIndex(), coordinates);
        return routeMapPort.findBySavedRouteIdAndVehicleIndex(savedRouteId, route.vehicleIndex())
                .flatMap(existing -> {
                    if (existing != null && fingerprint.equals(existing.fingerprint())) {
                        return Uni.createFrom().item(VehicleRouteMapResult.from(existing, true));
                    }
                    return directionsPort.fetchDrivingCarGeoJson(coordinates)
                            .flatMap(geoJson -> {
                                RouteMap generated = RouteMap.create(savedRouteId, route.vehicleIndex(), fingerprint, geoJson, LocalDateTime.now());
                                return routeMapPort.upsert(generated)
                                        .replaceWith(VehicleRouteMapResult.from(generated, false));
                            });
                });
    }

    private List<RoutePlan> selectedRoutes(SavedRouteSuggestion savedRoute, Integer requestedVehicleIndex) {
        if (savedRoute.suggestion() == null || savedRoute.suggestion().routes() == null || savedRoute.suggestion().routes().isEmpty()) {
            throw new RouteMapValidationException("saved route suggestion has no routes");
        }
        List<RoutePlan> routes = savedRoute.suggestion().routes().stream()
                .sorted(Comparator.comparingInt(RoutePlan::vehicleIndex))
                .toList();
        if (requestedVehicleIndex == null) {
            return routes;
        }
        if (requestedVehicleIndex < 0) {
            throw new RouteMapValidationException("vehicleIndex must be greater than or equal to zero");
        }
        return routes.stream()
                .filter(route -> route.vehicleIndex() == requestedVehicleIndex)
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new RouteMapValidationException("vehicle route not found: " + requestedVehicleIndex));
    }

    private List<RouteCoordinate> coordinates(RoutePlan route, Address startAddress) {
        List<RouteCoordinate> coordinates = new ArrayList<>();
        if (hasCoordinates(startAddress)) {
            coordinates.add(new RouteCoordinate("start", startAddress.getLatitude(), startAddress.getLongitude()));
        }
        if (route.stops() != null) {
            coordinates.addAll(route.stops().stream()
                    .sorted(Comparator.comparingInt(RouteStop::sequence))
                    .map(stop -> new RouteCoordinate(stop.collectionRequestId(), stop.latitude(), stop.longitude()))
                    .toList());
        }
        return List.copyOf(coordinates);
    }

    private boolean hasCoordinates(Address address) {
        return address != null
                && address.getLatitude() != null
                && address.getLongitude() != null;
    }

    private void validate(GetSavedRouteMapQuery query) {
        if (query == null) {
            throw new RouteMapValidationException("route map request is required");
        }
        if (query.savedRouteId() == null || query.savedRouteId().isBlank()) {
            throw new RouteMapValidationException("savedRouteId is required");
        }
    }
}
