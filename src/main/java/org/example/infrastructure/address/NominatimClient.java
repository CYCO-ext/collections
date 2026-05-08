package org.example.infrastructure.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@Singleton
public class NominatimClient {
    private static final Logger LOG = LoggerFactory.getLogger(NominatimClient.class);

    @Inject
    ObjectMapper mapper;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public Uni<Optional<double[]>> geocode(String query, String userAgent) {
        return Uni.createFrom().item(() -> {
            try {
                String url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", userAgent != null ? userAgent : "collections-service/1.0 (+contact)")
                        .GET()
                        .build();

                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    LOG.warn("Nominatim returned status {} for query {}", resp.statusCode(), query);
                    return Optional.empty();
                }

                JsonNode arr = mapper.readTree(resp.body());
                if (arr.isArray() && arr.size() > 0) {
                    JsonNode first = arr.get(0);
                    double lat = first.get("lat").asDouble();
                    double lon = first.get("lon").asDouble();
                    return Optional.of(new double[]{lat, lon});
                }
                return Optional.empty();
            } catch (Exception e) {
                LOG.error("Error calling Nominatim", e);
                return Optional.empty();
            }
        });
    }
}
