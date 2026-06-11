package org.example.application.usecase;

import jakarta.inject.Singleton;
import org.example.application.route.RouteModels.RoutePlan;
import org.example.application.route.RouteModels.RouteStop;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class RoutePlanRecalculator {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    public RoutePlan recalculate(RoutePlan original, List<RouteStop> orderedStops) {
        List<RouteStop> recalculatedStops = new ArrayList<>();
        double totalLoad = 0.0;
        long totalDistance = 0L;
        RouteStop previous = null;

        for (int index = 0; index < orderedStops.size(); index++) {
            RouteStop stop = orderedStops.get(index);
            totalLoad += stop.demand();
            long distanceFromPrevious;
            if (previous == null) {
                distanceFromPrevious = stop.distanceFromPreviousMeters();
            } else {
                distanceFromPrevious = distanceMeters(previous, stop);
            }
            totalDistance += distanceFromPrevious;
            RouteStop recalculated = new RouteStop(
                    index + 1,
                    stop.collectionRequestId(),
                    stop.addressId(),
                    stop.latitude(),
                    stop.longitude(),
                    stop.demand(),
                    totalLoad,
                    distanceFromPrevious
            );
            recalculatedStops.add(recalculated);
            previous = recalculated;
        }

        return new RoutePlan(
                original.vehicleIndex(),
                original.vehicleName(),
                original.capacity(),
                totalLoad,
                totalDistance,
                List.copyOf(recalculatedStops)
        );
    }

    public long routeDistance(List<RouteStop> orderedStops) {
        long totalDistance = 0L;
        RouteStop previous = null;
        for (RouteStop stop : orderedStops) {
            if (previous == null) {
                totalDistance += stop.distanceFromPreviousMeters();
            } else {
                totalDistance += distanceMeters(previous, stop);
            }
            previous = stop;
        }
        return totalDistance;
    }

    private long distanceMeters(RouteStop from, RouteStop to) {
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
