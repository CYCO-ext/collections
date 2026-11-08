package org.example.infrastructure.repository;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.CompletionCodeState;
import org.example.domain.entity.CompletionEvidence;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
class CollectionRequestRepositoryTest {
    @Inject
    CollectionRequestRepository repository;

    @Test
    void concurrentIssuePersistsExactlyOneCode() {
        String requestId = saveInProgressRequest();
        List<Uni<Boolean>> issuances = java.util.stream.IntStream.range(0, 24)
                .mapToObj(index -> repository.issueCompletionCodeIfAbsent(requestId,
                        code("ciphertext-" + index, 1)))
                .toList();

        List<Boolean> results = Uni.join().all(issuances).andCollectFailures().await().indefinitely();

        assertEquals(1, results.stream().filter(Boolean.TRUE::equals).count());
        CompletionCodeState persisted = repository.findById(requestId).await().indefinitely().getCompletionCode();
        assertTrue(persisted.ciphertext().startsWith("ciphertext-"));
        assertEquals(1, persisted.version());
    }

    @Test
    void fifthInvalidAttemptBlocksAndExpiryBeginsANewAttemptCycle() {
        String requestId = saveInProgressRequest();
        repository.issueCompletionCodeIfAbsent(requestId, code("ciphertext", 1)).await().indefinitely();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime blockedUntil = now.plusMinutes(15);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertTrue(repository.incrementCompletionCodeAttempts(requestId, 1, now, blockedUntil, 5)
                    .await().indefinitely());
        }
        assertFalse(repository.incrementCompletionCodeAttempts(requestId, 1, now, blockedUntil, 5)
                .await().indefinitely());
        CompletionCodeState blocked = repository.findById(requestId).await().indefinitely().getCompletionCode();
        assertEquals(5, blocked.failedAttempts());
        assertEquals(blockedUntil.withNano(0), blocked.blockedUntil().withNano(0));

        LocalDateTime afterExpiry = blockedUntil.plusSeconds(1);
        assertTrue(repository.incrementCompletionCodeAttempts(requestId, 1, afterExpiry,
                afterExpiry.plusMinutes(15), 5).await().indefinitely());
        CompletionCodeState restarted = repository.findById(requestId).await().indefinitely().getCompletionCode();
        assertEquals(1, restarted.failedAttempts());
        assertEquals(null, restarted.blockedUntil());
    }

    @Test
    void concurrentConfirmationConsumesOnceAndPersistsEvidenceAndEffectsAtomically() {
        String requestId = saveInProgressRequest();
        repository.issueCompletionCodeIfAbsent(requestId, code("ciphertext", 1)).await().indefinitely();
        List<Uni<Boolean>> confirmations = java.util.stream.IntStream.range(0, 24)
                .mapToObj(index -> repository.completeWithCode(requestId, 1, evidence("completion-" + index)))
                .toList();

        List<Boolean> results = Uni.join().all(confirmations).andCollectFailures().await().indefinitely();

        assertEquals(1, results.stream().filter(Boolean.TRUE::equals).count());
        CollectionRequest persisted = repository.findById(requestId).await().indefinitely();
        assertEquals(CollectionRequest.Status.COMPLETED, persisted.getStatus());
        assertEquals(Boolean.TRUE, persisted.getCollectorConfirmed());
        assertEquals(1, persisted.getCompletionCode().version());
        assertTrue(persisted.getCompletionCode().isConsumed());
        assertTrue(persisted.getCompletionEvidence().completionId().startsWith("completion-"));
        assertEquals(List.of("ROUTE_PROGRESS", "ROUTE_CLOSE", "COLLECTION_EVENT", "NOTIFICATION"),
                persisted.getCompletionEvidence().pendingEffects());
        assertFalse(repository.issueCompletionCodeIfAbsent(requestId, code("replacement", 2)).await().indefinitely());
    }

    private String saveInProgressRequest() {
        CollectionRequest request = new CollectionRequest("generator-1", "address-1", List.of("paper"), 10.0);
        request.setId(UUID.randomUUID().toString());
        request.setStatus(CollectionRequest.Status.IN_PROGRESS);
        request.setSelectedCollectorId("collector-1");
        repository.save(request).await().indefinitely();
        return request.getId();
    }

    private CompletionCodeState code(String ciphertext, int version) {
        return new CompletionCodeState(version, ciphertext, "nonce", "key-1", "verifier",
                LocalDateTime.now(), 0, null, null);
    }

    private CompletionEvidence evidence(String completionId) {
        return new CompletionEvidence(completionId, "user-1", "WASTE_COLLECTOR", "collector-1",
                LocalDateTime.now(), 1, UUID.randomUUID().toString(), "digest");
    }
}
