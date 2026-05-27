package org.example.presentation.rest;

import jakarta.ws.rs.core.Response;
import org.example.application.route.RouteMapModels.SavedRouteMapResult;
import org.example.application.route.RouteMapModels.VehicleRouteMapResult;
import org.example.application.route.RouteMapModels.RouteMapProvider;
import org.example.application.route.RouteModels.*;
import org.example.application.route.SavedRouteModels.SavedRouteResult;
import org.example.application.route.SavedRouteModels.SavedRouteStatus;
import org.example.application.usecase.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CollectorRouteResourceTest {
    private CollectorRouteResource resource;
    private RouteOptimizationUseCase routeOptimizationUseCase;
    private SaveRouteSuggestionUseCase saveRouteSuggestionUseCase;
    private ListSavedRoutesUseCase listSavedRoutesUseCase;
    private DeleteSavedRouteSuggestionUseCase deleteSavedRouteSuggestionUseCase;
    private MoveRouteRequestUseCase moveRouteRequestUseCase;
    private GetSavedRouteMapUseCase getSavedRouteMapUseCase;

    @BeforeEach
    void setUp() {
        routeOptimizationUseCase = mock(RouteOptimizationUseCase.class);
        saveRouteSuggestionUseCase = mock(SaveRouteSuggestionUseCase.class);
        listSavedRoutesUseCase = mock(ListSavedRoutesUseCase.class);
        deleteSavedRouteSuggestionUseCase = mock(DeleteSavedRouteSuggestionUseCase.class);
        moveRouteRequestUseCase = mock(MoveRouteRequestUseCase.class);
        getSavedRouteMapUseCase = mock(GetSavedRouteMapUseCase.class);
        resource = new CollectorRouteResource();
        resource.routeOptimizationUseCase = routeOptimizationUseCase;
        resource.saveRouteSuggestionUseCase = saveRouteSuggestionUseCase;
        resource.listSavedRoutesUseCase = listSavedRoutesUseCase;
        resource.deleteSavedRouteSuggestionUseCase = deleteSavedRouteSuggestionUseCase;
        resource.moveRouteRequestUseCase = moveRouteRequestUseCase;
        resource.getSavedRouteMapUseCase = getSavedRouteMapUseCase;
    }

    @Test
    void suggestRoutesReturnsUseCaseResult() {
        when(routeOptimizationUseCase.suggestRoutes(any())).thenReturn(io.smallrye.mutiny.Uni.createFrom().item(suggestion()));

        Response response = resource.suggestRoutes(validRequest()).await().indefinitely();

        assertEquals(200, response.getStatus());
        RouteOptimizationResult result = (RouteOptimizationResult) response.getEntity();
        assertEquals(SolverStatus.FEASIBLE, result.status());
        assertEquals(1, result.routes().size());
    }

    @Test
    void suggestRoutesReturnsBadRequestForInvalidStartType() {
        CollectorRouteResource.RouteOptimizationRequestDTO request = validRequest();
        request.getStart().setType("BAD_TYPE");

        Response response = resource.suggestRoutes(request).await().indefinitely();

        assertEquals(400, response.getStatus());
    }

    @Test
    void saveRouteReturnsCreated() {
        SavedRouteResult saved = savedResult();
        when(saveRouteSuggestionUseCase.save(any())).thenReturn(io.smallrye.mutiny.Uni.createFrom().item(saved));

        Response response = resource.saveRoute(saveRequest()).await().indefinitely();

        assertEquals(201, response.getStatus());
        assertEquals(saved, response.getEntity());
        verify(saveRouteSuggestionUseCase).save(any());
    }

    @Test
    void saveRouteReturnsConflictForDuplicate() {
        when(saveRouteSuggestionUseCase.save(any())).thenReturn(io.smallrye.mutiny.Uni.createFrom().failure(new DuplicateSavedRouteException()));

        Response response = resource.saveRoute(saveRequest()).await().indefinitely();

        assertEquals(409, response.getStatus());
        assertEquals("Route suggestion already saved", response.getEntity());
    }

    @Test
    void saveRouteReturnsBadRequestForNullBody() {
        Response response = resource.saveRoute(null).await().indefinitely();

        assertEquals(400, response.getStatus());
    }

    @Test
    void listSavedRoutesReturnsUseCaseResults() {
        SavedRouteResult saved = savedResult();
        when(listSavedRoutesUseCase.list()).thenReturn(io.smallrye.mutiny.Uni.createFrom().item(List.of(saved)));

        Response response = resource.listSavedRoutes().await().indefinitely();

        assertEquals(200, response.getStatus());
        assertEquals(List.of(saved), response.getEntity());
    }

    @Test
    void getSavedRouteMapReturnsUseCaseResult() {
        SavedRouteMapResult map = routeMapResult();
        when(getSavedRouteMapUseCase.get(any())).thenReturn(io.smallrye.mutiny.Uni.createFrom().item(map));

        Response response = resource.getSavedRouteMap("saved-1", 0).await().indefinitely();

        assertEquals(200, response.getStatus());
        assertEquals(map, response.getEntity());
        verify(getSavedRouteMapUseCase).get(any());
    }

    @Test
    void getSavedRouteMapReturnsNotFoundForMissingRoute() {
        when(getSavedRouteMapUseCase.get(any())).thenReturn(io.smallrye.mutiny.Uni.createFrom().failure(
                new SavedRouteSuggestionNotFoundException("missing")));

        Response response = resource.getSavedRouteMap("missing", null).await().indefinitely();

        assertEquals(404, response.getStatus());
        assertEquals("Saved route suggestion not found: missing", response.getEntity());
    }

    @Test
    void getSavedRouteMapReturnsBadRequestForValidationError() {
        when(getSavedRouteMapUseCase.get(any())).thenReturn(io.smallrye.mutiny.Uni.createFrom().failure(
                new RouteMapValidationException("vehicle route must contain at least two coordinates: 0")));

        Response response = resource.getSavedRouteMap("saved-1", 0).await().indefinitely();

        assertEquals(400, response.getStatus());
        assertEquals("vehicle route must contain at least two coordinates: 0", response.getEntity());
    }

    @Test
    void getSavedRouteMapReturnsBadGatewayForProviderFailure() {
        when(getSavedRouteMapUseCase.get(any())).thenReturn(io.smallrye.mutiny.Uni.createFrom().failure(
                new OpenRouteServiceException("OpenRouteService returned HTTP 500", 500, false)));

        Response response = resource.getSavedRouteMap("saved-1", 0).await().indefinitely();

        assertEquals(502, response.getStatus());
        assertEquals("OpenRouteService returned HTTP 500", response.getEntity());
    }

    @Test
    void moveRouteRequestReturnsUpdatedRoute() {
        SavedRouteResult saved = savedResult();
        when(moveRouteRequestUseCase.move(any())).thenReturn(io.smallrye.mutiny.Uni.createFrom().item(saved));

        Response response = resource.moveRouteRequest("saved-1", moveRequest()).await().indefinitely();

        assertEquals(200, response.getStatus());
        assertEquals(saved, response.getEntity());
        verify(moveRouteRequestUseCase).move(any());
    }

    @Test
    void moveRouteRequestReturnsNotFoundForMissingRoute() {
        when(moveRouteRequestUseCase.move(any())).thenReturn(io.smallrye.mutiny.Uni.createFrom().failure(
                new SavedRouteSuggestionNotFoundException("missing")));

        Response response = resource.moveRouteRequest("missing", moveRequest()).await().indefinitely();

        assertEquals(404, response.getStatus());
        assertEquals("Saved route suggestion not found: missing", response.getEntity());
    }

    @Test
    void moveRouteRequestReturnsBadRequestForValidationError() {
        when(moveRouteRequestUseCase.move(any())).thenReturn(io.smallrye.mutiny.Uni.createFrom().failure(
                new RouteMoveValidationException("move would exceed target vehicle capacity")));

        Response response = resource.moveRouteRequest("saved-1", moveRequest()).await().indefinitely();

        assertEquals(400, response.getStatus());
        assertEquals("move would exceed target vehicle capacity", response.getEntity());
    }

    @Test
    void moveRouteRequestReturnsConflictForDuplicateFingerprint() {
        when(moveRouteRequestUseCase.move(any())).thenReturn(io.smallrye.mutiny.Uni.createFrom().failure(new DuplicateSavedRouteException()));

        Response response = resource.moveRouteRequest("saved-1", moveRequest()).await().indefinitely();

        assertEquals(409, response.getStatus());
        assertEquals("Route suggestion already saved", response.getEntity());
    }

    @Test
    void deleteSavedRouteReturnsNoContent() {
        when(deleteSavedRouteSuggestionUseCase.delete("saved-1")).thenReturn(io.smallrye.mutiny.Uni.createFrom().voidItem());

        Response response = resource.deleteSavedRoute("saved-1").await().indefinitely();

        assertEquals(204, response.getStatus());
        verify(deleteSavedRouteSuggestionUseCase).delete("saved-1");
    }

    @Test
    void deleteSavedRouteReturnsBadRequestForValidationError() {
        when(deleteSavedRouteSuggestionUseCase.delete(" ")).thenReturn(io.smallrye.mutiny.Uni.createFrom().failure(
                new IllegalArgumentException("saved route id is required")));

        Response response = resource.deleteSavedRoute(" ").await().indefinitely();

        assertEquals(400, response.getStatus());
        assertEquals("saved route id is required", response.getEntity());
    }

    @Test
    void deleteSavedRouteReturnsNotFoundForMissingSuggestion() {
        when(deleteSavedRouteSuggestionUseCase.delete("missing")).thenReturn(io.smallrye.mutiny.Uni.createFrom().failure(
                new SavedRouteSuggestionNotFoundException("missing")));

        Response response = resource.deleteSavedRoute("missing").await().indefinitely();

        assertEquals(404, response.getStatus());
        assertEquals("Saved route suggestion not found: missing", response.getEntity());
    }

    @Test
    void deleteSavedRouteReturnsInternalServerErrorForUnexpectedFailure() {
        when(deleteSavedRouteSuggestionUseCase.delete("saved-1")).thenReturn(io.smallrye.mutiny.Uni.createFrom().failure(
                new RuntimeException("mongo unavailable")));

        Response response = resource.deleteSavedRoute("saved-1").await().indefinitely();

        assertEquals(500, response.getStatus());
        assertEquals("mongo unavailable", response.getEntity());
    }

    private CollectorRouteResource.MoveRouteRequestDTO moveRequest() {
        CollectorRouteResource.MoveRouteRequestDTO request = new CollectorRouteResource.MoveRouteRequestDTO();
        request.setCollectionRequestId("request-1");
        request.setSourceVehicleIndex(0);
        request.setTargetVehicleIndex(1);
        return request;
    }

    private CollectorRouteResource.SaveRouteRequestDTO saveRequest() {
        CollectorRouteResource.SaveRouteRequestDTO request = new CollectorRouteResource.SaveRouteRequestDTO();
        request.setCollectorId("collector-1");
        request.setSource("ROUTE_SUGGESTION");
        request.setSuggestion(suggestion());
        return request;
    }

    private SavedRouteMapResult routeMapResult() {
        LocalDateTime now = LocalDateTime.now();
        return new SavedRouteMapResult(
                "saved-1",
                RouteMapProvider.OPEN_ROUTE_SERVICE,
                "driving-car",
                List.of(new VehicleRouteMapResult(
                        0,
                        "sha256:fingerprint",
                        true,
                        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("type", "FeatureCollection"),
                        now,
                        now
                ))
        );
    }

    private SavedRouteResult savedResult() {
        LocalDateTime now = LocalDateTime.now();
        return new SavedRouteResult(
                "saved-1",
                "collector-1",
                SavedRouteStatus.OPEN,
                "fingerprint",
                List.of("request-1"),
                suggestion(),
                now,
                now,
                null
        );
    }

    private RouteOptimizationResult suggestion() {
        return new RouteOptimizationResult(
                SolverStatus.FEASIBLE,
                new SolverMetadata("TEST", 1, 10, 0),
                List.of(new RoutePlan(0, 100.0, 25.0, 10, List.of(
                        new RouteStop(1, "request-1", "address-1", -23.0, -46.0, 25.0, 25.0, 10)
                ))),
                List.of()
        );
    }

    private CollectorRouteResource.RouteOptimizationRequestDTO validRequest() {
        CollectorRouteResource.RouteOptimizationRequestDTO request = new CollectorRouteResource.RouteOptimizationRequestDTO();
        request.setCollectorId("collector-1");
        CollectorRouteResource.RouteVehicleDTO vehicle = new CollectorRouteResource.RouteVehicleDTO();
        vehicle.setCapacity(100.0);
        request.setVehicles(List.of(vehicle));
        request.setCandidateRequestIds(List.of("request-1"));

        CollectorRouteResource.StartLocationDTO start = new CollectorRouteResource.StartLocationDTO();
        start.setType("COORDINATES");
        start.setLatitude(-23.5505);
        start.setLongitude(-46.6333);
        request.setStart(start);
        return request;
    }
}
