package org.example.infrastructure.address;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleGeocodingClientTest {

    private HttpServer server;
    private GoogleGeocodingClient client;
    private AtomicReference<String> path;
    private AtomicReference<Map<String, String>> query;

    @BeforeEach
    void setUp() throws IOException {
        path = new AtomicReference<>();
        query = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/maps/api/geocode/json", this::handleOk);
        server.createContext("/zero/maps/api/geocode/json", this::handleZeroResults);
        server.createContext("/denied/maps/api/geocode/json", this::handleRequestDenied);
        server.createContext("/error/maps/api/geocode/json", this::handleHttpError);
        server.createContext("/invalid/maps/api/geocode/json", this::handleInvalidJson);
        server.start();

        client = new GoogleGeocodingClient();
        client.mapper = new ObjectMapper();
        client.baseUrl = "http://localhost:" + server.getAddress().getPort();
        client.apiKey = "google-key";
        client.timeoutMs = 1000;
        client.country = "Brazil";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void geocodeSendsFullAddressAndReturnsCoordinates() {
        Optional<GoogleGeocodingClient.Coordinates> result = client.geocode(new GoogleGeocodingClient.GoogleGeocodingAddress(
                "Praça da Sé",
                "100",
                "São Paulo",
                "SP",
                "01001000",
                null
        )).await().indefinitely();

        assertTrue(result.isPresent());
        assertEquals(-23.55052, result.get().latitude());
        assertEquals(-46.633308, result.get().longitude());
        assertEquals("/maps/api/geocode/json", path.get());
        assertEquals("Praça da Sé, 100, São Paulo, SP, 01001000, Brazil", query.get().get("address"));
        assertEquals("google-key", query.get().get("key"));
    }

    @Test
    void geocodeReturnsEmptyForZeroResults() {
        client.baseUrl = "http://localhost:" + server.getAddress().getPort() + "/zero";

        Optional<GoogleGeocodingClient.Coordinates> result = client.geocode(new GoogleGeocodingClient.GoogleGeocodingAddress(
                "Unknown Street",
                "999",
                "São Paulo",
                "SP",
                "01001000",
                null
        )).await().indefinitely();

        assertTrue(result.isEmpty());
    }

    @Test
    void geocodeReturnsEmptyForProviderStatusError() {
        client.baseUrl = "http://localhost:" + server.getAddress().getPort() + "/denied";

        Optional<GoogleGeocodingClient.Coordinates> result = client.geocode(new GoogleGeocodingClient.GoogleGeocodingAddress(
                "Praça da Sé",
                "100",
                "São Paulo",
                "SP",
                "01001000",
                null
        )).await().indefinitely();

        assertTrue(result.isEmpty());
    }

    @Test
    void geocodeReturnsEmptyForHttpError() {
        client.baseUrl = "http://localhost:" + server.getAddress().getPort() + "/error";

        Optional<GoogleGeocodingClient.Coordinates> result = client.geocode(new GoogleGeocodingClient.GoogleGeocodingAddress(
                "Praça da Sé",
                "100",
                "São Paulo",
                "SP",
                "01001000",
                null
        )).await().indefinitely();

        assertTrue(result.isEmpty());
    }

    @Test
    void geocodeReturnsEmptyForInvalidJson() {
        client.baseUrl = "http://localhost:" + server.getAddress().getPort() + "/invalid";

        Optional<GoogleGeocodingClient.Coordinates> result = client.geocode(new GoogleGeocodingClient.GoogleGeocodingAddress(
                "Praça da Sé",
                "100",
                "São Paulo",
                "SP",
                "01001000",
                null
        )).await().indefinitely();

        assertTrue(result.isEmpty());
    }

    @Test
    void geocodeReturnsEmptyWhenApiKeyIsMissing() {
        client.apiKey = "";

        Optional<GoogleGeocodingClient.Coordinates> result = client.geocode(new GoogleGeocodingClient.GoogleGeocodingAddress(
                "Praça da Sé",
                "100",
                "São Paulo",
                "SP",
                "01001000",
                null
        )).await().indefinitely();

        assertTrue(result.isEmpty());
    }

    private void handleOk(HttpExchange exchange) throws IOException {
        capture(exchange);
        byte[] body = "{\"status\":\"OK\",\"results\":[{\"geometry\":{\"location\":{\"lat\":-23.55052,\"lng\":-46.633308}}}]}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void handleZeroResults(HttpExchange exchange) throws IOException {
        capture(exchange);
        byte[] body = "{\"status\":\"ZERO_RESULTS\",\"results\":[]}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void handleRequestDenied(HttpExchange exchange) throws IOException {
        capture(exchange);
        byte[] body = "{\"status\":\"REQUEST_DENIED\",\"error_message\":\"denied\",\"results\":[]}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void handleHttpError(HttpExchange exchange) throws IOException {
        capture(exchange);
        exchange.sendResponseHeaders(500, -1);
        exchange.close();
    }

    private void handleInvalidJson(HttpExchange exchange) throws IOException {
        capture(exchange);
        byte[] body = "not-json".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private void capture(HttpExchange exchange) {
        path.set(exchange.getRequestURI().getPath());
        query.set(parseQuery(exchange.getRequestURI().getRawQuery()));
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> values = new HashMap<>();
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }
}
