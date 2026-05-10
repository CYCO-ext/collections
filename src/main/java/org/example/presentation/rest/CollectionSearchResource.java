package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.application.usecase.CollectionNotFoundException;
import org.example.application.usecase.GetCollectionByIdUseCase;
import org.example.application.usecase.SearchCollectionsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/collections")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CollectionSearchResource {
    private static final Logger LOG = LoggerFactory.getLogger(CollectionSearchResource.class);

    @Inject
    SearchCollectionsUseCase searchCollectionsUseCase;

    @Inject
    GetCollectionByIdUseCase getCollectionByIdUseCase;

    @GET
    @Path("/search")
    public Uni<Response> search(
            @QueryParam("status") String status,
            @QueryParam("collectorId") String collectorId,
            @QueryParam("generatorId") String generatorId) {
        LOG.info("GET /collections/search status={} collectorId={} generatorId={}", status, collectorId, generatorId);
        return searchCollectionsUseCase.search(status, collectorId, generatorId)
                .onItem().transform(results -> Response.ok(results).build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error searching collections", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    @GET
    @Path("/{id}")
    public Uni<Response> getById(@PathParam("id") String id) {
        LOG.info("GET /collections/{}", id);
        return getCollectionByIdUseCase.getById(id)
                .onItem().transform(result -> Response.ok(result).build())
                .onFailure(CollectionNotFoundException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.NOT_FOUND).entity(ex.getMessage()).build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error getting collection by id", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }
}
