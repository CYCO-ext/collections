package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.application.usecase.CancelCollectionRequestUseCase;
import org.example.application.usecase.CollectionCancellationConflictException;
import org.example.application.usecase.CollectionCancellationForbiddenException;
import org.example.application.usecase.CollectionRequestNotFoundException;
import org.example.application.usecase.CollectorAddressNotFoundException;
import org.example.application.usecase.CollectorNotFoundException;
import org.example.application.usecase.CollectorResponseUseCase;
import org.example.application.usecase.CollectorSelectionUseCase;
import org.example.application.usecase.GetCollectorAddressUseCase;
import org.example.application.usecase.MarkCollectorOnTheWayUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/collectors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CollectorResource {

    private static final Logger LOG = LoggerFactory.getLogger(CollectorResource.class);

    @Inject
    CollectorSelectionUseCase collectorSelectionUseCase;

    @Inject
    CollectorResponseUseCase collectorResponseUseCase;

    @Inject
    GetCollectorAddressUseCase getCollectorAddressUseCase;

    @Inject
    CancelCollectionRequestUseCase cancelCollectionRequestUseCase;

    @Inject
    MarkCollectorOnTheWayUseCase markCollectorOnTheWayUseCase;

    @GET
    @Path("/{collectorId}/address")
    public Uni<Response> getCollectorAddress(@PathParam("collectorId") String collectorId) {
        LOG.info("GET /collectors/{}/address", collectorId);

        return getCollectorAddressUseCase.getAddress(collectorId)
                .onItem().transform(result -> Response.ok(result).build())
                .onFailure(CollectorNotFoundException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.NOT_FOUND).entity(ex.getMessage()).build())
                .onFailure(CollectorAddressNotFoundException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.NOT_FOUND).entity(ex.getMessage()).build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error getting collector address", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    @POST
    @Path("/requests/{requestId}/select")
    public Uni<Response> selectCollector(
            @PathParam("requestId") String requestId,
            SelectCollectorDTO dto) {
        LOG.info("POST /collectors/requests/{}/select", requestId);

        return collectorSelectionUseCase.selectCollector(requestId, dto.getCollectorId())
                .onItem().transform(it -> Response.ok().build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error selecting collector", ex);
                    return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
                });
    }

    @POST
    @Path("/requests/{requestId}/accept")
    public Uni<Response> acceptRequest(@PathParam("requestId") String requestId) {
        LOG.info("POST /collectors/requests/{}/accept", requestId);

        return collectorResponseUseCase.acceptRequest(requestId)
                .onItem().transform(it -> Response.ok().build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error accepting request", ex);
                    return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
                });
    }

    @POST
    @Path("/requests/{requestId}/reject")
    public Uni<Response> rejectRequest(@PathParam("requestId") String requestId) {
        LOG.info("POST /collectors/requests/{}/reject", requestId);

        return collectorResponseUseCase.rejectRequest(requestId)
                .onItem().transform(it -> Response.ok().build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error rejecting request", ex);
                    return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
                });
    }

    @POST
    @Path("/requests/{requestId}/on-the-way")
    public Uni<Response> markOnTheWay(
            @PathParam("requestId") String requestId,
            OnTheWayDTO dto) {
        LOG.info("POST /collectors/requests/{}/on-the-way", requestId);

        String collectorId = dto == null ? null : dto.getCollectorId();
        return markCollectorOnTheWayUseCase.markOnTheWay(requestId, collectorId)
                .onItem().transform(it -> Response.ok().build())
                .onFailure(CollectionRequestNotFoundException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.NOT_FOUND).entity(ex.getMessage()).build())
                .onFailure(CollectionCancellationForbiddenException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.FORBIDDEN).entity(ex.getMessage()).build())
                .onFailure(CollectionCancellationConflictException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.CONFLICT).entity(ex.getMessage()).build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error marking request on the way", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    @POST
    @Path("/requests/{requestId}/cancel")
    public Uni<Response> cancelRequest(
            @PathParam("requestId") String requestId,
            CancelRequestDTO dto) {
        LOG.info("POST /collectors/requests/{}/cancel", requestId);

        String collectorId = dto == null ? null : dto.getCollectorId();
        return cancelCollectionRequestUseCase.cancelByCollector(requestId, collectorId)
                .onItem().transform(it -> Response.ok().build())
                .onFailure(CollectionRequestNotFoundException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.NOT_FOUND).entity(ex.getMessage()).build())
                .onFailure(CollectionCancellationForbiddenException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.FORBIDDEN).entity(ex.getMessage()).build())
                .onFailure(CollectionCancellationConflictException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.CONFLICT).entity(ex.getMessage()).build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error cancelling request by collector", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    // DTOs
    public static class SelectCollectorDTO {
        private String collectorId;

        public SelectCollectorDTO() {
        }

        public String getCollectorId() {
            return collectorId;
        }

        public void setCollectorId(String collectorId) {
            this.collectorId = collectorId;
        }
    }

    public static class OnTheWayDTO {
        private String collectorId;

        public OnTheWayDTO() {
        }

        public String getCollectorId() {
            return collectorId;
        }

        public void setCollectorId(String collectorId) {
            this.collectorId = collectorId;
        }
    }

    public static class CancelRequestDTO {
        private String collectorId;

        public CancelRequestDTO() {
        }

        public String getCollectorId() {
            return collectorId;
        }

        public void setCollectorId(String collectorId) {
            this.collectorId = collectorId;
        }
    }
}
