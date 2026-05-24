package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.RouteModels.*;
import org.example.application.route.SavedRouteModels.MoveRouteRequestCommand;
import org.example.application.route.SavedRouteModels.SavedRouteStatus;
import org.example.application.route.SavedRouteModels.SavedRouteSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MoveRouteRequestUseCaseTest {
    private MoveRouteRequestUseCase useCase;

    @Mock
    private SavedRoutePort savedRoutePort;

    @BeforeEach
    void setUp() {
        RoutePlanRecalculator recalculator = new RoutePlanRecalculator();
        BestRouteInsertionCalculator insertionCalculator = new BestRouteInsertionCalculator();
        insertionCalculator.routePlanRecalculator = recalculator;

        useCase = new MoveRouteRequestUseCase();
        useCase.savedRoutePort = savedRoutePort;
        useCase.fingerprintService = new SavedRouteFingerprintService();
        useCase.routePlanRecalculator = recalculator;
        useCase.insertionCalculator = insertionCalculator;
    }

    @Test
    void moveCalculatesBestFitInsertionInTargetVehicle() {
        SavedRouteSuggestion route = openRoute();
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(route));
        when(savedRoutePort.findByFingerprintExcludingId(any(), eq("saved-1"))).thenReturn(Uni.createFrom().nullItem());
        when(savedRoutePort.update(any())).thenReturn(Uni.createFrom().voidItem());

        var result = useCase.move(new MoveRouteRequestCommand("saved-1", "request-b", 0, 1)).await().indefinitely();

        RoutePlan source = result.suggestion().routes().get(0);
        RoutePlan target = result.suggestion().routes().get(1);
        assertEquals(List.of(), source.stops());
        assertEquals(List.of("request-a", "request-b", "request-c"), target.stops().stream().map(RouteStop::collectionRequestId).toList());
        assertEquals(List.of(1, 2, 3), target.stops().stream().map(RouteStop::sequence).toList());
        assertEquals(List.of(10.0, 20.0, 30.0), target.stops().stream().map(RouteStop::accumulatedLoad).toList());
        assertEquals(30.0, target.totalLoad());
        verify(savedRoutePort).update(any());
    }

    @Test
    void moveRejectsMissingSavedRoute() {
        when(savedRoutePort.findById("missing")).thenReturn(Uni.createFrom().nullItem());

        assertThrows(SavedRouteSuggestionNotFoundException.class,
                () -> useCase.move(new MoveRouteRequestCommand("missing", "request-b", null, 1)).await().indefinitely());
    }

    @Test
    void moveRejectsClosedSavedRoute() {
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(closedRoute()));

        assertThrows(RouteMoveValidationException.class,
                () -> useCase.move(new MoveRouteRequestCommand("saved-1", "request-b", null, 1)).await().indefinitely());
        verify(savedRoutePort, never()).update(any());
    }

    @Test
    void moveRejectsUnknownRequest() {
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(openRoute()));

        assertThrows(RouteMoveValidationException.class,
                () -> useCase.move(new MoveRouteRequestCommand("saved-1", "missing", null, 1)).await().indefinitely());
    }

    @Test
    void moveRejectsInvalidTargetVehicle() {
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(openRoute()));

        assertThrows(RouteMoveValidationException.class,
                () -> useCase.move(new MoveRouteRequestCommand("saved-1", "request-b", null, 9)).await().indefinitely());
    }

    @Test
    void moveRejectsCapacityViolation() {
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(routeWithFullTarget()));

        assertThrows(RouteMoveValidationException.class,
                () -> useCase.move(new MoveRouteRequestCommand("saved-1", "request-b", null, 1)).await().indefinitely());
    }

    @Test
    void moveRejectsDuplicateFingerprint() {
        SavedRouteSuggestion route = openRoute();
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(route));
        when(savedRoutePort.findByFingerprintExcludingId(any(), eq("saved-1"))).thenReturn(Uni.createFrom().item(openRoute("other")));

        assertThrows(DuplicateSavedRouteException.class,
                () -> useCase.move(new MoveRouteRequestCommand("saved-1", "request-b", null, 1)).await().indefinitely());
        verify(savedRoutePort, never()).update(any());
    }

    @Test
    void movePersistsUpdatedFingerprintAndAssignedIds() {
        SavedRouteSuggestion route = openRoute();
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(route));
        when(savedRoutePort.findByFingerprintExcludingId(any(), eq("saved-1"))).thenReturn(Uni.createFrom().nullItem());
        when(savedRoutePort.update(any())).thenReturn(Uni.createFrom().voidItem());

        useCase.move(new MoveRouteRequestCommand("saved-1", "request-b", null, 1)).await().indefinitely();

        ArgumentCaptor<SavedRouteSuggestion> captor = ArgumentCaptor.forClass(SavedRouteSuggestion.class);
        verify(savedRoutePort).update(captor.capture());
        assertEquals(List.of("request-a", "request-b", "request-c"), captor.getValue().assignedCollectionRequestIds());
        assertEquals(SavedRouteStatus.OPEN, captor.getValue().status());
    }

    private SavedRouteSuggestion openRoute() {
        return openRoute("saved-1");
    }

    private SavedRouteSuggestion openRoute(String id) {
        LocalDateTime now = LocalDateTime.now();
        return new SavedRouteSuggestion(id, "collector-1", SavedRouteStatus.OPEN, "fingerprint",
                List.of("request-b", "request-a", "request-c"), suggestion(100.0), now, now, null);
    }

    private SavedRouteSuggestion closedRoute() {
        LocalDateTime now = LocalDateTime.now();
        return new SavedRouteSuggestion("saved-1", "collector-1", SavedRouteStatus.CLOSED, "fingerprint",
                List.of("request-b", "request-a", "request-c"), suggestion(100.0), now, now, now);
    }

    private SavedRouteSuggestion routeWithFullTarget() {
        LocalDateTime now = LocalDateTime.now();
        return new SavedRouteSuggestion("saved-1", "collector-1", SavedRouteStatus.OPEN, "fingerprint",
                List.of("request-b", "request-a", "request-c"), suggestion(25.0), now, now, null);
    }

    private RouteOptimizationResult suggestion(double targetCapacity) {
        return new RouteOptimizationResult(
                SolverStatus.FEASIBLE,
                new SolverMetadata("TEST", 1, 0, 0),
                List.of(
                        new RoutePlan(0, 100.0, 10.0, 0, List.of(
                                stop(1, "request-b", 0.0, 1.0, 10.0, 10.0)
                        )),
                        new RoutePlan(1, targetCapacity, 20.0, 0, List.of(
                                stop(1, "request-a", 0.0, 0.0, 10.0, 10.0),
                                stop(2, "request-c", 0.0, 2.0, 10.0, 20.0)
                        ))
                ),
                List.of()
        );
    }

    private RouteStop stop(int sequence, String requestId, double latitude, double longitude, double demand, double accumulatedLoad) {
        return new RouteStop(sequence, requestId, "address-" + requestId, latitude, longitude, demand, accumulatedLoad, 0);
    }
}
