package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.example.application.usecase.CollectionRequestNotFoundException;
import org.example.application.usecase.CompletionCodeConflictException;
import org.example.application.usecase.CompletionCodeForbiddenException;
import org.example.application.usecase.CompletionCodeUnauthenticatedException;
import org.example.application.usecase.CompletionCodeUseCase;

@Alternative
@Priority(2)
@ApplicationScoped
public class CompletionCodeUseCaseFixture extends CompletionCodeUseCase {
    @Override
    public Uni<IssuedCompletionCode> issueOrRead(String requestId, String authorization) {
        if (authorization == null || "Bearer invalid".equals(authorization)) {
            return Uni.createFrom().failure(new CompletionCodeUnauthenticatedException());
        }
        if ("Bearer unavailable".equals(authorization)) {
            return Uni.createFrom().failure(new IllegalStateException("identity service unavailable"));
        }
        if ("Bearer forbidden".equals(authorization)) {
            return Uni.createFrom().failure(new CompletionCodeForbiddenException());
        }
        if ("Bearer inactive".equals(authorization)) {
            return Uni.createFrom().failure(new CompletionCodeConflictException(
                    "COLLECTION_NOT_ACTIVE", "Collection request is not active"));
        }
        if ("Bearer missing".equals(authorization)) {
            return Uni.createFrom().failure(new CollectionRequestNotFoundException(requestId));
        }
        return Uni.createFrom().item(new IssuedCompletionCode(requestId, "001234"));
    }
}
