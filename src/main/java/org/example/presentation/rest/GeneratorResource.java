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
import org.example.application.usecase.CollectionRequestUseCase;
import org.example.domain.entity.Collector;
import org.example.domain.entity.CollectionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Path("/generators")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GeneratorResource {

    private static final Logger LOG = LoggerFactory.getLogger(GeneratorResource.class);

    @Inject
    CollectionRequestUseCase collectionRequestUseCase;

    @Inject
    CancelCollectionRequestUseCase cancelCollectionRequestUseCase;

    @POST
    @Path("/requests")
    public Uni<Response> createRequest(CreateRequestDTO dto) {
        LOG.info("POST /generators/requests - Creating collection request");

        return collectionRequestUseCase.createRequest(
                        dto.getGeneratorId(),
                        dto.getAddressId(),
                        dto.getMaterialIds(),
                        dto.getWeight()
                )
                .onItem().transform(request -> Response.status(Response.Status.CREATED).entity(request).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error creating request", ex);
                    return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
                });
    }

    @GET
    @Path("/requests/{requestId}/collectors")
    public Uni<Response> getNearbyCollectors(@PathParam("requestId") String requestId) {
        LOG.info("GET /generators/requests/{}/collectors", requestId);

        return collectionRequestUseCase.findNearbyCollectors(requestId)
                .onItem().transform(collectors -> Response.ok(collectors).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error finding collectors", ex);
                    return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
                });
    }

    @POST
    @Path("/requests/{requestId}/cancel")
    public Uni<Response> cancelRequest(
            @PathParam("requestId") String requestId,
            CancelRequestDTO dto) {
        LOG.info("POST /generators/requests/{}/cancel", requestId);

        String generatorId = dto == null ? null : dto.getGeneratorId();
        return cancelCollectionRequestUseCase.cancelByGenerator(requestId, generatorId)
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
                    LOG.error("Error cancelling request by generator", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    // DTOs
    public static class CreateRequestDTO {
        private String generatorId;
        private String addressId;
        private List<String> materialIds;
        private Double weight;

        public CreateRequestDTO() {
        }

        public String getGeneratorId() {
            return generatorId;
        }

        public void setGeneratorId(String generatorId) {
            this.generatorId = generatorId;
        }

        public String getAddressId() {
            return addressId;
        }

        public void setAddressId(String addressId) {
            this.addressId = addressId;
        }

        public List<String> getMaterialIds() {
            return materialIds;
        }

        public void setMaterialIds(List<String> materialIds) {
            this.materialIds = materialIds;
        }

        public Double getWeight() {
            return weight;
        }

        public void setWeight(Double weight) {
            this.weight = weight;
        }
    }

    public static class CancelRequestDTO {
        private String generatorId;

        public CancelRequestDTO() {
        }

        public String getGeneratorId() {
            return generatorId;
        }

        public void setGeneratorId(String generatorId) {
            this.generatorId = generatorId;
        }
    }
}

