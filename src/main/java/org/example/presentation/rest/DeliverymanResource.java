package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.example.application.deliveryman.DeliverymanModels.AssignDeliveryRouteCommand;
import org.example.application.deliveryman.DeliverymanModels.FailStopCommand;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteAssignment;
import org.example.application.deliveryman.DeliverymanModels.SyncDeliverymanCommand;
import org.example.application.usecase.DeliverymanUseCase;
import org.example.application.usecase.CollectionRequestNotFoundException;
import org.example.application.usecase.CompletionCodeConflictException;
import org.example.application.usecase.CompletionCodeForbiddenException;
import org.example.application.usecase.CompletionCodeUnauthenticatedException;
import org.example.application.usecase.CompletionCodeValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/")
@Tag(name = "Deliverymen", description = "Synchronize deliverymen and manage route assignments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeliverymanResource {
    private static final Logger LOG = LoggerFactory.getLogger(DeliverymanResource.class);

    @Inject
    DeliverymanUseCase deliverymanUseCase;

    @Operation(summary = "Synchronize a deliveryman profile")
    @POST
    @Path("/deliverymen/sync")
    public Uni<Response> syncDeliveryman(SyncDeliverymanCommand command) {
        LOG.info("POST /deliverymen/sync");
        return deliverymanUseCase.syncDeliveryman(command)
                .onItem().transform(it -> Response.ok().build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex -> Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error syncing deliveryman", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    @Operation(summary = "List deliverymen for a collector")
    @GET
    @Path("/collectors/{collectorId}/deliverymen")
    public Uni<Response> listDeliverymen(@PathParam("collectorId") String collectorId) {
        LOG.info("GET /collectors/{}/deliverymen", collectorId);
        return deliverymanUseCase.listDeliverymen(collectorId)
                .onItem().transform(result -> Response.ok(result).build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex -> Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error listing deliverymen", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    @Operation(summary = "Assign a deliveryman to a route")
    @POST
    @Path("/collectors/{collectorId}/routes/{savedRouteId}/assign")
    public Uni<Response> assignRoute(
            @PathParam("collectorId") String collectorId,
            @PathParam("savedRouteId") String savedRouteId,
            AssignRouteDTO dto
    ) {
        LOG.info("POST /collectors/{}/routes/{}/assign", collectorId, savedRouteId);
        AssignDeliveryRouteCommand command = new AssignDeliveryRouteCommand(
                collectorId,
                dto == null ? null : dto.deliverymanId,
                savedRouteId,
                dto == null ? null : dto.vehicleIndex
        );
        return deliverymanUseCase.assignRoute(command)
                .onItem().transform(result -> Response.status(Response.Status.CREATED).entity(result).build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex -> Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure(IllegalStateException.class).recoverWithItem(ex -> Response.status(Response.Status.CONFLICT).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error assigning delivery route", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    @Operation(summary = "List a deliveryman's route assignments")
    @GET
    @Path("/deliverymen/{deliverymanId}/assignments")
    public Uni<Response> listAssignments(@PathParam("deliverymanId") String deliverymanId) {
        LOG.info("GET /deliverymen/{}/assignments", deliverymanId);
        return deliverymanUseCase.listActiveAssignments(deliverymanId)
                .onItem().transform(result -> Response.ok(result).build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex -> Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error listing delivery assignments", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    @Operation(summary = "Start a route assignment")
    @POST
    @Path("/deliverymen/{deliverymanId}/assignments/{assignmentId}/start")
    public Uni<Response> startAssignment(
            @PathParam("deliverymanId") String deliverymanId,
            @PathParam("assignmentId") String assignmentId
    ) {
        LOG.info("POST /deliverymen/{}/assignments/{}/start", deliverymanId, assignmentId);
        return deliverymanUseCase.startAssignment(deliverymanId, assignmentId)
                .onItem().transform(result -> Response.ok(result).build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex -> Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure(IllegalStateException.class).recoverWithItem(ex -> Response.status(Response.Status.CONFLICT).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error starting delivery assignment", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    @Operation(summary = "Complete a route stop using the completion code")
    @POST
    @Path("/deliverymen/{deliverymanId}/assignments/{assignmentId}/stops/{collectionRequestId}/complete")
    public Uni<Response> completeStop(
            @PathParam("deliverymanId") String deliverymanId,
            @PathParam("assignmentId") String assignmentId,
            @PathParam("collectionRequestId") String collectionRequestId,
            @HeaderParam("Authorization") String authorization,
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            ConfirmDeliveryStopDTO dto
    ) {
        LOG.info("POST /deliverymen/{}/assignments/{}/stops/{}/complete", deliverymanId, assignmentId, collectionRequestId);
        return deliverymanUseCase.completeAssignedStop(deliverymanId, assignmentId, collectionRequestId,
                        authorization, dto == null ? null : dto.code(), idempotencyKey)
                .onItem().transform(result -> {
                    if (result.reconciliationPending()) {
                        return Response.accepted(new PendingDeliveryStopResponse(
                                        result.requestId(), result.completionId(), true))
                                .header("Cache-Control", "no-store")
                                .build();
                    }
                    return Response.ok(new CompletedDeliveryStopResponse(
                                    result.assignment(), result.requestId(), result.completionId()))
                            .header("Cache-Control", "no-store")
                            .build();
                })
                .onFailure(CompletionCodeUnauthenticatedException.class).recoverWithItem(ex ->
                        completionError(Response.Status.UNAUTHORIZED, "UNAUTHENTICATED", ex.getMessage()))
                .onFailure(CompletionCodeForbiddenException.class).recoverWithItem(ex ->
                        completionError(Response.Status.FORBIDDEN, "FORBIDDEN", ex.getMessage()))
                .onFailure(CompletionCodeConflictException.class).recoverWithItem(ex -> {
                    CompletionCodeConflictException conflict = (CompletionCodeConflictException) ex;
                    return completionError(Response.Status.CONFLICT, conflict.code(), conflict.getMessage());
                })
                .onFailure(CompletionCodeValidationException.class).recoverWithItem(ex -> {
                    CompletionCodeValidationException validation = (CompletionCodeValidationException) ex;
                    Response.ResponseBuilder response = Response.status(validation.status())
                            .entity(new CompletionErrorResponse(new CompletionError(
                                    validation.code(), validation.getMessage(), validation.retryAfterSeconds())));
                    if (validation.retryAfterSeconds() != null) {
                        response.header("Retry-After", validation.retryAfterSeconds());
                    }
                    return response.build();
                })
                .onFailure(CollectionRequestNotFoundException.class).recoverWithItem(ex ->
                        completionError(Response.Status.NOT_FOUND, "COLLECTION_NOT_FOUND", ex.getMessage()))
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex ->
                        completionError(Response.Status.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage()))
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error completing delivery stop {}", collectionRequestId, ex);
                    return completionError(Response.Status.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                            "Completion service is temporarily unavailable");
                });
    }

    private Response completionError(Response.Status status, String code, String message) {
        return Response.status(status)
                .entity(new CompletionErrorResponse(new CompletionError(code, message, null)))
                .build();
    }

    public record ConfirmDeliveryStopDTO(String code) { }
    public record CompletedDeliveryStopResponse(DeliveryRouteAssignment assignment, String requestId, String completionId) { }
    public record PendingDeliveryStopResponse(String requestId, String completionId, boolean reconciliationPending) { }
    public record CompletionErrorResponse(CompletionError error) { }
    public record CompletionError(String code, String message, Integer retryAfterSeconds) { }

    @Operation(summary = "Mark a route stop as failed")
    @POST
    @Path("/deliverymen/{deliverymanId}/assignments/{assignmentId}/stops/{collectionRequestId}/fail")
    public Uni<Response> failStop(
            @PathParam("deliverymanId") String deliverymanId,
            @PathParam("assignmentId") String assignmentId,
            @PathParam("collectionRequestId") String collectionRequestId,
            FailStopCommand command
    ) {
        LOG.info("POST /deliverymen/{}/assignments/{}/stops/{}/fail", deliverymanId, assignmentId, collectionRequestId);
        return deliverymanUseCase.failAssignedStop(deliverymanId, assignmentId, collectionRequestId, command)
                .onItem().transform(result -> Response.ok(result).build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex -> Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure(IllegalStateException.class).recoverWithItem(ex -> Response.status(Response.Status.CONFLICT).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error failing delivery stop", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    @Operation(summary = "Finish a route assignment")
    @POST
    @Path("/deliverymen/{deliverymanId}/assignments/{assignmentId}/finish")
    public Uni<Response> finishAssignment(
            @PathParam("deliverymanId") String deliverymanId,
            @PathParam("assignmentId") String assignmentId
    ) {
        LOG.info("POST /deliverymen/{}/assignments/{}/finish", deliverymanId, assignmentId);
        return deliverymanUseCase.finishAssignment(deliverymanId, assignmentId)
                .onItem().transform(result -> Response.ok(result).build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex -> Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure(IllegalStateException.class).recoverWithItem(ex -> Response.status(Response.Status.CONFLICT).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error finishing delivery assignment", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    public static class AssignRouteDTO {
        public String deliverymanId;
        public Integer vehicleIndex;
    }
}
