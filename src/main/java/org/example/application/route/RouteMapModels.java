package org.example.application.route;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class RouteMapModels {
    private RouteMapModels() {
    }

    public enum RouteMapProvider {
        OPEN_ROUTE_SERVICE
    }

    public record RouteCoordinate(
            String collectionRequestId,
            double latitude,
            double longitude
    ) {
    }

    public record GetSavedRouteMapQuery(
            String savedRouteId,
            Integer vehicleIndex
    ) {
    }

    public record RouteMap(
            String id,
            String savedRouteId,
            int vehicleIndex,
            RouteMapProvider provider,
            String profile,
            String fingerprint,
            JsonNode geoJson,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static RouteMap create(
                String savedRouteId,
                int vehicleIndex,
                String fingerprint,
                JsonNode geoJson,
                LocalDateTime now
        ) {
            return new RouteMap(
                    UUID.randomUUID().toString(),
                    savedRouteId,
                    vehicleIndex,
                    RouteMapProvider.OPEN_ROUTE_SERVICE,
                    "driving-car",
                    fingerprint,
                    geoJson,
                    now,
                    now
            );
        }
    }

    public record VehicleRouteMapResult(
            int vehicleIndex,
            String fingerprint,
            boolean reused,
            JsonNode geoJson,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static VehicleRouteMapResult from(RouteMap map, boolean reused) {
            return new VehicleRouteMapResult(
                    map.vehicleIndex(),
                    map.fingerprint(),
                    reused,
                    map.geoJson(),
                    map.createdAt(),
                    map.updatedAt()
            );
        }
    }

    public record SavedRouteMapResult(
            String savedRouteId,
            RouteMapProvider provider,
            String profile,
            List<VehicleRouteMapResult> maps
    ) {
    }
}
