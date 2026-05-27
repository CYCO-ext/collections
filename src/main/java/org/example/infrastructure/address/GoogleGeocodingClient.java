package org.example.infrastructure.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Singleton
public class GoogleGeocodingClient {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleGeocodingClient.class);

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "google.geocoding.base-url", defaultValue = "https://maps.googleapis.com")
    String baseUrl;

    @ConfigProperty(name = "google.geocoding.api-key", defaultValue = "")
    String apiKey;

    @ConfigProperty(name = "google.geocoding.timeout-ms", defaultValue = "5000")
    long timeoutMs;

    @ConfigProperty(name = "google.geocoding.country", defaultValue = "Brazil")
    String country;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public Uni<Optional<Coordinates>> geocode(GoogleGeocodingAddress address) {
        String formattedAddress = formattedAddress(address);
        if (formattedAddress.isBlank()) {
            LOG.warn("Google Geocoding skipped because address is blank");
            return Uni.createFrom().item(Optional.empty());
        }
        if (apiKey == null || apiKey.isBlank()) {
            LOG.warn("Google Geocoding API key is not configured");
            return Uni.createFrom().item(Optional.empty());
        }

        String url = normalizedBaseUrl()
                + "/maps/api/geocode/json?address="
                + encode(formattedAddress)
                + "&key="
                + encode(apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();

        return Uni.createFrom()
                .completionStage(http.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
                .onItem().transform(response -> parseResponse(formattedAddress, response))
                .onFailure().invoke(error -> LOG.error("Error calling Google Geocoding for address {}", formattedAddress, error))
                .onFailure().recoverWithItem(Optional.empty());
    }

    private Optional<Coordinates> parseResponse(String formattedAddress, HttpResponse<String> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOG.warn("Google Geocoding returned HTTP {} for address {}", response.statusCode(), formattedAddress);
            return Optional.empty();
        }

        try {
            JsonNode root = mapper.readTree(response.body());
            String status = root.path("status").asText("");
            if (!"OK".equals(status)) {
                LOG.warn("Google Geocoding returned status {} for address {}", status, formattedAddress);
                return Optional.empty();
            }

            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                LOG.warn("Google Geocoding returned OK without results for address {}", formattedAddress);
                return Optional.empty();
            }

            JsonNode location = results.get(0).path("geometry").path("location");
            JsonNode lat = location.get("lat");
            JsonNode lng = location.get("lng");
            if (lat == null || lng == null || !lat.isNumber() || !lng.isNumber()) {
                LOG.warn("Google Geocoding response is missing numeric lat/lng for address {}", formattedAddress);
                return Optional.empty();
            }

            return Optional.of(new Coordinates(lat.asDouble(), lng.asDouble()));
        } catch (Exception e) {
            LOG.error("Error parsing Google Geocoding response", e);
            return Optional.empty();
        }
    }

    private String formattedAddress(GoogleGeocodingAddress address) {
        StringBuilder query = new StringBuilder();
        appendPart(query, address.street());
        appendPart(query, address.number());
        appendPart(query, address.city());
        appendPart(query, address.state());
        appendPart(query, address.zipCode());
        appendPart(query, firstNonBlank(address.country(), country));
        return query.toString();
    }

    private void appendPart(StringBuilder query, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!query.isEmpty()) {
            query.append(", ");
        }
        query.append(value.trim());
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred.trim();
        }
        return fallback == null ? null : fallback.trim();
    }

    private String normalizedBaseUrl() {
        return baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record GoogleGeocodingAddress(
            String street,
            String number,
            String city,
            String state,
            String zipCode,
            String country
    ) {
    }

    public record Coordinates(double latitude, double longitude) {
    }
}
