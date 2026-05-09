package org.example.presentation.rest;

import jakarta.ws.rs.core.Response;
import org.example.application.route.RouteModels.RouteOptimizationResult;
import org.example.application.route.RouteModels.RoutePlan;
import org.example.application.route.RouteModels.SolverMetadata;
import org.example.application.route.RouteModels.SolverStatus;
import org.example.application.usecase.RouteOptimizationUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollectorRouteResourceTest {
    private CollectorRouteResource resource;
    private RouteOptimizationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = mock(RouteOptimizationUseCase.class);
        resource = new CollectorRouteResource();
        resource.routeOptimizationUseCase = useCase;
    }

    @Test
    void suggestRoutesReturnsUseCaseResult() {
        when(useCase.suggestRoutes(any())).thenReturn(io.smallrye.mutiny.Uni.createFrom().item(
                new RouteOptimizationResult(
                        SolverStatus.FEASIBLE,
                        new SolverMetadata("TEST", 1, 10, 0),
                        List.of(new RoutePlan(0, 100.0, 25.0, 10, List.of())),
                        List.of()
                )
        ));

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
