package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.deliveryman.DeliverymanModels.AssignDeliveryRouteCommand;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteAssignment;
import org.example.application.deliveryman.DeliverymanModels.RouteAssignmentStatus;
import org.example.application.port.out.DeliveryRouteAssignmentPort;
import org.example.application.port.out.DeliverymanDirectoryPort;
import org.example.application.port.out.DeliverymanDirectoryPort.DeliverymanRef;
import org.example.application.port.out.DeliverymanPort;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.RouteModels.RouteOptimizationResult;
import org.example.application.route.RouteModels.RoutePlan;
import org.example.application.route.RouteModels.RouteStop;
import org.example.application.route.RouteModels.SolverMetadata;
import org.example.application.route.RouteModels.SolverStatus;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliverymanUseCaseTest {
    private DeliverymanUseCase useCase;

    @Mock
    DeliverymanPort deliverymanPort;

    @Mock
    DeliverymanDirectoryPort deliverymanDirectoryPort;

    @Mock
    DeliveryRouteAssignmentPort assignmentPort;

    @Mock
    SavedRoutePort savedRoutePort;

    @BeforeEach
    void setUp() {
        useCase = new DeliverymanUseCase();
        useCase.deliverymanPort = deliverymanPort;
        useCase.deliverymanDirectoryPort = deliverymanDirectoryPort;
        useCase.assignmentPort = assignmentPort;
        useCase.savedRoutePort = savedRoutePort;
    }

    @Test
    void assignRouteStoresAssignmentForActiveDeliverymanOwnedByCollector() {
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(savedRoute(SavedRouteStatus.OPEN)));
        when(deliverymanDirectoryPort.findById("deliveryman-1")).thenReturn(Uni.createFrom().item(
                new DeliverymanRef("deliveryman-1", "collector-1", "Driver One", true)
        ));
        when(assignmentPort.findOpenBySavedRouteIdAndVehicleIndex("saved-1", 0)).thenReturn(Uni.createFrom().nullItem());
        when(assignmentPort.save(any())).thenReturn(Uni.createFrom().voidItem());

        DeliveryRouteAssignment result = useCase.assignRoute(command()).await().indefinitely();

        assertEquals("collector-1", result.collectorId());
        assertEquals("deliveryman-1", result.deliverymanId());
        assertEquals("saved-1", result.savedRouteId());
        assertEquals(0, result.vehicleIndex());
        assertEquals(RouteAssignmentStatus.ASSIGNED, result.status());
        assertEquals(List.of("request-1"), result.assignedCollectionRequestIds());
        ArgumentCaptor<DeliveryRouteAssignment> captor = ArgumentCaptor.forClass(DeliveryRouteAssignment.class);
        verify(assignmentPort).save(captor.capture());
        assertEquals("deliveryman-1", captor.getValue().deliverymanId());
    }

    @Test
    void assignRouteRejectsDeliverymanFromAnotherCollector() {
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(savedRoute(SavedRouteStatus.OPEN)));
        when(deliverymanDirectoryPort.findById("deliveryman-1")).thenReturn(Uni.createFrom().item(
                new DeliverymanRef("deliveryman-1", "collector-2", "Driver One", true)
        ));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> useCase.assignRoute(command()).await().indefinitely());

        assertEquals("Deliveryman does not belong to collector", exception.getMessage());
        verify(assignmentPort, never()).save(any());
    }

    @Test
    void assignRouteRejectsInactiveDeliveryman() {
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(savedRoute(SavedRouteStatus.OPEN)));
        when(deliverymanDirectoryPort.findById("deliveryman-1")).thenReturn(Uni.createFrom().item(
                new DeliverymanRef("deliveryman-1", "collector-1", "Driver One", false)
        ));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> useCase.assignRoute(command()).await().indefinitely());

        assertEquals("Deliveryman is not active", exception.getMessage());
        verify(assignmentPort, never()).save(any());
    }

    @Test
    void assignRouteRejectsClosedSavedRoute() {
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(savedRoute(SavedRouteStatus.CLOSED)));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> useCase.assignRoute(command()).await().indefinitely());

        assertEquals("Saved route is not OPEN", exception.getMessage());
        verify(deliverymanDirectoryPort, never()).findById(any());
        verify(assignmentPort, never()).save(any());
    }

    @Test
    void assignRouteRejectsInvalidVehicleIndex() {
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(savedRoute(SavedRouteStatus.OPEN)));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> useCase.assignRoute(new AssignDeliveryRouteCommand("collector-1", "deliveryman-1", "saved-1", 9)).await().indefinitely());

        assertEquals("Saved route vehicle index not found", exception.getMessage());
        verify(deliverymanDirectoryPort, never()).findById(any());
        verify(assignmentPort, never()).save(any());
    }

    private AssignDeliveryRouteCommand command() {
        return new AssignDeliveryRouteCommand("collector-1", "deliveryman-1", "saved-1", 0);
    }

    private SavedRouteSuggestion savedRoute(SavedRouteStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return new SavedRouteSuggestion(
                "saved-1",
                "collector-1",
                status,
                "fingerprint",
                List.of("request-1"),
                List.of(),
                new RouteOptimizationResult(
                        SolverStatus.FEASIBLE,
                        new SolverMetadata("TEST", 1, 10, 0),
                        List.of(new RoutePlan(0, "Truck A", 100.0, 10.0, 1000, List.of(
                                new RouteStop(1, "request-1", "address-1", -23.0, -46.0, 10.0, 10.0, 1000)
                        ))),
                        List.of()
                ),
                now,
                now,
                status == SavedRouteStatus.CLOSED ? now : null
        );
    }
}
