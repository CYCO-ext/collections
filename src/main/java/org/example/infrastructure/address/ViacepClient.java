package org.example.infrastructure.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
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

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    // keep same user agent as other clients
    private static final String USER_AGENT = "collections-service/1.0 (contact@example.com)";

    public Uni<Optional<Address>> geocodeCep(String cep) {
        return Uni.createFrom().item(() -> {
            try {
                if (cep == null) return Optional.empty();
                String digits = cep.replaceAll("\\D", "");
                if (digits.length() != 8) return Optional.empty();

                String url = "https://viacep.com.br/ws/" + digits + "/json/";
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();

                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    LOG.warn("ViaCEP returned status {} for cep {}", resp.statusCode(), cep);
                    return Optional.empty();
                }

                JsonNode node = mapper.readTree(resp.body());
                if (node.has("erro") && node.get("erro").asBoolean(false)) {
                    return Optional.empty();
                }

                String logradouro = node.has("logradouro") && !node.get("logradouro").isNull() ? node.get("logradouro").asText() : null;
                String localidade = node.has("localidade") && !node.get("localidade").isNull() ? node.get("localidade").asText() : null;
                String cepResp = node.has("cep") && !node.get("cep").isNull() ? node.get("cep").asText() : digits;

                Address a = new Address();
                a.setStreet(logradouro);
                a.setCity(localidade);
                a.setZipCode(cepResp != null ? cepResp.replaceAll("\\D", "") : digits);

                return Optional.of(a);
            } catch (Exception e) {
                LOG.error("Error calling ViaCEP", e);
                return Optional.empty();
            }
        });
    }
}
