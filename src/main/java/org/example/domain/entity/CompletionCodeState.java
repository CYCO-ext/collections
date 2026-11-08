package org.example.domain.entity;

import java.time.LocalDateTime;
import java.util.Optional;

public record CompletionCodeState(
        int version,
        String ciphertext,
        String nonce,
        String keyId,
        String verifier,
        LocalDateTime issuedAt,
        int failedAttempts,
        LocalDateTime blockedUntil,
        LocalDateTime consumedAt
) {
    public CompletionCodeState {
        if (version < 1) throw new IllegalArgumentException("version must be positive");
        if (ciphertext == null || ciphertext.isBlank()) throw new IllegalArgumentException("ciphertext is required");
        if (nonce == null || nonce.isBlank()) throw new IllegalArgumentException("nonce is required");
        if (keyId == null || keyId.isBlank()) throw new IllegalArgumentException("keyId is required");
        if (verifier == null || verifier.isBlank()) throw new IllegalArgumentException("verifier is required");
        if (issuedAt == null) throw new IllegalArgumentException("issuedAt is required");
        if (failedAttempts < 0) throw new IllegalArgumentException("failedAttempts cannot be negative");
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isBlockedAt(LocalDateTime instant) {
        return blockedUntil != null && instant != null && blockedUntil.isAfter(instant);
    }

    public static Optional<String> normalizeInput(String code) {
        if (code == null) return Optional.empty();
        String normalized = code.trim();
        return normalized.matches("[0-9]{6}") ? Optional.of(normalized) : Optional.empty();
    }
}
