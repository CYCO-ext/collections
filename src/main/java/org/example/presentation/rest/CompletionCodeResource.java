package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.application.usecase.CollectionRequestNotFoundException;
import org.example.application.usecase.CompletionCodeConflictException;
import org.example.application.usecase.CompletionCodeForbiddenException;
import org.example.application.usecase.CompletionCodeUnauthenticatedException;
import org.example.application.usecase.CompletionCodeUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/requests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CompletionCodeResource {
    private static final Logger LOG = LoggerFactory.getLogger(CompletionCodeResource.class);

    @Inject
    CompletionCodeUseCase completionCodeUseCase;

    @POST
    @Path("/{requestId}/completion-code")
    public Uni<Response> issueOrRead(
            @PathParam("requestId") String requestId,
            @HeaderParam("Authorization") String authorization) {
        LOG.info("POST /requests/{}/completion-code", requestId);
        return completionCodeUseCase.issueOrRead(requestId, authorization)
                .onItem().transform(result -> Response.ok(new CompletionCodeResponse(result.requestId(), result.code()))
                        .header("Cache-Control", "no-store")
                        .build())
                .onFailure(CompletionCodeUnauthenticatedException.class).recoverWithItem(ex -> error(
                        Response.Status.UNAUTHORIZED, "UNAUTHENTICATED", ex.getMessage()))
                .onFailure(CompletionCodeForbiddenException.class).recoverWithItem(ex -> error(
                        Response.Status.FORBIDDEN, "FORBIDDEN", ex.getMessage()))
                .onFailure(CompletionCodeConflictException.class).recoverWithItem(ex -> {
                    CompletionCodeConflictException conflict = (CompletionCodeConflictException) ex;
                    return error(Response.Status.CONFLICT, conflict.code(), conflict.getMessage());
                })
                .onFailure(CollectionRequestNotFoundException.class).recoverWithItem(ex -> error(
                        Response.Status.NOT_FOUND, "COLLECTION_NOT_FOUND", ex.getMessage()))
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex -> error(
                        Response.Status.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage()))
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Completion code service unavailable for request {}", requestId, ex);
                    return error(Response.Status.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                            "Completion code service is temporarily unavailable");
                });
    }

    private Response error(Response.Status status, String code, String message) {
        return Response.status(status).entity(new CompletionCodeErrorResponse(new ErrorDetail(code, message))).build();
    }

    public record CompletionCodeResponse(String requestId, String code) {
    }

    public record CompletionCodeErrorResponse(ErrorDetail error) {
    }

    public record ErrorDetail(String code, String message) {
    }
}
