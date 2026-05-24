package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.*;
import org.example.application.route.RouteModels.*;
import org.example.domain.entity.Address;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
                        List.of(new RoutePlan(0, 20.0, 10.0, 10, List.of())),
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
        return new RouteOptimizationCommand(
                "collector-1",
                List.of(new RouteVehicle(0, capacity)),
                new StartLocation(StartLocationType.COLLECTOR_ADDRESS, null, null, null),
                true,
                candidateIds,
                null,
                new RouteOptions(5, false, null)
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
