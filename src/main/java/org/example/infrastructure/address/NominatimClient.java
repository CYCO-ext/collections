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
public class NominatimClient {

    private static final Logger LOG = LoggerFactory.getLogger(NominatimClient.class);

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "nominatim.endpoint", defaultValue = "https://nominatim.openstreetmap.org")
    String endpoint;

    @ConfigProperty(name = "enrichment.timeout.ms", defaultValue = "5000")
    long timeoutMs;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public Uni<Optional<double[]>> geocode(String query, String userAgent) {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = endpoint.replaceAll("/+$", "") + "/search?format=json&limit=1&q=" + encodedQuery;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", userAgent != null && !userAgent.isBlank() ? userAgent : "collections-service/1.0")
                .GET()
                .build();

        return Uni.createFrom()
                .completionStage(http.sendAsync(request, HttpResponse.BodyHandlers.ofString()))
                .onItem().transform(response -> parseResponse(query, response))
                .onFailure().invoke(error -> LOG.error("Error calling Nominatim for query {}", query, error))
                .onFailure().recoverWithItem(Optional.empty());
    }

    private Optional<double[]> parseResponse(String query, HttpResponse<String> response) {
        if (response.statusCode() == 429 || response.statusCode() >= 500) {
            LOG.warn("Nominatim temporarily unavailable with status {} for query {}", response.statusCode(), query);
            return Optional.empty();
        }

        if (response.statusCode() != 200) {
            LOG.warn("Nominatim returned status {} for query {}", response.statusCode(), query);
            return Optional.empty();
        }

        try {
            JsonNode array = mapper.readTree(response.body());
            if (!array.isArray() || array.isEmpty()) {
                LOG.warn("No results found in Nominatim for {}", query);
                return Optional.empty();
            }

            JsonNode first = array.get(0);
            JsonNode latNode = first.get("lat");
            JsonNode lonNode = first.get("lon");
            if (latNode == null || lonNode == null) {
                LOG.warn("Missing lat/lon in Nominatim response for {}", query);
                return Optional.empty();
            }

            return Optional.of(new double[]{latNode.asDouble(), lonNode.asDouble()});
        } catch (Exception e) {
            LOG.error("Error parsing Nominatim response", e);
            return Optional.empty();
        }
    }
}
