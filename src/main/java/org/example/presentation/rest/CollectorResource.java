package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.application.usecase.CollectorAddressNotFoundException;
import org.example.application.usecase.CollectorNotFoundException;
import org.example.application.usecase.CollectorResponseUseCase;
import org.example.application.usecase.CollectorSelectionUseCase;
import org.example.application.usecase.GetCollectorAddressUseCase;
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
}
