package org.example.presentation.rest;

import jakarta.ws.rs.core.Response;
import org.example.application.route.RouteModels.RouteOptimizationResult;
import org.example.application.route.RouteModels.RoutePlan;
import org.example.application.route.RouteModels.RouteStop;
import org.example.application.route.RouteModels.SolverMetadata;
import org.example.application.route.RouteModels.SolverStatus;
import org.example.application.route.SavedRouteModels.SavedRouteResult;
import org.example.application.route.SavedRouteModels.SavedRouteStatus;
import org.example.application.usecase.DuplicateSavedRouteException;
import org.example.application.usecase.ListSavedRoutesUseCase;
import org.example.application.usecase.RouteOptimizationUseCase;
import org.example.application.usecase.SaveRouteSuggestionUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectorRouteResourceTest {
    private CollectorRouteResource resource;
    private RouteOptimizationUseCase routeOptimizationUseCase;
    private SaveRouteSuggestionUseCase saveRouteSuggestionUseCase;
    private ListSavedRoutesUseCase listSavedRoutesUseCase;

    @BeforeEach
    void setUp() {
        routeOptimizationUseCase = mock(RouteOptimizationUseCase.class);
        saveRouteSuggestionUseCase = mock(SaveRouteSuggestionUseCase.class);
        listSavedRoutesUseCase = mock(ListSavedRoutesUseCase.class);
        resource = new CollectorRouteResource();
        resource.routeOptimizationUseCase = routeOptimizationUseCase;
        resource.saveRouteSuggestionUseCase = saveRouteSuggestionUseCase;
        resource.listSavedRoutesUseCase = listSavedRoutesUseCase;
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

    private CollectorRouteResource.SaveRouteRequestDTO saveRequest() {
        CollectorRouteResource.SaveRouteRequestDTO request = new CollectorRouteResource.SaveRouteRequestDTO();
        request.setCollectorId("collector-1");
        request.setSource("ROUTE_SUGGESTION");
        request.setSuggestion(suggestion());
        return request;
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
        request.setVehicleCount(1);
        request.setVehicleCapacity(100.0);
        request.setCandidateRequestIds(List.of("request-1"));

        CollectorRouteResource.StartLocationDTO start = new CollectorRouteResource.StartLocationDTO();
        start.setType("COORDINATES");
        start.setLatitude(-23.5505);
        start.setLongitude(-46.6333);
        request.setStart(start);
        return request;
    }
}
