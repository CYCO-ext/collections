package org.example.application.usecase;

import org.example.application.route.RouteModels.RouteOptimizationResult;
import org.example.application.route.RouteModels.RoutePlan;
import org.example.application.route.RouteModels.RouteStop;
import org.example.application.route.RouteModels.SolverMetadata;
import org.example.application.route.RouteModels.SolverStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SavedRouteFingerprintServiceTest {
    private final SavedRouteFingerprintService service = new SavedRouteFingerprintService();

    @Test
    void fingerprintIgnoresVehicleNames() {
        RouteOptimizationResult original = suggestion("Truck A");
        RouteOptimizationResult renamed = suggestion("Renamed Truck");

        assertEquals(
                service.fingerprint("collector-1", original),
                service.fingerprint("collector-1", renamed)
        );
    }

    private RouteOptimizationResult suggestion(String vehicleName) {
        return new RouteOptimizationResult(
                SolverStatus.FEASIBLE,
                new SolverMetadata("TEST", 1, 100, 0),
                List.of(new RoutePlan(0, vehicleName, 100.0, 10.0, 100, List.of(
                        new RouteStop(1, "request-1", "address-1", -23.0, -46.0, 10.0, 10.0, 100)
                ))),
                List.of()
        );
    }
}
