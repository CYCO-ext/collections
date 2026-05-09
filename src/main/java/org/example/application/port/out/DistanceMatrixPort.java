package org.example.application.port.out;

import org.example.application.route.RouteModels.RouteCandidateStop;
import org.example.application.route.RouteModels.RouteLocation;

import java.util.List;

public interface DistanceMatrixPort {
    long[][] buildMatrixMeters(RouteLocation depot, List<RouteCandidateStop> stops);
}
