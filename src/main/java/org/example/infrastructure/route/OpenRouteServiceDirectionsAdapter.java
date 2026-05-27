package org.example.infrastructure.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.port.out.OpenRouteServiceDirectionsPort;
import org.example.application.route.RouteMapModels.RouteCoordinate;
import org.example.application.usecase.OpenRouteServiceException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;

@Singleton
public class OpenRouteServiceDirectionsAdapter implements OpenRouteServiceDirectionsPort {

    @ConfigProperty(name = "openrouteservice.base-url", defaultValue = "https://api.openrouteservice.org")
    String baseUrl;

    @ConfigProperty(name = "openrouteservice.api-key", defaultValue = "")
    String apiKey;

    @ConfigProperty(name = "openrouteservice.timeout-ms", defaultValue = "10000")
    long timeoutMs;

    @Inject
    ObjectMapper objectMapper;

    HttpClient client;

    @PostConstruct
    void init() {
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    @Override
    public Uni<JsonNode> fetchDrivingCarGeoJson(List<RouteCoordinate> coordinates) {
        if (apiKey == null || apiKey.isBlank()) {
            return Uni.createFrom().failure(new OpenRouteServiceException("OpenRouteService API key is not configured", 500, false));
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizedBaseUrl() + "/v2/directions/driving-car/geojson"))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/geo+json, application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body(coordinates)))
                .build();

        return Uni.createFrom().completionStage(() -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
                .onItem().transform(this::parseResponse)
                .onFailure(java.net.http.HttpTimeoutException.class).transform(ex ->
                        new OpenRouteServiceException("OpenRouteService request timed out", 504, true))
                .onFailure(CompletionException.class).transform(ex -> {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    if (cause instanceof OpenRouteServiceException openRouteServiceException) {
                        return openRouteServiceException;
                    }
                    return new OpenRouteServiceException("OpenRouteService request failed: " + cause.getMessage(), 502, false);
                });
    }

    private JsonNode parseResponse(HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new OpenRouteServiceException("OpenRouteService returned HTTP " + response.statusCode(), response.statusCode(), false);
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception ex) {
            throw new OpenRouteServiceException("OpenRouteService returned invalid GeoJSON", 502, false);
        }
    }

    private String body(List<RouteCoordinate> coordinates) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode coordinateArray = root.putArray("coordinates");
        for (RouteCoordinate coordinate : coordinates) {
            ArrayNode pair = coordinateArray.addArray();
            pair.add(coordinate.longitude());
            pair.add(coordinate.latitude());
        }
        return root.toString();
    }

    private String normalizedBaseUrl() {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
