package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.*;
import org.example.application.route.RouteModels.*;
import org.example.domain.entity.Address;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RouteOptimizationUseCaseTest {
    private CollectionRequestPort collectionRequestPort;
    private CollectorDiscoveryPort collectorDiscoveryPort;
    private AddressPort addressPort;
    private DistanceMatrixPort distanceMatrixPort;
    private RouteOptimizationPort routeOptimizationPort;
    private RouteOptimizationUseCase useCase;

    @BeforeEach
    void setUp() {
        collectionRequestPort = mock(CollectionRequestPort.class);
        collectorDiscoveryPort = mock(CollectorDiscoveryPort.class);
        addressPort = mock(AddressPort.class);
        distanceMatrixPort = mock(DistanceMatrixPort.class);
        routeOptimizationPort = mock(RouteOptimizationPort.class);

        useCase = new RouteOptimizationUseCase();
        useCase.collectionRequestPort = collectionRequestPort;
        useCase.collectorDiscoveryPort = collectorDiscoveryPort;
        useCase.addressPort = addressPort;
        useCase.distanceMatrixPort = distanceMatrixPort;
        useCase.routeOptimizationPort = routeOptimizationPort;
        useCase.defaultTimeLimitSeconds = 5;
        useCase.maxTimeLimitSeconds = 30;
        useCase.defaultDropPenalty = 100000;
        useCase.maxCandidates = 100;
    }

    @Test
    void suggestRoutesFiltersMissingCoordinatesAndDoesNotMutateRequests() {
        Address collectorAddress = address("collector-address", -23.5505, -46.6333);
        Collector collector = new Collector("collector-1", "user-1", "Collector", collectorAddress, List.of("paper"), 1.0);
        CollectionRequest routable = request("request-1", "address-1", CollectionRequest.Status.IN_PROGRESS, List.of("paper"), 10.0);
        CollectionRequest missingCoordinates = request("request-2", "address-2", CollectionRequest.Status.IN_PROGRESS, List.of("paper"), 5.0);
        Address routableAddress = address("address-1", -23.5605, -46.6433);
        Address incompleteAddress = address("address-2", null, null);

        when(collectorDiscoveryPort.findCollectorById("collector-1")).thenReturn(Uni.createFrom().item(collector));
        when(collectionRequestPort.findByIds(List.of("request-1", "request-2")))
                .thenReturn(Uni.createFrom().item(List.of(routable, missingCoordinates)));
        when(addressPort.findById("address-1")).thenReturn(Uni.createFrom().item(routableAddress));
        when(addressPort.findById("address-2")).thenReturn(Uni.createFrom().item(incompleteAddress));
        when(distanceMatrixPort.buildMatrixMeters(any(), any())).thenReturn(new long[][]{{0, 10}, {10, 0}});
        when(routeOptimizationPort.optimize(any(RouteOptimizationProblem.class)))
                .thenReturn(new RouteOptimizationResult(
                        SolverStatus.FEASIBLE,
                        new SolverMetadata("TEST", 1, 10, 0),
                        List.of(new RoutePlan(0, "Truck A", 20.0, 10.0, 10, List.of())),
                        List.of()
                ));

        RouteOptimizationResult result = useCase.suggestRoutes(command(List.of("request-1", "request-2"), 20.0))
                .await().indefinitely();

        assertEquals(SolverStatus.PARTIAL, result.status());
        assertEquals(1, result.unassigned().size());
        assertEquals("request-2", result.unassigned().getFirst().collectionRequestId());
        assertEquals(UnassignedReason.MISSING_COORDINATES, result.unassigned().getFirst().reason());
        assertEquals(CollectionRequest.Status.IN_PROGRESS, routable.getStatus());
        assertEquals(CollectionRequest.Status.IN_PROGRESS, missingCoordinates.getStatus());
        verify(routeOptimizationPort).optimize(any(RouteOptimizationProblem.class));
    }

    @Test
    void suggestRoutesReportsExplicitCandidateIdsNotFound() {
        Address collectorAddress = address("collector-address", -23.5505, -46.6333);
        Collector collector = new Collector("collector-1", "user-1", "Collector", collectorAddress, List.of("paper"), 1.0);
        CollectionRequest first = request("request-1", "address-1", CollectionRequest.Status.IN_PROGRESS, List.of("paper"), 10.0);
        CollectionRequest second = request("request-2", "address-2", CollectionRequest.Status.IN_PROGRESS, List.of("paper"), 10.0);

        when(collectorDiscoveryPort.findCollectorById("collector-1")).thenReturn(Uni.createFrom().item(collector));
        when(collectionRequestPort.findByIds(List.of("request-1", "request-2", "missing-request")))
                .thenReturn(Uni.createFrom().item(List.of(first, second)));
        when(addressPort.findById("address-1")).thenReturn(Uni.createFrom().item(address("address-1", -23.5605, -46.6433)));
        when(addressPort.findById("address-2")).thenReturn(Uni.createFrom().item(address("address-2", -23.5705, -46.6533)));
        when(distanceMatrixPort.buildMatrixMeters(any(), any())).thenReturn(new long[][]{{0, 10, 20}, {10, 0, 10}, {20, 10, 0}});
        when(routeOptimizationPort.optimize(any(RouteOptimizationProblem.class)))
                .thenReturn(new RouteOptimizationResult(
                        SolverStatus.FEASIBLE,
                        new SolverMetadata("TEST", 1, 20, 0),
                        List.of(new RoutePlan(0, "Truck A", 20.0, 20.0, 20, List.of(
                                new RouteStop(1, "request-1", "address-1", -23.5605, -46.6433, 10.0, 10.0, 10),
                                new RouteStop(2, "request-2", "address-2", -23.5705, -46.6533, 10.0, 20.0, 10)
                        ))),
                        List.of()
                ));

        RouteOptimizationResult result = useCase.suggestRoutes(command(List.of("request-1", "request-2", "missing-request"), 20.0))
                .await().indefinitely();

        assertEquals(SolverStatus.PARTIAL, result.status());
        assertEquals(1, result.unassigned().size());
        assertEquals("missing-request", result.unassigned().getFirst().collectionRequestId());
        assertEquals(UnassignedReason.NOT_FOUND, result.unassigned().getFirst().reason());
    }

    @Test
    void suggestRoutesAddsReasonForSolverMissingRoutableStop() {
        Address collectorAddress = address("collector-address", -23.5505, -46.6333);
        Collector collector = new Collector("collector-1", "user-1", "Collector", collectorAddress, List.of("paper"), 1.0);
        CollectionRequest first = request("request-1", "address-1", CollectionRequest.Status.IN_PROGRESS, List.of("paper"), 10.0);
        CollectionRequest second = request("request-2", "address-2", CollectionRequest.Status.IN_PROGRESS, List.of("paper"), 10.0);

        when(collectorDiscoveryPort.findCollectorById("collector-1")).thenReturn(Uni.createFrom().item(collector));
        when(collectionRequestPort.findByIds(List.of("request-1", "request-2"))).thenReturn(Uni.createFrom().item(List.of(first, second)));
        when(addressPort.findById("address-1")).thenReturn(Uni.createFrom().item(address("address-1", -23.5605, -46.6433)));
        when(addressPort.findById("address-2")).thenReturn(Uni.createFrom().item(address("address-2", -23.5705, -46.6533)));
        when(distanceMatrixPort.buildMatrixMeters(any(), any())).thenReturn(new long[][]{{0, 10, 20}, {10, 0, 10}, {20, 10, 0}});
        when(routeOptimizationPort.optimize(any(RouteOptimizationProblem.class)))
                .thenReturn(new RouteOptimizationResult(
                        SolverStatus.FEASIBLE,
                        new SolverMetadata("TEST", 1, 20, 0),
                        List.of(new RoutePlan(0, "Truck A", 20.0, 10.0, 10, List.of(
                                new RouteStop(1, "request-1", "address-1", -23.5605, -46.6433, 10.0, 10.0, 10)
                        ))),
                        List.of()
                ));

        RouteOptimizationResult result = useCase.suggestRoutes(command(List.of("request-1", "request-2"), 20.0))
                .await().indefinitely();

        assertEquals(SolverStatus.PARTIAL, result.status());
        assertEquals(1, result.unassigned().size());
        assertEquals("request-2", result.unassigned().getFirst().collectionRequestId());
        assertEquals(UnassignedReason.SOLVER_DROPPED, result.unassigned().getFirst().reason());
    }

    @Test
    void suggestRoutesRaisesDropPenaltyAboveSequentialRouteCost() {
        Address collectorAddress = address("collector-address", -23.5505, -46.6333);
        Collector collector = new Collector("collector-1", "user-1", "Collector", collectorAddress, List.of("paper"), 1.0);
        CollectionRequest first = request("request-1", "address-1", CollectionRequest.Status.IN_PROGRESS, List.of("paper"), 10.0);
        CollectionRequest second = request("request-2", "address-2", CollectionRequest.Status.IN_PROGRESS, List.of("paper"), 10.0);

        when(collectorDiscoveryPort.findCollectorById("collector-1")).thenReturn(Uni.createFrom().item(collector));
        when(collectionRequestPort.findByIds(List.of("request-1", "request-2"))).thenReturn(Uni.createFrom().item(List.of(first, second)));
        when(addressPort.findById("address-1")).thenReturn(Uni.createFrom().item(address("address-1", -23.5605, -46.6433)));
        when(addressPort.findById("address-2")).thenReturn(Uni.createFrom().item(address("address-2", -23.5705, -46.6533)));
        when(distanceMatrixPort.buildMatrixMeters(any(), any())).thenReturn(new long[][]{
                {0, 300, 400},
                {300, 0, 500},
                {400, 500, 0}
        });
        when(routeOptimizationPort.optimize(any(RouteOptimizationProblem.class)))
                .thenReturn(new RouteOptimizationResult(
                        SolverStatus.FEASIBLE,
                        new SolverMetadata("TEST", 1, 800, 0),
                        List.of(new RoutePlan(0, "Truck A", 20.0, 20.0, 800, List.of())),
                        List.of()
                ));

        useCase.suggestRoutes(command(List.of("request-1", "request-2"), 20.0, new RouteOptions(5, true, 1L)))
                .await().indefinitely();

        ArgumentCaptor<RouteOptimizationProblem> captor = ArgumentCaptor.forClass(RouteOptimizationProblem.class);
        verify(routeOptimizationPort).optimize(captor.capture());
        assertEquals(1201L, captor.getValue().dropPenalty());
    }

    @Test
    void suggestRoutesReturnsInfeasibleWhenNoStopsCanBeRouted() {
        Address collectorAddress = address("collector-address", -23.5505, -46.6333);
        Collector collector = new Collector("collector-1", "user-1", "Collector", collectorAddress, List.of("paper"), 1.0);
        CollectionRequest overCapacity = request("request-1", "address-1", CollectionRequest.Status.IN_PROGRESS, List.of("paper"), 100.0);

        when(collectorDiscoveryPort.findCollectorById("collector-1")).thenReturn(Uni.createFrom().item(collector));
        when(collectionRequestPort.findByIds(List.of("request-1"))).thenReturn(Uni.createFrom().item(List.of(overCapacity)));
        when(addressPort.findById("address-1")).thenReturn(Uni.createFrom().item(address("address-1", -23.5605, -46.6433)));

        RouteOptimizationResult result = useCase.suggestRoutes(command(List.of("request-1"), 20.0))
                .await().indefinitely();

        assertEquals(SolverStatus.INFEASIBLE, result.status());
        assertEquals(UnassignedReason.OVER_CAPACITY, result.unassigned().getFirst().reason());
        verifyNoInteractions(distanceMatrixPort, routeOptimizationPort);
    }

    @Test
    void suggestRoutesRejectsPendingRequests() {
        Address collectorAddress = address("collector-address", -23.5505, -46.6333);
        Collector collector = new Collector("collector-1", "user-1", "Collector", collectorAddress, List.of("paper"), 1.0);
        CollectionRequest pending = request("request-1", "address-1", CollectionRequest.Status.PENDING, List.of("paper"), 10.0);

        when(collectorDiscoveryPort.findCollectorById("collector-1")).thenReturn(Uni.createFrom().item(collector));
        when(collectionRequestPort.findByIds(List.of("request-1"))).thenReturn(Uni.createFrom().item(List.of(pending)));
        when(addressPort.findById("address-1")).thenReturn(Uni.createFrom().item(address("address-1", -23.5605, -46.6433)));

        RouteOptimizationResult result = useCase.suggestRoutes(command(List.of("request-1"), 20.0))
                .await().indefinitely();

        assertEquals(SolverStatus.INFEASIBLE, result.status());
        assertEquals(UnassignedReason.NOT_IN_PROGRESS, result.unassigned().getFirst().reason());
        verifyNoInteractions(distanceMatrixPort, routeOptimizationPort);
    }

    private RouteOptimizationCommand command(List<String> candidateIds, double capacity) {
        return command(candidateIds, capacity, new RouteOptions(5, false, null));
    }

    private RouteOptimizationCommand command(List<String> candidateIds, double capacity, RouteOptions options) {
        return new RouteOptimizationCommand(
                "collector-1",
                List.of(new RouteVehicle(0, "Truck A", capacity)),
                new StartLocation(StartLocationType.COLLECTOR_ADDRESS, null, null, null),
                true,
                candidateIds,
                null,
                options
        );
    }

    private CollectionRequest request(String id, String addressId, CollectionRequest.Status status, List<String> materialIds, double weight) {
        CollectionRequest request = new CollectionRequest("generator-1", addressId, materialIds, weight);
        request.setId(id);
        request.setStatus(status);
        return request;
    }

    private Address address(String id, Double latitude, Double longitude) {
        return new Address(id, "Street", "City", "01001000", "100", "SP", latitude, longitude, "ENRICHED", "test");
    }
}
