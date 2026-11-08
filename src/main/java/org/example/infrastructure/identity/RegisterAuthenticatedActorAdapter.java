package org.example.infrastructure.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.port.out.AuthenticatedActorPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Singleton
public class RegisterAuthenticatedActorAdapter implements AuthenticatedActorPort {
    @ConfigProperty(name = "register.service.base-url")
    String registerBaseUrl;

    HttpClient httpClient = HttpClient.newHttpClient();
    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Uni<AuthenticatedActor> resolve(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("bearerToken is required"));
        }

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl() + "/session/me"))
                    .header("Authorization", bearerToken.startsWith("Bearer ") ? bearerToken : "Bearer " + bearerToken)
                    .GET()
                    .build();
        } catch (RuntimeException ex) {
            return Uni.createFrom().failure(new IllegalStateException("Register service is unavailable", ex));
        }

        return Uni.createFrom().completionStage(httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
                .onFailure().transform(ex -> new IllegalStateException("Register service is unavailable", ex))
                .flatMap(response -> {
                    if (response.statusCode() == 401) {
                        return Uni.createFrom().failure(new ActorUnauthenticatedException());
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return Uni.createFrom().failure(new IllegalStateException("Register service is unavailable"));
                    }
                    try {
                        JsonNode json = objectMapper.readTree(response.body());
                        String userId = text(json, "userId");
                        String role = text(json, "role");
                        if (userId == null || role == null) {
                            throw new IllegalArgumentException("required identity fields are missing");
                        }
                        return Uni.createFrom().item(new AuthenticatedActor(
                                userId,
                                role,
                                text(json, "generatorId"),
                                text(json, "collectorId"),
                                text(json, "deliverymanId"),
                                text(json, "ownerCollectorId")));
                    } catch (Exception ex) {
                        return Uni.createFrom().failure(new IllegalStateException("Register service returned invalid identity", ex));
                    }
                });
    }

    private String normalizeBaseUrl() {
        if (registerBaseUrl == null || registerBaseUrl.isBlank()) {
            throw new IllegalStateException("register.service.base-url is required");
        }
        return registerBaseUrl.endsWith("/")
                ? registerBaseUrl.substring(0, registerBaseUrl.length() - 1)
                : registerBaseUrl;
    }

    private String text(JsonNode json, String field) {
        JsonNode value = json.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    public static class ActorUnauthenticatedException extends RuntimeException {
    }
}
