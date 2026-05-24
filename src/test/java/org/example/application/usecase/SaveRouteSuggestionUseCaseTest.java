package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.RouteModels.RouteOptimizationResult;
import org.example.application.route.RouteModels.RoutePlan;
import org.example.application.route.RouteModels.RouteStop;
import org.example.application.route.RouteModels.SolverMetadata;
import org.example.application.route.RouteModels.SolverStatus;
import org.example.application.route.SavedRouteModels.SaveRouteSuggestionCommand;
import org.example.application.route.SavedRouteModels.SavedRouteStatus;
import org.example.application.route.SavedRouteModels.SavedRouteSuggestion;
import org.example.domain.entity.CollectionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaveRouteSuggestionUseCaseTest {
    private SaveRouteSuggestionUseCase useCase;

    @Mock
    private CollectionRequestPort collectionRequestPort;

    @Mock
    private SavedRoutePort savedRoutePort;

    @BeforeEach
    void setUp() {
        useCase = new SaveRouteSuggestionUseCase();
        useCase.collectionRequestPort = collectionRequestPort;
        useCase.savedRoutePort = savedRoutePort;
        useCase.fingerprintService = new SavedRouteFingerprintService();
    }

    @Test
    void saveStoresOpenRouteWhenAnyRequestIsNotCompleted() {
        CollectionRequest request = request("request-1", CollectionRequest.Status.IN_PROGRESS);
        when(savedRoutePort.findByFingerprint(any())).thenReturn(Uni.createFrom().nullItem());
        when(collectionRequestPort.findByIds(List.of("request-1"))).thenReturn(Uni.createFrom().item(List.of(request)));
        when(savedRoutePort.save(any())).thenReturn(Uni.createFrom().voidItem());

        var result = useCase.save(command("collector-1", suggestionWithStop("request-1"))).await().indefinitely();

        assertEquals(SavedRouteStatus.OPEN, result.status());
        assertEquals(List.of("request-1"), result.assignedCollectionRequestIds());
        verify(collectionRequestPort, never()).update(any());
    }

    @Test
    void saveStoresClosedRouteWhenAllRequestsAreCompleted() {
        CollectionRequest request = request("request-1", CollectionRequest.Status.COMPLETED);
        when(savedRoutePort.findByFingerprint(any())).thenReturn(Uni.createFrom().nullItem());
        when(collectionRequestPort.findByIds(List.of("request-1"))).thenReturn(Uni.createFrom().item(List.of(request)));
        when(savedRoutePort.save(any())).thenReturn(Uni.createFrom().voidItem());

        var result = useCase.save(command("collector-1", suggestionWithStop("request-1"))).await().indefinitely();

        assertEquals(SavedRouteStatus.CLOSED, result.status());
        ArgumentCaptor<SavedRouteSuggestion> captor = ArgumentCaptor.forClass(SavedRouteSuggestion.class);
        verify(savedRoutePort).save(captor.capture());
        assertEquals(SavedRouteStatus.CLOSED, captor.getValue().status());
    }

    @Test
    void saveRejectsDuplicateFingerprint() {
        when(savedRoutePort.findByFingerprint(any())).thenReturn(Uni.createFrom().item(existingRoute()));

        assertThrows(DuplicateSavedRouteException.class,
                () -> useCase.save(command("collector-1", suggestionWithStop("request-1"))).await().indefinitely());

        verify(collectionRequestPort, never()).findByIds(any());
    }

    @Test
    void saveRejectsEmptyAssignedStops() {
        RouteOptimizationResult suggestion = new RouteOptimizationResult(
                SolverStatus.FEASIBLE,
                new SolverMetadata("TEST", 1, 10, 0),
                List.of(new RoutePlan(0, 100.0, 0.0, 0, List.of())),
                List.of()
        );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> useCase.save(command("collector-1", suggestion)));

        assertEquals("route suggestion must contain at least one assigned stop", exception.getMessage());
    }

    private SaveRouteSuggestionCommand command(String collectorId, RouteOptimizationResult suggestion) {
        return new SaveRouteSuggestionCommand(collectorId, "ROUTE_SUGGESTION", suggestion);
    }

    private RouteOptimizationResult suggestionWithStop(String requestId) {
        return new RouteOptimizationResult(
                SolverStatus.FEASIBLE,
                new SolverMetadata("TEST", 1, 10, 0),
                List.of(new RoutePlan(0, 100.0, 10.0, 1000, List.of(
                        new RouteStop(1, requestId, "address-1", -23.0, -46.0, 10.0, 10.0, 1000)
                ))),
                List.of()
        );
    }

    private CollectionRequest request(String id, CollectionRequest.Status status) {
        CollectionRequest request = new CollectionRequest("generator-1", "address-1", List.of("paper"), 10.0);
        request.setId(id);
        request.setSelectedCollectorId("collector-1");
        request.setStatus(status);
        return request;
    }

    private SavedRouteSuggestion existingRoute() {
        return SavedRouteSuggestion.create("collector-1", SavedRouteStatus.OPEN, "fingerprint", List.of("request-1"), suggestionWithStop("request-1"), java.time.LocalDateTime.now());
    }
}
