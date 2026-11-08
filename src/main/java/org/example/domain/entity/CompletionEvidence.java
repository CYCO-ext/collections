package org.example.domain.entity;

import java.time.LocalDateTime;
import java.util.List;

public record CompletionEvidence(
        String completionId,
        String actorUserId,
        String actorRole,
        String actorProfileId,
        LocalDateTime validatedAt,
        int codeVersion,
        String idempotencyKey,
        String payloadDigest,
        List<String> pendingEffects
) {
    public CompletionEvidence(String completionId, String actorUserId, String actorRole, String actorProfileId,
                              LocalDateTime validatedAt, int codeVersion, String idempotencyKey, String payloadDigest) {
        this(completionId, actorUserId, actorRole, actorProfileId, validatedAt, codeVersion,
                idempotencyKey, payloadDigest, List.of());
    }

    public CompletionEvidence {
        if (completionId == null || completionId.isBlank()) throw new IllegalArgumentException("completionId is required");
        if (actorUserId == null || actorUserId.isBlank()) throw new IllegalArgumentException("actorUserId is required");
        if (actorRole == null || actorRole.isBlank()) throw new IllegalArgumentException("actorRole is required");
        if (actorProfileId == null || actorProfileId.isBlank()) throw new IllegalArgumentException("actorProfileId is required");
        if (validatedAt == null) throw new IllegalArgumentException("validatedAt is required");
        if (codeVersion < 1) throw new IllegalArgumentException("codeVersion must be positive");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        if (payloadDigest == null || payloadDigest.isBlank()) throw new IllegalArgumentException("payloadDigest is required");
        pendingEffects = pendingEffects == null ? List.of() : List.copyOf(pendingEffects);
    }
}
