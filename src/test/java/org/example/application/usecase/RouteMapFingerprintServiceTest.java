package org.example.application.usecase;

import org.example.application.route.RouteMapModels.RouteCoordinate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteMapFingerprintServiceTest {
    private final RouteMapFingerprintService service = new RouteMapFingerprintService();

    @Test
    void fingerprintIsStableForSameVehicleRoute() {
        List<RouteCoordinate> coordinates = List.of(
                new RouteCoordinate("request-1", -23.0, -46.0),
                new RouteCoordinate("request-2", -23.1, -46.1)
        );

        String first = service.fingerprint("saved-1", 0, coordinates);
        String second = service.fingerprint("saved-1", 0, coordinates);

        assertEquals(first, second);
        assertTrue(first.startsWith("sha256:"));
    }

    @Test
    void fingerprintChangesWhenStopOrderChanges() {
        String first = service.fingerprint("saved-1", 0, List.of(
                new RouteCoordinate("request-1", -23.0, -46.0),
                new RouteCoordinate("request-2", -23.1, -46.1)
        ));
        String second = service.fingerprint("saved-1", 0, List.of(
                new RouteCoordinate("request-2", -23.1, -46.1),
                new RouteCoordinate("request-1", -23.0, -46.0)
        ));

        assertNotEquals(first, second);
    }

    @Test
    void fingerprintChangesWhenVehicleIndexChanges() {
        List<RouteCoordinate> coordinates = List.of(
                new RouteCoordinate("request-1", -23.0, -46.0),
                new RouteCoordinate("request-2", -23.1, -46.1)
        );

        assertNotEquals(
                service.fingerprint("saved-1", 0, coordinates),
                service.fingerprint("saved-1", 1, coordinates)
        );
    }
}
