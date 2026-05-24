package org.example.application.usecase;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.route.RouteModels.RouteStop;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class BestRouteInsertionCalculator {

    @Inject
    RoutePlanRecalculator routePlanRecalculator;

    public int bestInsertionIndex(List<RouteStop> targetStops, RouteStop movedStop) {
        if (targetStops == null || targetStops.isEmpty()) {
            return 0;
        }

        int bestIndex = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int index = 0; index <= targetStops.size(); index++) {
            List<RouteStop> candidate = new ArrayList<>(targetStops);
            candidate.add(index, movedStop);
            long distance = routePlanRecalculator.routeDistance(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }
        return bestIndex;
    }
}
