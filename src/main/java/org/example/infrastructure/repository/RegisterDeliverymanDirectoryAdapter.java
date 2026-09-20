package org.example.infrastructure.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.port.out.DeliverymanDirectoryPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Singleton
public class RegisterDeliverymanDirectoryAdapter implements DeliverymanDirectoryPort {
    @ConfigProperty(name = "register.service.base-url")
    String registerBaseUrl;

    HttpClient httpClient = HttpClient.newHttpClient();
    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Uni<DeliverymanRef> findById(String deliverymanId) {
        if (deliverymanId == null || deliverymanId.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("deliverymanId is required"));
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl() + "/deliveryman/" + deliverymanId))
                .GET()
                .build();

        return Uni.createFrom().completionStage(httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
                .flatMap(response -> {
                    if (response.statusCode() == 404) {
                        return Uni.createFrom().nullItem();
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        return Uni.createFrom().failure(new IllegalStateException("Register service returned HTTP " + response.statusCode()));
                    }
                    try {
                        JsonNode json = objectMapper.readTree(response.body());
                        String id = text(json, "id");
                        String collectorId = text(json, "wasteCollectorId");
                        String name = text(json, "name");
                        boolean active = !json.has("active") || json.get("active").asBoolean(true);
                        return Uni.createFrom().item(new DeliverymanRef(id, collectorId, name, active));
                    } catch (Exception ex) {
                        return Uni.createFrom().failure(new IllegalStateException("Register service returned invalid deliveryman payload", ex));
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
}
