package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.GeneratorNotificationTokenPort;
import org.example.domain.entity.GeneratorNotificationToken;

@Singleton
public class RegisterGeneratorNotificationTokenUseCase {

    @Inject
    GeneratorNotificationTokenPort tokenPort;

    public Uni<Void> register(String generatorId, String token, String platform) {
        String normalizedGeneratorId = normalizeRequired(generatorId, "generator id is required");
        String normalizedToken = normalizeRequired(token, "notification token is required");
        String normalizedPlatform = platform == null || platform.isBlank() ? "UNKNOWN" : platform.trim().toUpperCase();
        return tokenPort.saveOrUpdate(new GeneratorNotificationToken(normalizedGeneratorId, normalizedToken, normalizedPlatform));
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
