package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.usecase.SearchCollectionsUseCase.CollectionSearchQuery;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.CompletionCodeState;
import org.example.domain.entity.CompletionEvidence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Alternative
@Priority(1)
@ApplicationScoped
public class CompletionRequestPortFixture implements CollectionRequestPort {
    private final Map<String, CollectionRequest> requests = new ConcurrentHashMap<>();

    public void seed(CollectionRequest request) {
        requests.put(request.getId(), request);
    }

    public void clear() {
        requests.clear();
    }

    @Override public Uni<Void> save(CollectionRequest request) { return Uni.createFrom().voidItem(); }
    @Override public Uni<Void> update(CollectionRequest request) { return Uni.createFrom().voidItem(); }
    @Override public Uni<CollectionRequest> findById(String id) { return Uni.createFrom().item(requests.get(id)); }
    @Override public Uni<List<CollectionRequest>> findByGeneratorId(String id) { return Uni.createFrom().item(List.of()); }
    @Override public Uni<List<CollectionRequest>> findByStatus(String status) { return Uni.createFrom().item(List.of()); }
    @Override public Uni<List<CollectionRequest>> search(CollectionSearchQuery query) { return Uni.createFrom().item(List.of()); }
    @Override public Uni<List<CollectionRequest>> findByIds(List<String> ids) { return Uni.createFrom().item(List.of()); }
    @Override public Uni<List<CollectionRequest>> findInProgress(int limit) { return Uni.createFrom().item(List.of()); }
    @Override public Uni<List<CollectionRequest>> findBySelectedCollectorId(String id) { return Uni.createFrom().item(List.of()); }
    @Override public Uni<Boolean> issueCompletionCodeIfAbsent(String id, CompletionCodeState code) {
        CollectionRequest request = requests.get(id);
        if (request == null || request.getCompletionCode() != null || !isActive(request)) return Uni.createFrom().item(false);
        request.setCompletionCode(code);
        return Uni.createFrom().item(true);
    }
    @Override public Uni<Boolean> incrementCompletionCodeAttempts(String id, int version, LocalDateTime now,
                                                                    LocalDateTime blockedUntil, int maxAttempts) {
        CollectionRequest request = requests.get(id);
        CompletionCodeState current = request == null ? null : request.getCompletionCode();
        if (current == null || current.version() != version || current.isConsumed() || current.isBlockedAt(now)) {
            return Uni.createFrom().item(false);
        }
        int attempts = current.failedAttempts() + 1;
        request.setCompletionCode(new CompletionCodeState(current.version(), current.ciphertext(), current.nonce(),
                current.keyId(), current.verifier(), current.issuedAt(), attempts,
                attempts >= maxAttempts ? blockedUntil : null, current.consumedAt()));
        return Uni.createFrom().item(true);
    }
    @Override public Uni<Boolean> completeWithCode(String id, int version, CompletionEvidence evidence) {
        CollectionRequest request = requests.get(id);
        CompletionCodeState current = request == null ? null : request.getCompletionCode();
        if (current == null || current.version() != version || current.isConsumed() || !isActive(request)) {
            return Uni.createFrom().item(false);
        }
        request.setCompletionCode(new CompletionCodeState(current.version(), current.ciphertext(), current.nonce(),
                current.keyId(), current.verifier(), current.issuedAt(), current.failedAttempts(),
                current.blockedUntil(), evidence.validatedAt()));
        request.setCollectorConfirmed(true);
        request.setCompletionEvidence(new CompletionEvidence(
                evidence.completionId(), evidence.actorUserId(), evidence.actorRole(), evidence.actorProfileId(),
                evidence.validatedAt(), evidence.codeVersion(), evidence.idempotencyKey(), evidence.payloadDigest(),
                List.of("ROUTE_PROGRESS", "ROUTE_CLOSE", "COLLECTION_EVENT", "NOTIFICATION")));
        request.setStatus(CollectionRequest.Status.COMPLETED);
        return Uni.createFrom().item(true);
    }

    @Override public Uni<List<CollectionRequest>> findPendingCompletionEffects(int limit) {
        return Uni.createFrom().item(requests.values().stream()
                .filter(request -> request.getCompletionEvidence() != null &&
                        !request.getCompletionEvidence().pendingEffects().isEmpty())
                .limit(Math.max(limit, 0)).toList());
    }

    @Override public Uni<Boolean> markCompletionEffectProcessed(String id, String completionId, String effect) {
        CollectionRequest request = requests.get(id);
        if (request == null || request.getCompletionEvidence() == null ||
                !request.getCompletionEvidence().completionId().equals(completionId) ||
                !request.getCompletionEvidence().pendingEffects().contains(effect)) {
            return Uni.createFrom().item(false);
        }
        CompletionEvidence current = request.getCompletionEvidence();
        request.setCompletionEvidence(new CompletionEvidence(current.completionId(), current.actorUserId(),
                current.actorRole(), current.actorProfileId(), current.validatedAt(), current.codeVersion(),
                current.idempotencyKey(), current.payloadDigest(), current.pendingEffects().stream()
                .filter(pending -> !pending.equals(effect)).toList()));
        return Uni.createFrom().item(true);
    }

    private boolean isActive(CollectionRequest request) {
        return CollectionRequest.Status.IN_PROGRESS.equals(request.getStatus()) ||
                CollectionRequest.Status.ON_THE_WAY.equals(request.getStatus());
    }
}
