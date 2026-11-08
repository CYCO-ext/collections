package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.deliveryman.DeliverymanModels.AssignDeliveryRouteCommand;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteAssignment;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteLog;
import org.example.application.deliveryman.DeliverymanModels.FailStopCommand;
import org.example.application.deliveryman.DeliverymanModels.RouteAssignmentStatus;
import org.example.application.deliveryman.DeliverymanModels.RouteLogEventType;
import org.example.application.port.out.DeliveryRouteAssignmentPort;
import org.example.application.port.out.DeliveryRouteLogPort;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.AuthenticatedActorPort;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
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
    DeliveryRouteLogPort routeLogPort;

    @Mock
    SavedRoutePort savedRoutePort;

    @Mock
    CompletionUseCase completionUseCase;

    @Mock
    AuthenticatedActorPort authenticatedActorPort;

    @Mock
    CollectionRequestPort collectionRequestPort;

    @BeforeEach
    void setUp() {
        useCase = new DeliverymanUseCase();
        useCase.deliverymanPort = deliverymanPort;
        useCase.deliverymanDirectoryPort = deliverymanDirectoryPort;
        useCase.assignmentPort = assignmentPort;
        useCase.routeLogPort = routeLogPort;
        useCase.savedRoutePort = savedRoutePort;
        useCase.completionUseCase = completionUseCase;
        useCase.authenticatedActorPort = authenticatedActorPort;
        useCase.collectionRequestPort = collectionRequestPort;
        lenient().when(authenticatedActorPort.resolve("bearer")).thenReturn(Uni.createFrom().item(
                new AuthenticatedActorPort.AuthenticatedActor(
                        "user-1", "DELIVERYMAN", null, null, "deliveryman-1", "collector-1")));
        lenient().when(collectionRequestPort.findByIds(any()))
                .thenReturn(Uni.createFrom().item(List.of()));
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
    void assignRouteReturnsExistingAssignmentWhenAlreadyAssignedToSameDeliveryman() {
        DeliveryRouteAssignment existing = assignment(RouteAssignmentStatus.ASSIGNED, List.of());
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(savedRoute(SavedRouteStatus.OPEN)));
        when(deliverymanDirectoryPort.findById("deliveryman-1")).thenReturn(Uni.createFrom().item(
                new DeliverymanRef("deliveryman-1", "collector-1", "Driver One", true)
        ));
        when(assignmentPort.findOpenBySavedRouteIdAndVehicleIndex("saved-1", 0)).thenReturn(Uni.createFrom().item(existing));

        DeliveryRouteAssignment result = useCase.assignRoute(command()).await().indefinitely();

        assertEquals(existing, result);
        verify(assignmentPort, never()).save(any());
        verify(assignmentPort, never()).update(any());
    }

    @Test
    void assignRouteReassignsExistingAssignmentBeforeRouteStarts() {
        DeliveryRouteAssignment existing = assignment(RouteAssignmentStatus.ASSIGNED, List.of());
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(savedRoute(SavedRouteStatus.OPEN)));
        when(deliverymanDirectoryPort.findById("deliveryman-2")).thenReturn(Uni.createFrom().item(
                new DeliverymanRef("deliveryman-2", "collector-1", "Driver Two", true)
        ));
        when(assignmentPort.findOpenBySavedRouteIdAndVehicleIndex("saved-1", 0)).thenReturn(Uni.createFrom().item(existing));
        when(assignmentPort.update(any())).thenReturn(Uni.createFrom().voidItem());

        DeliveryRouteAssignment result = useCase.assignRoute(
                new AssignDeliveryRouteCommand("collector-1", "deliveryman-2", "saved-1", 0)
        ).await().indefinitely();

        assertEquals("assignment-1", result.id());
        assertEquals("deliveryman-2", result.deliverymanId());
        assertEquals(RouteAssignmentStatus.ASSIGNED, result.status());
        verify(assignmentPort, never()).save(any());
        verify(assignmentPort).update(any());
    }

    @Test
    void assignRouteRejectsReassignmentAfterRouteStarts() {
        DeliveryRouteAssignment existing = assignment(RouteAssignmentStatus.IN_PROGRESS, List.of());
        when(savedRoutePort.findById("saved-1")).thenReturn(Uni.createFrom().item(savedRoute(SavedRouteStatus.OPEN)));
        when(deliverymanDirectoryPort.findById("deliveryman-2")).thenReturn(Uni.createFrom().item(
                new DeliverymanRef("deliveryman-2", "collector-1", "Driver Two", true)
        ));
        when(assignmentPort.findOpenBySavedRouteIdAndVehicleIndex("saved-1", 0)).thenReturn(Uni.createFrom().item(existing));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> useCase.assignRoute(
                        new AssignDeliveryRouteCommand("collector-1", "deliveryman-2", "saved-1", 0)
                ).await().indefinitely());

        assertEquals("Assignment already started", exception.getMessage());
        verify(assignmentPort, never()).save(any());
        verify(assignmentPort, never()).update(any());
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

    @Test
    void startAssignmentTransitionsToInProgressAndAppendsRouteStartedLog() {
        DeliveryRouteAssignment assignment = assignment(RouteAssignmentStatus.ASSIGNED, List.of());
        when(assignmentPort.findById("assignment-1")).thenReturn(Uni.createFrom().item(assignment));
        when(assignmentPort.update(any())).thenReturn(Uni.createFrom().voidItem());
        when(routeLogPort.append(any())).thenReturn(Uni.createFrom().voidItem());
        when(collectionRequestPort.findByIds(any())).thenReturn(Uni.createFrom().item(List.of()));

        DeliveryRouteAssignment result = useCase.startAssignment("deliveryman-1", "assignment-1").await().indefinitely();

        assertEquals(RouteAssignmentStatus.IN_PROGRESS, result.status());
        ArgumentCaptor<DeliveryRouteLog> captor = ArgumentCaptor.forClass(DeliveryRouteLog.class);
        verify(routeLogPort).append(captor.capture());
        assertEquals(RouteLogEventType.ROUTE_STARTED, captor.getValue().eventType());
        assertEquals("saved-1", captor.getValue().savedRouteId());
    }

    @Test
    void completeAssignedStopValidatesCodeBeforeReturningReconciledAssignment() {
        DeliveryRouteAssignment assignment = assignment(RouteAssignmentStatus.IN_PROGRESS, List.of());
        DeliveryRouteAssignment completed = assignment(RouteAssignmentStatus.COMPLETED, List.of("request-1"));
        when(assignmentPort.findById("assignment-1"))
                .thenReturn(Uni.createFrom().item(assignment))
                .thenReturn(Uni.createFrom().item(completed));
        when(completionUseCase.confirmDeliverymanWithCode(
                "request-1", "bearer", "001234", "00000000-0000-0000-0000-000000000001"))
                .thenReturn(Uni.createFrom().item(new CompletionUseCase.CompletionResult("request-1", "COMPLETED", "completion-1")));

        DeliveryRouteAssignment result = useCase.completeAssignedStop(
                "deliveryman-1", "assignment-1", "request-1", "bearer", "001234",
                "00000000-0000-0000-0000-000000000001").await().indefinitely();

        assertEquals(RouteAssignmentStatus.COMPLETED, result.status());
        assertEquals(List.of("request-1"), result.completedCollectionRequestIds());
        var order = inOrder(completionUseCase, assignmentPort);
        order.verify(completionUseCase).confirmDeliverymanWithCode(
                "request-1", "bearer", "001234", "00000000-0000-0000-0000-000000000001");
        order.verify(assignmentPort).findById("assignment-1");
        verify(assignmentPort, never()).update(any());
        verify(routeLogPort, never()).append(any());
    }

    @Test
    void invalidCodeLeavesAssignmentAndSuccessLogUntouched() {
        DeliveryRouteAssignment assignment = assignment(RouteAssignmentStatus.IN_PROGRESS, List.of());
        when(assignmentPort.findById("assignment-1")).thenReturn(Uni.createFrom().item(assignment));
        when(completionUseCase.confirmDeliverymanWithCode(
                "request-1", "bearer", "999999", "00000000-0000-0000-0000-000000000001"))
                .thenReturn(Uni.createFrom().failure(new CompletionCodeValidationException(
                        "CODE_INVALID", "Code is invalid", 422, null)));

        assertThrows(CompletionCodeValidationException.class, () -> useCase.completeAssignedStop(
                "deliveryman-1", "assignment-1", "request-1", "bearer", "999999",
                "00000000-0000-0000-0000-000000000001").await().indefinitely());

        verify(completionUseCase).confirmDeliverymanWithCode(any(), any(), any(), any());
        verify(assignmentPort, never()).update(any());
        verify(routeLogPort, never()).append(any());
    }

    @Test
    void stopOutsideDeliverymanAssignmentDoesNotConsumeCodeAttempt() {
        when(assignmentPort.findById("assignment-1"))
                .thenReturn(Uni.createFrom().item(assignment(RouteAssignmentStatus.IN_PROGRESS, List.of())));

        assertThrows(IllegalStateException.class, () -> useCase.completeAssignedStop(
                "deliveryman-1", "assignment-1", "other-request", "bearer", "999999",
                "00000000-0000-0000-0000-000000000001").await().indefinitely());

        verify(completionUseCase, never()).confirmDeliverymanWithCode(any(), any(), any(), any());
        verify(assignmentPort, never()).update(any());
    }

    @Test
    void validIdempotentRetryWorksAfterAssignmentBecomesTerminal() {
        DeliveryRouteAssignment terminal = assignment(RouteAssignmentStatus.COMPLETED, List.of("request-1"));
        when(assignmentPort.findById("assignment-1"))
                .thenReturn(Uni.createFrom().item(terminal))
                .thenReturn(Uni.createFrom().item(terminal));
        when(completionUseCase.confirmDeliverymanWithCode(
                "request-1", "bearer", "001234", "00000000-0000-0000-0000-000000000001"))
                .thenReturn(Uni.createFrom().item(new CompletionUseCase.CompletionResult("request-1", "COMPLETED", "completion-1")));

        DeliveryRouteAssignment result = useCase.completeAssignedStop(
                "deliveryman-1", "assignment-1", "request-1", "bearer", "001234",
                "00000000-0000-0000-0000-000000000001").await().indefinitely();

        assertEquals(RouteAssignmentStatus.COMPLETED, result.status());
        assertEquals("deliveryman-1", result.deliverymanId());
        verify(completionUseCase).confirmDeliverymanWithCode(any(), any(), any(), any());
        verify(assignmentPort, never()).update(any());
    }

    @Test
    void failAssignedStopAppendsFailureLogAndDoesNotCompleteRequest() {
        DeliveryRouteAssignment assignment = assignment(RouteAssignmentStatus.IN_PROGRESS, List.of());
        when(assignmentPort.findById("assignment-1")).thenReturn(Uni.createFrom().item(assignment));
        when(assignmentPort.update(any())).thenReturn(Uni.createFrom().voidItem());
        when(routeLogPort.append(any())).thenReturn(Uni.createFrom().voidItem());

        DeliveryRouteAssignment result = useCase.failAssignedStop(
                "deliveryman-1",
                "assignment-1",
                "request-1",
                new FailStopCommand("Generator unavailable", "No one home")
        ).await().indefinitely();

        assertEquals(List.of(), result.completedCollectionRequestIds());
        ArgumentCaptor<DeliveryRouteLog> captor = ArgumentCaptor.forClass(DeliveryRouteLog.class);
        verify(routeLogPort).append(captor.capture());
        assertEquals(RouteLogEventType.STOP_FAILED, captor.getValue().eventType());
        assertEquals("Generator unavailable", captor.getValue().failureReason());
        verify(completionUseCase, never()).confirmCollectorCompletion(any());
    }

    @Test
    void finishAssignmentMarksPartiallyCompletedFromMixedStopLogs() {
        DeliveryRouteAssignment assignment = assignment(RouteAssignmentStatus.IN_PROGRESS, List.of("request-1"));
        when(assignmentPort.findById("assignment-1")).thenReturn(Uni.createFrom().item(assignment));
        when(routeLogPort.findBySavedRouteIdAndVehicleIndex("saved-1", 0)).thenReturn(Uni.createFrom().item(List.of(
                log("request-1", RouteLogEventType.STOP_COMPLETED),
                log("request-2", RouteLogEventType.STOP_FAILED)
        )));
        when(assignmentPort.update(any())).thenReturn(Uni.createFrom().voidItem());
        when(routeLogPort.append(any())).thenReturn(Uni.createFrom().voidItem());

        DeliveryRouteAssignment result = useCase.finishAssignment("deliveryman-1", "assignment-1").await().indefinitely();

        assertEquals(RouteAssignmentStatus.PARTIALLY_COMPLETED, result.status());
        ArgumentCaptor<DeliveryRouteLog> captor = ArgumentCaptor.forClass(DeliveryRouteLog.class);
        verify(routeLogPort).append(captor.capture());
        assertEquals(RouteLogEventType.ROUTE_COMPLETED, captor.getValue().eventType());
    }

    private AssignDeliveryRouteCommand command() {
        return new AssignDeliveryRouteCommand("collector-1", "deliveryman-1", "saved-1", 0);
    }

    private DeliveryRouteAssignment assignment(RouteAssignmentStatus status, List<String> completedIds) {
        LocalDateTime now = LocalDateTime.now();
        return new DeliveryRouteAssignment(
                "assignment-1",
                "collector-1",
                "deliveryman-1",
                "saved-1",
                0,
                status,
                List.of("request-1", "request-2"),
                completedIds,
                now,
                status == RouteAssignmentStatus.ASSIGNED ? null : now,
                null,
                now
        );
    }

    private DeliveryRouteLog log(String requestId, RouteLogEventType eventType) {
        return DeliveryRouteLog.create(assignment(RouteAssignmentStatus.IN_PROGRESS, List.of()), requestId, eventType, null, null, LocalDateTime.now());
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
