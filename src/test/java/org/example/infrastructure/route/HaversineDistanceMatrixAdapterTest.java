package org.example.infrastructure.route;

import org.example.application.route.RouteModels.RouteCandidateStop;
import org.example.application.route.RouteModels.RouteLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaversineDistanceMatrixAdapterTest {
    private final HaversineDistanceMatrixAdapter adapter = new HaversineDistanceMatrixAdapter();

    @Test
    void buildMatrixMetersReturnsZeroDiagonalAndSymmetricDistances() {
        RouteLocation depot = new RouteLocation("start", null, -23.5505, -46.6333);
        RouteCandidateStop stop = new RouteCandidateStop(
                "request-1",
                "address-1",
                List.of("paper"),
                10.0,
                new RouteLocation("request-1", "address-1", -23.5605, -46.6433)
        );

        long[][] matrix = adapter.buildMatrixMeters(depot, List.of(stop));

        assertEquals(2, matrix.length);
        assertEquals(0, matrix[0][0]);
        assertEquals(0, matrix[1][1]);
        assertEquals(matrix[0][1], matrix[1][0]);
        assertTrue(matrix[0][1] > 0);
    }
}
