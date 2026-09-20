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
