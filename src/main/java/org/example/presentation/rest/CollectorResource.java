package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.application.usecase.CollectorResponseUseCase;
import org.example.application.usecase.CollectorSelectionUseCase;
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

        public SelectCollectorDTO() {}

        public String getCollectorId() { return collectorId; }
        public void setCollectorId(String collectorId) { this.collectorId = collectorId; }
    }
}

