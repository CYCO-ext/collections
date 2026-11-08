package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.AuthenticatedActorPort;
import org.example.application.port.out.CompletionCodeCryptoPort;
import org.example.application.port.out.CollectionRequestPort;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.CompletionCodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Objects;

@Singleton
public class CompletionCodeUseCase {
    private static final Logger LOG = LoggerFactory.getLogger(CompletionCodeUseCase.class);
    private static final int INITIAL_VERSION = 1;

    @Inject
    CollectionRequestPort collectionRequestPort;

    @Inject
    AuthenticatedActorPort authenticatedActorPort;

    @Inject
    CompletionCodeCryptoPort cryptoPort;

    public Uni<IssuedCompletionCode> issueOrRead(String requestId, String bearerToken) {
        if (requestId == null || requestId.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("requestId is required"));
        }
        if (bearerToken == null || bearerToken.isBlank()) {
            return Uni.createFrom().failure(new CompletionCodeUnauthenticatedException());
        }
        return authenticatedActorPort.resolve(bearerToken)
                .onFailure(org.example.infrastructure.identity.RegisterAuthenticatedActorAdapter.ActorUnauthenticatedException.class)
                .transform(ignored -> new CompletionCodeUnauthenticatedException())
                .flatMap(actor -> collectionRequestPort.findById(requestId)
                        .onItem().ifNull().failWith(() -> new CollectionRequestNotFoundException("Request not found: " + requestId))
                        .flatMap(request -> {
                            validateOwner(actor, request);
                            validateActive(request);
                            CompletionCodeState existing = request.getCompletionCode();
                            if (existing != null) {
                                if (existing.isConsumed()) {
                                    return Uni.createFrom().failure(new CompletionCodeConflictException(
                                            "CODE_CONSUMED", "Completion code has already been consumed"));
                                }
                                return Uni.createFrom().item(new IssuedCompletionCode(
                                        requestId, cryptoPort.decrypt(requestId, toEncryptedCode(existing))));
                            }
                            return issue(requestId);
                        }));
    }

    private Uni<IssuedCompletionCode> issue(String requestId) {
        String code = cryptoPort.generateCode();
        int version = INITIAL_VERSION;
        CompletionCodeCryptoPort.EncryptedCode encrypted = cryptoPort.encrypt(requestId, version, code);
        CompletionCodeState state = new CompletionCodeState(
                version,
                encrypted.ciphertext(),
                encrypted.nonce(),
                encrypted.keyId(),
                cryptoPort.createVerifier(requestId, version, code),
                LocalDateTime.now(),
                0,
                null,
                null);
        LOG.info("Issuing completion code for collection request {}", requestId);
        return collectionRequestPort.issueCompletionCodeIfAbsent(requestId, state)
                .flatMap(inserted -> inserted
                        ? Uni.createFrom().item(new IssuedCompletionCode(requestId, code))
                        : readWinningIssue(requestId));
    }

    private Uni<IssuedCompletionCode> readWinningIssue(String requestId) {
        return collectionRequestPort.findById(requestId)
                .onItem().ifNull().failWith(() -> new CollectionRequestNotFoundException("Request not found: " + requestId))
                .flatMap(request -> {
                    validateActive(request);
                    CompletionCodeState winner = request.getCompletionCode();
                    if (winner == null || winner.isConsumed()) {
                        return Uni.createFrom().failure(new CompletionCodeConflictException(
                                "COMPLETION_CODE_UNAVAILABLE", "Completion code is no longer available"));
                    }
                    return Uni.createFrom().item(new IssuedCompletionCode(
                            requestId, cryptoPort.decrypt(requestId, toEncryptedCode(winner))));
                });
    }

    private CompletionCodeCryptoPort.EncryptedCode toEncryptedCode(CompletionCodeState state) {
        return new CompletionCodeCryptoPort.EncryptedCode(state.version(), state.ciphertext(), state.nonce(), state.keyId());
    }

    private void validateOwner(AuthenticatedActorPort.AuthenticatedActor actor, CollectionRequest request) {
        if (actor == null || !"GENERATOR".equals(actor.role()) ||
                !Objects.equals(actor.generatorId(), request.getGeneratorId())) {
            throw new CompletionCodeForbiddenException();
        }
    }

    private void validateActive(CollectionRequest request) {
        if (!CollectionRequest.Status.IN_PROGRESS.equals(request.getStatus()) &&
                !CollectionRequest.Status.ON_THE_WAY.equals(request.getStatus())) {
            throw new CompletionCodeConflictException("COLLECTION_NOT_ACTIVE", "Collection request is not active");
        }
    }

    public record IssuedCompletionCode(String requestId, String code) {
    }
}
