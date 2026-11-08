package org.example.domain.entity;

import java.time.LocalDateTime;

public record CompletionEvidence(
        String completionId,
        String actorUserId,
        String actorRole,
        String actorProfileId,
        LocalDateTime validatedAt,
        int codeVersion,
        String idempotencyKey,
        String payloadDigest
) {
    public CompletionEvidence {
        if (completionId == null || completionId.isBlank()) throw new IllegalArgumentException("completionId is required");
        if (actorUserId == null || actorUserId.isBlank()) throw new IllegalArgumentException("actorUserId is required");
        if (actorRole == null || actorRole.isBlank()) throw new IllegalArgumentException("actorRole is required");
        if (actorProfileId == null || actorProfileId.isBlank()) throw new IllegalArgumentException("actorProfileId is required");
        if (validatedAt == null) throw new IllegalArgumentException("validatedAt is required");
        if (codeVersion < 1) throw new IllegalArgumentException("codeVersion must be positive");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        if (payloadDigest == null || payloadDigest.isBlank()) throw new IllegalArgumentException("payloadDigest is required");
    }
}
