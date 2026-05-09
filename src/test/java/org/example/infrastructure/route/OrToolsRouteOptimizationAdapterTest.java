package org.example.infrastructure.route;

import org.example.application.route.RouteModels.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Enable after the IDE/Maven classpath resolves com.google.ortools:ortools-java")
class OrToolsRouteOptimizationAdapterTest {
    private final OrToolsRouteOptimizationAdapter adapter = new OrToolsRouteOptimizationAdapter();

    @Test
    void optimizeRespectsSingleVehicleCapacity() {
        RouteOptimizationResult result = adapter.optimize(problem(
                List.of(new RouteVehicle(0, 10.0)),
                List.of(stop("request-1", 4.0), stop("request-2", 6.0)),
                false
        ));

        assertEquals(SolverStatus.FEASIBLE, result.status());
        assertEquals(1, result.routes().size());
        assertEquals(10.0, result.routes().getFirst().totalLoad());
        assertTrue(result.unassigned().isEmpty());
    }

    @Test
    void optimizeSplitsStopsAcrossVehiclesWhenCapacityRequiresIt() {
        RouteOptimizationResult result = adapter.optimize(problem(
                List.of(new RouteVehicle(0, 10.0), new RouteVehicle(1, 10.0)),
                List.of(stop("request-1", 8.0), stop("request-2", 8.0)),
                false
        ));

        assertEquals(SolverStatus.FEASIBLE, result.status());
        assertEquals(2, result.routes().size());
        assertEquals(16.0, result.routes().stream().mapToDouble(route -> route.totalLoad()).sum());
    }

    @Test
    void optimizeDropsStopsWhenCapacityIsInsufficientAndDroppingIsEnabled() {
        RouteOptimizationResult result = adapter.optimize(problem(
                List.of(new RouteVehicle(0, 10.0)),
                List.of(stop("request-1", 8.0), stop("request-2", 8.0)),
                true
        ));

        assertEquals(SolverStatus.PARTIAL, result.status());
        assertEquals(1, result.unassigned().size());
        assertEquals(UnassignedReason.SOLVER_DROPPED, result.unassigned().getFirst().reason());
    }

    private RouteOptimizationProblem problem(List<RouteVehicle> vehicles, List<RouteCandidateStop> stops, boolean allowDropping) {
        int size = stops.size() + 1;
        long[][] matrix = new long[size][size];
        for (int from = 0; from < size; from++) {
            for (int to = 0; to < size; to++) {
                matrix[from][to] = from == to ? 0 : Math.abs(from - to) + 1;
            }
        }
        return new RouteOptimizationProblem(
                new RouteLocation("start", null, 0.0, 0.0),
                vehicles,
                stops,
                matrix,
                new RouteOptions(1, allowDropping, 100000L),
                1,
                100000L
        );
    }

    private RouteCandidateStop stop(String requestId, double demand) {
        return new RouteCandidateStop(
                requestId,
                requestId + "-address",
                List.of("paper"),
                demand,
                new RouteLocation(requestId, requestId + "-address", 0.0, 0.0)
        );
    }
}
