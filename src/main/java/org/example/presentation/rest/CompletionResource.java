package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.application.usecase.CollectionRequestNotFoundException;
import org.example.application.usecase.CompletionCodeConflictException;
import org.example.application.usecase.CompletionCodeForbiddenException;
import org.example.application.usecase.CompletionCodeUnauthenticatedException;
import org.example.application.usecase.CompletionCodeValidationException;
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
    public Response confirmGeneratorCompletion(@PathParam("requestId") String requestId) {
        LOG.info("POST /requests/{}/confirm-generator", requestId);
        return error(Response.Status.CONFLICT, "COMPLETION_CODE_REQUIRED",
                "A valid completion code is required to finish this collection");
    }

    @POST
    @Path("/{requestId}/confirm-collector")
    public Uni<Response> confirmCollectorCompletion(
            @PathParam("requestId") String requestId,
            @HeaderParam("Authorization") String authorization,
            @HeaderParam("Idempotency-Key") String idempotencyKey,
            ConfirmCollectorCompletionDTO dto) {
        LOG.info("POST /requests/{}/confirm-collector", requestId);

        String code = dto == null ? null : dto.code();
        return completionUseCase.confirmCollectorWithCode(requestId, authorization, code, idempotencyKey)
                .onItem().transform(result -> Response.ok(new CompletionResponse(
                                result.requestId(), result.status(), result.completionId()))
                        .header("Cache-Control", "no-store")
                        .build())
                .onFailure(CompletionCodeUnauthenticatedException.class).recoverWithItem(ex ->
                        error(Response.Status.UNAUTHORIZED, "UNAUTHENTICATED", ex.getMessage()))
                .onFailure(CompletionCodeForbiddenException.class).recoverWithItem(ex ->
                        error(Response.Status.FORBIDDEN, "FORBIDDEN", ex.getMessage()))
                .onFailure(CompletionCodeConflictException.class).recoverWithItem(ex -> {
                    CompletionCodeConflictException conflict = (CompletionCodeConflictException) ex;
                    return error(Response.Status.CONFLICT, conflict.code(), conflict.getMessage());
                })
                .onFailure(CompletionCodeValidationException.class).recoverWithItem(ex -> {
                    CompletionCodeValidationException validation = (CompletionCodeValidationException) ex;
                    Response.ResponseBuilder response = Response.status(validation.status())
                            .entity(new CompletionErrorResponse(new ErrorDetail(
                                    validation.code(), validation.getMessage(), validation.retryAfterSeconds())));
                    if (validation.retryAfterSeconds() != null) {
                        response.header("Retry-After", validation.retryAfterSeconds());
                    }
                    return response.build();
                })
                .onFailure(CollectionRequestNotFoundException.class).recoverWithItem(ex ->
                        error(Response.Status.NOT_FOUND, "COLLECTION_NOT_FOUND", ex.getMessage()))
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex ->
                        error(Response.Status.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage()))
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Completion service unavailable for request {}", requestId, ex);
                    return error(Response.Status.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                            "Completion service is temporarily unavailable");
                });
    }

    private Response error(Response.Status status, String code, String message) {
        return Response.status(status)
                .entity(new CompletionErrorResponse(new ErrorDetail(code, message, null)))
                .build();
    }

    public record ConfirmCollectorCompletionDTO(String code) {
    }

    public record CompletionResponse(String requestId, String status, String completionId) {
    }

    public record CompletionErrorResponse(ErrorDetail error) {
    }

    public record ErrorDetail(String code, String message, Integer retryAfterSeconds) {
    }
}

