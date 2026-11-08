package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.AuthenticatedActorPort;
import org.example.application.port.out.CompletionCodeCryptoPort;
import org.example.application.port.out.EventPort;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.CompletionCodeState;
import org.example.domain.entity.CompletionEvidence;
import org.example.infrastructure.event.CollectionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Singleton
public class CompletionUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CompletionUseCase.class);

    @Inject
    CollectionRequestPort collectionRequestPort;

    @Inject
    EventPort eventPort;

    @Inject
    CloseSavedRoutesUseCase closeSavedRoutesUseCase;

    @Inject
    CollectionStatusNotificationUseCase notificationUseCase;

    @Inject
    AuthenticatedActorPort authenticatedActorPort;

    @Inject
    CompletionCodeCryptoPort cryptoPort;

    @Inject
    CompletionEffectsUseCase completionEffectsUseCase;

    public Uni<CompletionResult> confirmWithCode(
            String requestId,
            String bearerToken,
            String submittedCode,
            String idempotencyKey
    ) {
        return confirmWithCode(requestId, bearerToken, submittedCode, idempotencyKey, null);
    }

    public Uni<CompletionResult> confirmCollectorWithCode(
            String requestId,
            String bearerToken,
            String submittedCode,
            String idempotencyKey
    ) {
        return confirmWithCode(requestId, bearerToken, submittedCode, idempotencyKey, "WASTE_COLLECTOR");
    }

    public Uni<CompletionResult> confirmDeliverymanWithCode(
            String requestId,
            String bearerToken,
            String submittedCode,
            String idempotencyKey
    ) {
        return confirmWithCode(requestId, bearerToken, submittedCode, idempotencyKey, "DELIVERYMAN");
    }

    private Uni<CompletionResult> confirmWithCode(
            String requestId,
            String bearerToken,
            String submittedCode,
            String idempotencyKey,
            String requiredRole
    ) {
        if (requestId == null || requestId.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("requestId is required"));
        }
        if (!isUuid(idempotencyKey)) {
            return Uni.createFrom().failure(new IllegalArgumentException("Idempotency-Key must be a UUID"));
        }
        java.util.Optional<String> normalized = CompletionCodeState.normalizeInput(submittedCode);
        if (normalized.isEmpty()) {
            return Uni.createFrom().failure(new CompletionCodeValidationException(
                    "CODE_FORMAT_INVALID", "Code must contain exactly six ASCII digits", 400, null));
        }
        String normalizedCode = normalized.orElseThrow();
        if (bearerToken == null || bearerToken.isBlank()) {
            return Uni.createFrom().failure(new CompletionCodeUnauthenticatedException());
        }
        return authenticatedActorPort.resolve(bearerToken)
                .onFailure(org.example.infrastructure.identity.RegisterAuthenticatedActorAdapter.ActorUnauthenticatedException.class)
                .transform(ignored -> new CompletionCodeUnauthenticatedException())
                .flatMap(actor -> collectionRequestPort.findById(requestId)
                        .onItem().ifNull().failWith(() -> new CollectionRequestNotFoundException("Request not found: " + requestId))
                        .flatMap(request -> {
                            String profileId = authorizeCompletionActor(actor, request, requiredRole);
                            CompletionCodeState codeState = request.getCompletionCode();
                            if (CollectionRequest.Status.COMPLETED.equals(request.getStatus()) &&
                                    request.getCompletionEvidence() != null) {
                                return idempotentResult(request, actor, profileId, normalizedCode, idempotencyKey);
                            }
                            if (!isActive(request.getStatus())) {
                                return Uni.createFrom().failure(new CompletionCodeConflictException(
                                        "COLLECTION_NOT_ACTIVE", "Collection request is not active"));
                            }
                            if (codeState == null) {
                                return Uni.createFrom().failure(new CompletionCodeConflictException(
                                        "CODE_NOT_ISSUED", "Completion code has not been issued"));
                            }
                            LocalDateTime now = LocalDateTime.now();
                            if (codeState.isConsumed()) {
                                return Uni.createFrom().failure(new CompletionCodeConflictException(
                                        "CODE_CONSUMED", "Completion code has already been consumed"));
                            }
                            if (codeState.isBlockedAt(now)) {
                                return Uni.createFrom().failure(blockedException(codeState.blockedUntil(), now));
                            }
                            if (!cryptoPort.matchesVerifier(requestId, codeState.version(), normalizedCode, codeState.verifier())) {
                                return recordInvalidAttempt(request, codeState, now);
                            }
                            String payloadDigest = cryptoPort.createVerifier(requestId, codeState.version(), normalizedCode);
                            CompletionEvidence evidence = new CompletionEvidence(
                                    UUID.randomUUID().toString(),
                                    actor.userId(),
                                    actor.role(),
                                    profileId,
                                    now,
                                    codeState.version(),
                                    idempotencyKey,
                                    payloadDigest);
                            return collectionRequestPort.completeWithCode(requestId, codeState.version(), evidence)
                                    .flatMap(completed -> completed
                                            ? reconcileAndReturn(requestId, new CompletionResult(
                                                    requestId, "COMPLETED", evidence.completionId()))
                                            : retryAfterLostCompletion(requestId, actor, profileId, normalizedCode, idempotencyKey));
                        }));
    }

    private Uni<CompletionResult> recordInvalidAttempt(
            CollectionRequest request,
            CompletionCodeState codeState,
            LocalDateTime now
    ) {
        return collectionRequestPort.incrementCompletionCodeAttempts(
                        request.getId(), codeState.version(), now, now.plusMinutes(15), 5)
                .flatMap(recorded -> {
                    if (!recorded) {
                        return reloadAfterInvalidAttempt(request.getId(), now, false);
                    }
                    return reloadAfterInvalidAttempt(request.getId(), now, true);
                });
    }

    private Uni<CompletionResult> reloadAfterInvalidAttempt(String requestId, LocalDateTime now, boolean attemptRecorded) {
        return collectionRequestPort.findById(requestId)
                .flatMap(request -> {
                    if (request != null && request.getCompletionCode() != null &&
                            request.getCompletionCode().isBlockedAt(now)) {
                        return Uni.createFrom().failure(blockedException(request.getCompletionCode().blockedUntil(), now));
                    }
                    if (request != null && request.getCompletionCode() != null && request.getCompletionCode().failedAttempts() >= 5) {
                        return Uni.createFrom().failure(blockedException(now.plusMinutes(15), now));
                    }
                    if (attemptRecorded) {
                        return Uni.createFrom().failure(new CompletionCodeValidationException(
                                "CODE_INVALID", "Completion code is invalid", 422, null));
                    }
                    return Uni.createFrom().failure(new CompletionCodeConflictException(
                            "COMPLETION_CHANGED", "Collection changed while validating the code"));
                });
    }

    private Uni<CompletionResult> idempotentResult(
            CollectionRequest request,
            AuthenticatedActorPort.AuthenticatedActor actor,
            String profileId,
            String normalizedCode,
            String idempotencyKey
    ) {
        CompletionEvidence evidence = request.getCompletionEvidence();
        String digest = request.getCompletionCode() == null ? null :
                cryptoPort.createVerifier(request.getId(), evidence.codeVersion(), normalizedCode);
        if (Objects.equals(evidence.idempotencyKey(), idempotencyKey) &&
                Objects.equals(evidence.actorUserId(), actor.userId()) &&
                Objects.equals(evidence.actorRole(), actor.role()) &&
                Objects.equals(evidence.actorProfileId(), profileId) &&
                Objects.equals(evidence.payloadDigest(), digest)) {
            return reconcileAndReturn(request.getId(), new CompletionResult(
                    request.getId(), "COMPLETED", evidence.completionId()));
        }
        if (Objects.equals(evidence.idempotencyKey(), idempotencyKey)) {
            return Uni.createFrom().failure(new CompletionCodeConflictException(
                    "IDEMPOTENCY_KEY_CONFLICT", "Idempotency-Key was already used with another payload"));
        }
        return Uni.createFrom().failure(new CompletionCodeConflictException(
                "COLLECTION_ALREADY_COMPLETED", "Collection request is already completed"));
    }

    private Uni<CompletionResult> reconcileAndReturn(String requestId, CompletionResult result) {
        return completionEffectsUseCase.processCompletion(requestId, result.completionId())
                .onFailure().invoke(ex -> LOG.error("Completion persisted; effects remain pending requestId={} completionId={}",
                        requestId, result.completionId(), ex))
                .onFailure().recoverWithNull()
                .replaceWith(result);
    }

    private Uni<CompletionResult> retryAfterLostCompletion(
            String requestId,
            AuthenticatedActorPort.AuthenticatedActor actor,
            String profileId,
            String normalizedCode,
            String idempotencyKey
    ) {
        return collectionRequestPort.findById(requestId)
                .flatMap(request -> {
                    if (request != null && CollectionRequest.Status.COMPLETED.equals(request.getStatus()) &&
                            request.getCompletionEvidence() != null) {
                        return idempotentResult(request, actor, profileId, normalizedCode, idempotencyKey);
                    }
                    return Uni.createFrom().failure(new CompletionCodeConflictException(
                            "COLLECTION_CHANGED", "Collection request changed before confirmation was committed"));
                });
    }

    private CompletionCodeValidationException blockedException(LocalDateTime blockedUntil, LocalDateTime now) {
        long retryAfter = Math.max(1, Duration.between(now, blockedUntil).getSeconds());
        return new CompletionCodeValidationException(
                "TOO_MANY_ATTEMPTS", "Too many invalid completion code attempts", 429, (int) Math.min(retryAfter, Integer.MAX_VALUE));
    }

    private String authorizeCompletionActor(
            AuthenticatedActorPort.AuthenticatedActor actor,
            CollectionRequest request,
            String requiredRole
    ) {
        if (actor != null && "WASTE_COLLECTOR".equals(actor.role()) &&
                (requiredRole == null || "WASTE_COLLECTOR".equals(requiredRole)) &&
                Objects.equals(actor.collectorId(), request.getSelectedCollectorId())) {
            return actor.collectorId();
        }
        if (actor != null && "DELIVERYMAN".equals(actor.role()) &&
                (requiredRole == null || "DELIVERYMAN".equals(requiredRole)) &&
                Objects.equals(actor.ownerCollectorId(), request.getSelectedCollectorId()) &&
                actor.deliverymanId() != null && !actor.deliverymanId().isBlank()) {
            return actor.deliverymanId();
        }
        throw new CompletionCodeForbiddenException();
    }

    private boolean isActive(CollectionRequest.Status status) {
        return CollectionRequest.Status.IN_PROGRESS.equals(status) ||
                CollectionRequest.Status.ON_THE_WAY.equals(status);
    }

    private boolean isUuid(String value) {
        return value != null && value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    public record CompletionResult(String requestId, String status, String completionId) {
    }

    public Uni<Void> confirmGeneratorCompletion(String requestId) {
        LOG.info("Generator confirming completion for request: {}", requestId);

        return collectionRequestPort.findById(requestId)
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Request not found: " + requestId))
                .flatMap(request -> {
                    if (!CollectionRequest.Status.IN_PROGRESS.equals(request.getStatus()) && !CollectionRequest.Status.ON_THE_WAY.equals(request.getStatus())) {
                        return Uni.createFrom().failure(new IllegalStateException("Request is not IN_PROGRESS or ON_THE_WAY"));
                    }
                    request.setGeneratorConfirmed(true);
                    return collectionRequestPort.update(request)
                            .flatMap(it -> tryMarkCompleted(request))
                            .invoke(() -> LOG.info("Generator confirmed completion for request: {}", requestId));
                });
    }

    public Uni<Void> confirmCollectorCompletion(String requestId) {
        LOG.info("Collector confirming completion for request: {}", requestId);

        return collectionRequestPort.findById(requestId)
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Request not found: " + requestId))
                .flatMap(request -> {
                    if (!CollectionRequest.Status.IN_PROGRESS.equals(request.getStatus()) && !CollectionRequest.Status.ON_THE_WAY.equals(request.getStatus())) {
                        return Uni.createFrom().failure(new IllegalStateException("Request is not IN_PROGRESS or ON_THE_WAY"));
                    }
                    request.setCollectorConfirmed(true);
                    return collectionRequestPort.update(request)
                            .flatMap(it -> tryMarkCompleted(request))
                            .invoke(() -> LOG.info("Collector confirmed completion for request: {}", requestId));
                });
    }

    private Uni<Void> tryMarkCompleted(CollectionRequest request) {
        if (request.canMarkCompleted()) {
            request.setStatus(CollectionRequest.Status.COMPLETED);
            return collectionRequestPort.update(request)
                    .flatMap(it -> publishCompletionEvent(request))
                    .flatMap(it -> closeSavedRoutesUseCase.closeRoutesContaining(request.getId()))
                    .flatMap(it -> notificationUseCase.notifyGenerator(request, "COLLECTION_COMPLETED"));
        }
        return Uni.createFrom().nullItem().replaceWithVoid();
    }

    private Uni<Void> publishCompletionEvent(CollectionRequest request) {
        CollectionEvent event = new CollectionEvent();
        event.setEventType("COLLECTION_COMPLETED");
        event.setRequestId(request.getId());
        event.setGeneratorId(request.getGeneratorId());
        event.setCollectorId(request.getSelectedCollectorId());
        event.setStatus(request.getStatus().toString());
        event.setTimestamp(Instant.now().toString());

        return eventPort.publishCollectionEvent(event);
    }
}
