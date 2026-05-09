package org.example.infrastructure.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.domain.entity.Address;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

@Singleton
public class ViacepClient {

    private static final Logger LOG = LoggerFactory.getLogger(ViacepClient.class);

    @Inject
    ObjectMapper mapper;

    @ConfigProperty(name = "viacep.endpoint", defaultValue = "https://viacep.com.br/ws")
    String endpoint;

    @ConfigProperty(name = "enrichment.timeout.ms", defaultValue = "5000")
    long timeoutMs;

    @ConfigProperty(name = "enrichment.user-agent", defaultValue = "collections-service/1.0")
    String userAgent;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public Uni<Optional<Address>> geocodeCep(String cep) {
        return Uni.createFrom().item(() -> {
            try {
                String digits = normalizeCep(cep);
                if (digits == null) {
                    return Optional.empty();
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint.replaceAll("/+$", "") + "/" + digits + "/json/"))
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("User-Agent", userAgent)
                        .GET()
                        .build();

                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    LOG.warn("ViaCEP returned status {} for cep {}", response.statusCode(), cep);
                    return Optional.empty();
                }

                JsonNode node = mapper.readTree(response.body());
                if (node.has("erro") && node.get("erro").asBoolean(false)) {
                    return Optional.empty();
                }

                Address address = new Address();
                address.setStreet(readText(node, "logradouro"));
                address.setCity(readText(node, "localidade"));
                address.setState(readText(node, "uf"));
                address.setZipCode(normalizeCep(readText(node, "cep")) != null ? normalizeCep(readText(node, "cep")) : digits);
                return Optional.of(address);
            } catch (Exception e) {
                LOG.error("Error calling ViaCEP", e);
                return Optional.empty();
            }
        });
    }

    private String readText(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText().trim();
        return value.isEmpty() ? null : value;
    }

    private String normalizeCep(String cep) {
        if (cep == null) {
            return null;
        }
        String digits = cep.replaceAll("\\D", "");
        return digits.length() == 8 ? digits : null;
    }
}
