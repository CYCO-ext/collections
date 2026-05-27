package org.example.application.port.out;

import io.smallrye.mutiny.Uni;
import org.example.application.route.RouteMapModels.RouteMap;

import java.util.List;

public interface RouteMapPort {
    Uni<RouteMap> findBySavedRouteIdAndVehicleIndex(String savedRouteId, int vehicleIndex);

    Uni<List<RouteMap>> findBySavedRouteId(String savedRouteId);

    Uni<Void> upsert(RouteMap map);
}
