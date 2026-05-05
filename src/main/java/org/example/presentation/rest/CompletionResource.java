package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.application.usecase.CompletionUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/requests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CompletionResource {

    private static final Logger LOG = LoggerFactory.getLogger(CompletionResource.class);

    @Inject
    CompletionUseCase completionUseCase;

    @POST
    @Path("/{requestId}/confirm-generator")
    public Uni<Response> confirmGeneratorCompletion(@PathParam("requestId") String requestId) {
        LOG.info("POST /requests/{}/confirm-generator", requestId);

        return completionUseCase.confirmGeneratorCompletion(requestId)
                .onItem().transform(it -> Response.ok().build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error confirming generator completion", ex);
                    return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
                });
    }

    @POST
    @Path("/{requestId}/confirm-collector")
    public Uni<Response> confirmCollectorCompletion(@PathParam("requestId") String requestId) {
        LOG.info("POST /requests/{}/confirm-collector", requestId);

        return completionUseCase.confirmCollectorCompletion(requestId)
                .onItem().transform(it -> Response.ok().build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error confirming collector completion", ex);
                    return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
                });
    }
}

