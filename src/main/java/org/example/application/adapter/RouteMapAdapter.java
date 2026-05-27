package org.example.application.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.RouteMapPort;
import org.example.application.route.RouteMapModels.RouteMap;
import org.example.infrastructure.repository.RouteMapRepository;

import java.util.List;

@Singleton
public class RouteMapAdapter implements RouteMapPort {

    @Inject
    RouteMapRepository repository;

    @Override
    public Uni<RouteMap> findBySavedRouteIdAndVehicleIndex(String savedRouteId, int vehicleIndex) {
        return repository.findBySavedRouteIdAndVehicleIndex(savedRouteId, vehicleIndex);
    }

    @Override
    public Uni<List<RouteMap>> findBySavedRouteId(String savedRouteId) {
        return repository.findBySavedRouteId(savedRouteId);
    }

    @Override
    public Uni<Void> upsert(RouteMap map) {
        return repository.upsert(map);
    }
}
