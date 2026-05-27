package org.example.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import org.example.application.port.out.CollectorDiscoveryPort;
import org.example.application.port.out.OpenRouteServiceDirectionsPort;
import org.example.application.port.out.RouteMapPort;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.RouteMapModels.GetSavedRouteMapQuery;
import org.example.application.route.RouteMapModels.RouteCoordinate;
import org.example.application.route.RouteMapModels.RouteMap;
import org.example.application.route.RouteMapModels.SavedRouteMapResult;
import org.example.application.route.RouteModels.*;
import org.example.application.route.SavedRouteModels.SavedRouteStatus;
import org.example.application.route.SavedRouteModels.SavedRouteSuggestion;
import org.example.domain.entity.Address;
import org.example.domain.entity.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetSavedRouteMapUseCaseTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private GetSavedRouteMapUseCase useCase;

    @Mock
    private SavedRoutePort savedRoutePort;

    @Mock
    private RouteMapPort routeMapPort;

    @Mock
    private OpenRouteServiceDirectionsPort directionsPort;

    @Mock
    private CollectorDiscoveryPort collectorDiscoveryPort;

    @BeforeEach
    void setUp() {
        useCase = new GetSavedRouteMapUseCase();
        useCase.savedRoutePort = savedRoutePort;
        useCase.routeMapPort = routeMapPort;
        useCase.directionsPort = directionsPort;
        useCase.collectorDiscoveryPort = collectorDiscoveryPort;
        useCase.fingerprintService = new RouteMapFingerprintService();
        lenient().when(collectorDiscoveryPort.findCollectorById("collector-1"))
                .thenReturn(Uni.createFrom().item(collector()));
    }

    @Test
    void generatesAndPersistsMissingMap() {
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(savedRoute()));
        when(routeMapPort.findBySavedRouteIdAndVehicleIndex("saved-1", 0)).thenReturn(Uni.createFrom().nullItem());
        when(directionsPort.fetchDrivingCarGeoJson(any())).thenReturn(Uni.createFrom().item(geoJson()));
        when(routeMapPort.upsert(any())).thenReturn(Uni.createFrom().voidItem());

        SavedRouteMapResult result = useCase.get(new GetSavedRouteMapQuery("saved-1", null)).await().indefinitely();

        assertEquals("saved-1", result.savedRouteId());
        assertEquals(1, result.maps().size());
        assertFalse(result.maps().getFirst().reused());
        verify(directionsPort).fetchDrivingCarGeoJson(any());
        verify(routeMapPort).upsert(any());
    }

    @Test
    void reusesStoredMapWhenFingerprintMatches() {
        SavedRouteSuggestion savedRoute = savedRoute();
        String fingerprint = new RouteMapFingerprintService().fingerprint("saved-1", 0, List.of(
                new RouteCoordinate("start", -22.9, -45.9),
                new RouteCoordinate("request-1", -23.0, -46.0),
                new RouteCoordinate("request-2", -23.1, -46.1)
        ));
        RouteMap stored = RouteMap.create("saved-1", 0, fingerprint, geoJson(), LocalDateTime.now());
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(savedRoute));
        when(routeMapPort.findBySavedRouteIdAndVehicleIndex("saved-1", 0)).thenReturn(Uni.createFrom().item(stored));

        SavedRouteMapResult result = useCase.get(new GetSavedRouteMapQuery("saved-1", 0)).await().indefinitely();

        assertTrue(result.maps().getFirst().reused());
        verify(directionsPort, never()).fetchDrivingCarGeoJson(any());
        verify(routeMapPort, never()).upsert(any());
    }

    @Test
    void regeneratesStoredMapWhenFingerprintDiffers() {
        RouteMap stale = RouteMap.create("saved-1", 0, "sha256:stale", geoJson(), LocalDateTime.now());
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(savedRoute()));
        when(routeMapPort.findBySavedRouteIdAndVehicleIndex("saved-1", 0)).thenReturn(Uni.createFrom().item(stale));
        when(directionsPort.fetchDrivingCarGeoJson(any())).thenReturn(Uni.createFrom().item(geoJson()));
        when(routeMapPort.upsert(any())).thenReturn(Uni.createFrom().voidItem());

        SavedRouteMapResult result = useCase.get(new GetSavedRouteMapQuery("saved-1", 0)).await().indefinitely();

        assertFalse(result.maps().getFirst().reused());
        ArgumentCaptor<RouteMap> captor = ArgumentCaptor.forClass(RouteMap.class);
        verify(routeMapPort).upsert(captor.capture());
        assertTrue(captor.getValue().fingerprint().startsWith("sha256:"));
    }

    @Test
    void generatesMapForSingleStopRouteUsingCollectorStartPoint() {
        SavedRouteSuggestion route = routeWithStops(List.of(
                new RouteStop(1, "request-1", "address-1", -23.0, -46.0, 10.0, 10.0, 0)
        ));
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(route));
        when(routeMapPort.findBySavedRouteIdAndVehicleIndex("saved-1", 0)).thenReturn(Uni.createFrom().nullItem());
        when(directionsPort.fetchDrivingCarGeoJson(any())).thenReturn(Uni.createFrom().item(geoJson()));
        when(routeMapPort.upsert(any())).thenReturn(Uni.createFrom().voidItem());

        useCase.get(new GetSavedRouteMapQuery("saved-1", null)).await().indefinitely();

        ArgumentCaptor<List<RouteCoordinate>> captor = ArgumentCaptor.forClass(List.class);
        verify(directionsPort).fetchDrivingCarGeoJson(captor.capture());
        assertEquals("start", captor.getValue().get(0).collectionRequestId());
        assertEquals("request-1", captor.getValue().get(1).collectionRequestId());
    }

    @Test
    void failsWhenVehicleIndexDoesNotExist() {
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(savedRoute()));

        RouteMapValidationException exception = assertThrows(RouteMapValidationException.class,
                () -> useCase.get(new GetSavedRouteMapQuery("saved-1", 9)).await().indefinitely());

        assertEquals("vehicle route not found: 9", exception.getMessage());
    }

    @Test
    void sendsCoordinatesInRouteOrderToProvider() {
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(savedRoute()));
        when(routeMapPort.findBySavedRouteIdAndVehicleIndex("saved-1", 0)).thenReturn(Uni.createFrom().nullItem());
        when(directionsPort.fetchDrivingCarGeoJson(any())).thenReturn(Uni.createFrom().item(geoJson()));
        when(routeMapPort.upsert(any())).thenReturn(Uni.createFrom().voidItem());

        useCase.get(new GetSavedRouteMapQuery("saved-1", 0)).await().indefinitely();

        ArgumentCaptor<List<RouteCoordinate>> captor = ArgumentCaptor.forClass(List.class);
        verify(directionsPort).fetchDrivingCarGeoJson(captor.capture());
        assertEquals("start", captor.getValue().get(0).collectionRequestId());
        assertEquals("request-1", captor.getValue().get(1).collectionRequestId());
        assertEquals("request-2", captor.getValue().get(2).collectionRequestId());
    }

    private SavedRouteSuggestion savedRoute() {
        return routeWithStops(List.of(
                new RouteStop(1, "request-1", "address-1", -23.0, -46.0, 10.0, 10.0, 0),
                new RouteStop(2, "request-2", "address-2", -23.1, -46.1, 10.0, 20.0, 1000)
        ));
    }

    private SavedRouteSuggestion routeWithStops(List<RouteStop> stops) {
        return new SavedRouteSuggestion(
                "saved-1",
                "collector-1",
                SavedRouteStatus.OPEN,
                "route-fingerprint",
                stops.stream().map(RouteStop::collectionRequestId).toList(),
                new RouteOptimizationResult(
                        SolverStatus.FEASIBLE,
                        new SolverMetadata("TEST", 1, 10, 0),
                        List.of(new RoutePlan(0, 100.0, 20.0, 1000, stops)),
                        List.of()
                ),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null
        );
    }

    private Collector collector() {
        Address address = new Address(
                "collector-address",
                "Start St",
                "Sao Paulo",
                "01000000",
                "1",
                "SP",
                -22.9,
                -45.9,
                "ENRICHED",
                "provided"
        );
        return new Collector("collector-1", "user-1", "Collector One", address, List.of("paper"), 0.9);
    }

    private com.fasterxml.jackson.databind.JsonNode geoJson() {
        return objectMapper.createObjectNode()
                .put("type", "FeatureCollection")
                .set("features", objectMapper.createArrayNode());
    }
}
