package org.example.presentation.rest;

import jakarta.ws.rs.core.Response;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteAssignment;
import org.example.application.deliveryman.DeliverymanModels.FailStopCommand;
import org.example.application.deliveryman.DeliverymanModels.RouteAssignmentStatus;
import org.example.application.usecase.DeliverymanUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliverymanResourceTest {
    private DeliverymanResource resource;
    private DeliverymanUseCase deliverymanUseCase;

    @BeforeEach
    void setUp() {
        deliverymanUseCase = mock(DeliverymanUseCase.class);
        resource = new DeliverymanResource();
        resource.deliverymanUseCase = deliverymanUseCase;
    }

    @Test
    void completeStopReturnsReconciledAssignmentAndNoStore() {
        DeliveryRouteAssignment assignment = assignment(RouteAssignmentStatus.COMPLETED);
        when(deliverymanUseCase.completeAssignedStop(any(), any(), any(), any(), any(), any()))
                .thenReturn(io.smallrye.mutiny.Uni.createFrom().item(
                        new DeliverymanUseCase.CompletedStopResult(assignment, "request-1", "completion-1", false)));

        Response response = resource.completeStop("deliveryman-1", "assignment-1", "request-1", "Bearer driver",
                "00000000-0000-0000-0000-000000000001", new DeliverymanResource.ConfirmDeliveryStopDTO("001234"))
                .await().indefinitely();

        assertEquals(200, response.getStatus());
        assertEquals("no-store", response.getHeaderString("Cache-Control"));
        DeliverymanResource.CompletedDeliveryStopResponse body =
                (DeliverymanResource.CompletedDeliveryStopResponse) response.getEntity();
        assertEquals("request-1", body.requestId());
        assertEquals("completion-1", body.completionId());
        assertEquals(assignment, body.assignment());
        verify(deliverymanUseCase).completeAssignedStop("deliveryman-1", "assignment-1", "request-1",
                "Bearer driver", "001234", "00000000-0000-0000-0000-000000000001");
    }

    @Test
    void pendingReconciliationReturns202WithStableCompletionId() {
        DeliveryRouteAssignment assignment = assignment(RouteAssignmentStatus.IN_PROGRESS);
        when(deliverymanUseCase.completeAssignedStop(any(), any(), any(), any(), any(), any()))
                .thenReturn(io.smallrye.mutiny.Uni.createFrom().item(
                        new DeliverymanUseCase.CompletedStopResult(assignment, "request-1", "completion-2", true)));

        Response response = resource.completeStop("deliveryman-1", "assignment-1", "request-1", "Bearer driver",
                "00000000-0000-0000-0000-000000000002", new DeliverymanResource.ConfirmDeliveryStopDTO("001234"))
                .await().indefinitely();

        assertEquals(202, response.getStatus());
        assertEquals("no-store", response.getHeaderString("Cache-Control"));
        DeliverymanResource.PendingDeliveryStopResponse body =
                (DeliverymanResource.PendingDeliveryStopResponse) response.getEntity();
        assertEquals("request-1", body.requestId());
        assertEquals("completion-2", body.completionId());
        assertTrue(body.reconciliationPending());
    }

    @Test
    void failStopReturnsUpdatedAssignment() {
        DeliveryRouteAssignment assignment = assignment(RouteAssignmentStatus.IN_PROGRESS);
        when(deliverymanUseCase.failAssignedStop(any(), any(), any(), any()))
                .thenReturn(io.smallrye.mutiny.Uni.createFrom().item(assignment));

        Response response = resource.failStop(
                "deliveryman-1",
                "assignment-1",
                "request-1",
                new FailStopCommand("Generator unavailable", "No one home")
        ).await().indefinitely();

        assertEquals(200, response.getStatus());
        assertEquals(assignment, response.getEntity());
        verify(deliverymanUseCase).failAssignedStop(any(), any(), any(), any());
    }

    @Test
    void finishAssignmentReturnsRouteOutcome() {
        DeliveryRouteAssignment assignment = assignment(RouteAssignmentStatus.PARTIALLY_COMPLETED);
        when(deliverymanUseCase.finishAssignment("deliveryman-1", "assignment-1"))
                .thenReturn(io.smallrye.mutiny.Uni.createFrom().item(assignment));

        Response response = resource.finishAssignment("deliveryman-1", "assignment-1").await().indefinitely();

        assertEquals(200, response.getStatus());
        assertEquals(RouteAssignmentStatus.PARTIALLY_COMPLETED, ((DeliveryRouteAssignment) response.getEntity()).status());
    }

    private DeliveryRouteAssignment assignment(RouteAssignmentStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return new DeliveryRouteAssignment(
                "assignment-1",
                "collector-1",
                "deliveryman-1",
                "saved-1",
                0,
                status,
                List.of("request-1"),
                List.of(),
                now,
                now,
                status == RouteAssignmentStatus.IN_PROGRESS ? null : now,
                now
        );
    }
}
