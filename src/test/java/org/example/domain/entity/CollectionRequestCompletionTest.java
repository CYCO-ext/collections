package org.example.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionRequestCompletionTest {
    @Test
    void codeInputKeepsLeadingZerosAndAcceptsOnlySixAsciiDigits() {
        assertEquals("001234", CompletionCodeState.normalizeInput(" 001234 ").orElseThrow());
        assertTrue(CompletionCodeState.normalizeInput("12345").isEmpty());
        assertTrue(CompletionCodeState.normalizeInput("１２３４５６").isEmpty());
    }

    @Test
    void legacyConfirmationsDoNotAuthorizeCompletionWithoutCodeEvidence() {
        CollectionRequest request = inProgressRequest();
        request.setGeneratorConfirmed(true);
        request.setCollectorConfirmed(true);

        assertFalse(request.canMarkCompleted());
    }

    @Test
    void validatedEvidenceAuthorizesOnlyAnActiveCollection() {
        CollectionRequest request = inProgressRequest();
        request.setCollectorConfirmed(true);
        request.setCompletionEvidence(evidence());

        assertTrue(request.canMarkCompleted());
        request.setStatus(CollectionRequest.Status.CANCELLED);
        assertFalse(request.canMarkCompleted());
    }

    @Test
    void consumedCodeAndHistoricalCompletionRemainReadable() {
        LocalDateTime now = LocalDateTime.now();
        CompletionCodeState consumedCode = new CompletionCodeState(
                1, "ciphertext", "nonce", "key-1", "verifier", now, 0, null, now);
        assertTrue(consumedCode.isConsumed());

        CollectionRequest historical = inProgressRequest();
        historical.setStatus(CollectionRequest.Status.COMPLETED);
        assertEquals(CollectionRequest.Status.COMPLETED, historical.getStatus());
        assertFalse(historical.canMarkCompleted());
    }

    private CollectionRequest inProgressRequest() {
        CollectionRequest request = new CollectionRequest("generator-1", "address-1", List.of("paper"), 10.0);
        request.setStatus(CollectionRequest.Status.IN_PROGRESS);
        return request;
    }

    private CompletionEvidence evidence() {
        return new CompletionEvidence(
                "completion-1", "user-1", "WASTE_COLLECTOR", "collector-1",
                LocalDateTime.now(), 1, "idempotency-1", "digest-1");
    }
}
