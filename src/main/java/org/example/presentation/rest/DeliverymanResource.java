package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.application.deliveryman.DeliverymanModels.AssignDeliveryRouteCommand;
import org.example.application.deliveryman.DeliverymanModels.SyncDeliverymanCommand;
import org.example.application.usecase.DeliverymanUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DeliverymanResource {
    private static final Logger LOG = LoggerFactory.getLogger(DeliverymanResource.class);

    @Inject
    DeliverymanUseCase deliverymanUseCase;

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

    @POST
    @Path("/deliverymen/{deliverymanId}/assignments/{assignmentId}/stops/{collectionRequestId}/complete")
    public Uni<Response> completeStop(
            @PathParam("deliverymanId") String deliverymanId,
            @PathParam("assignmentId") String assignmentId,
            @PathParam("collectionRequestId") String collectionRequestId
    ) {
        LOG.info("POST /deliverymen/{}/assignments/{}/stops/{}/complete", deliverymanId, assignmentId, collectionRequestId);
        return deliverymanUseCase.completeAssignedStop(deliverymanId, assignmentId, collectionRequestId)
                .onItem().transform(result -> Response.ok(result).build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex -> Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure(IllegalStateException.class).recoverWithItem(ex -> Response.status(Response.Status.CONFLICT).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error completing delivery stop", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    public static class AssignRouteDTO {
        public String deliverymanId;
        public Integer vehicleIndex;
    }
}
