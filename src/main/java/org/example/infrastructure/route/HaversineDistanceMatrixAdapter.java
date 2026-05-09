package org.example.infrastructure.route;

import jakarta.inject.Singleton;
import org.example.application.port.out.DistanceMatrixPort;
import org.example.application.route.RouteModels.RouteCandidateStop;
import org.example.application.route.RouteModels.RouteLocation;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class HaversineDistanceMatrixAdapter implements DistanceMatrixPort {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    @Override
    public long[][] buildMatrixMeters(RouteLocation depot, List<RouteCandidateStop> stops) {
        List<RouteLocation> locations = new ArrayList<>();
        locations.add(depot);
        stops.forEach(stop -> locations.add(stop.location()));

        long[][] matrix = new long[locations.size()][locations.size()];
        for (int from = 0; from < locations.size(); from++) {
            for (int to = 0; to < locations.size(); to++) {
                matrix[from][to] = from == to ? 0L : distanceMeters(locations.get(from), locations.get(to));
            }
        }
        return matrix;
    }

    private long distanceMeters(RouteLocation from, RouteLocation to) {
        double fromLat = Math.toRadians(from.latitude());
        double toLat = Math.toRadians(to.latitude());
        double deltaLat = toLat - fromLat;
        double deltaLon = Math.toRadians(to.longitude() - from.longitude());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(fromLat) * Math.cos(toLat) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(EARTH_RADIUS_METERS * c);
    }
}
