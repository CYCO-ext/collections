package org.example.infrastructure.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.application.route.RouteMapModels.RouteCoordinate;
import org.example.application.usecase.OpenRouteServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenRouteServiceDirectionsAdapterTest {
    private HttpServer server;
    private OpenRouteServiceDirectionsAdapter adapter;
    private AtomicReference<String> method;
    private AtomicReference<String> path;
    private AtomicReference<String> authorization;
    private AtomicReference<String> accept;
    private AtomicReference<String> contentType;
    private AtomicReference<String> body;

    @BeforeEach
    void setUp() throws IOException {
        method = new AtomicReference<>();
        path = new AtomicReference<>();
        authorization = new AtomicReference<>();
        accept = new AtomicReference<>();
        contentType = new AtomicReference<>();
        body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v2/directions/driving-car/geojson", this::handleSuccess);
        server.createContext("/failure/v2/directions/driving-car/geojson", this::handleFailure);
        server.start();

        adapter = new OpenRouteServiceDirectionsAdapter();
        adapter.baseUrl = "http://localhost:" + server.getAddress().getPort();
        adapter.apiKey = "test-api-key";
        adapter.timeoutMs = 5000;
        adapter.objectMapper = new ObjectMapper();
        adapter.init();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendsDirectionsGeoJsonRequestWithLongitudeLatitudeCoordinates() {
        var result = adapter.fetchDrivingCarGeoJson(List.of(
                new RouteCoordinate("request-1", -23.0, -46.0),
                new RouteCoordinate("request-2", -23.1, -46.1)
        )).await().indefinitely();

        assertEquals("FeatureCollection", result.get("type").asText());
        assertEquals("POST", method.get());
        assertEquals("/v2/directions/driving-car/geojson", path.get());
        assertEquals("test-api-key", authorization.get());
        assertEquals("application/geo+json, application/json", accept.get());
        assertEquals("application/json", contentType.get());
        assertEquals("{\"coordinates\":[[-46.0,-23.0],[-46.1,-23.1]]}", body.get());
    }

    @Test
    void mapsProviderFailureToOpenRouteServiceException() {
        adapter.baseUrl = "http://localhost:" + server.getAddress().getPort() + "/failure";

        OpenRouteServiceException exception = assertThrows(OpenRouteServiceException.class,
                () -> adapter.fetchDrivingCarGeoJson(List.of(
                        new RouteCoordinate("request-1", -23.0, -46.0),
                        new RouteCoordinate("request-2", -23.1, -46.1)
                )).await().indefinitely());

        assertEquals("OpenRouteService returned HTTP 500", exception.getMessage());
        assertEquals(500, exception.statusCode());
    }

    private void handleSuccess(HttpExchange exchange) throws IOException {
        capture(exchange);
        byte[] response = "{\"type\":\"FeatureCollection\",\"features\":[]}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private void handleFailure(HttpExchange exchange) throws IOException {
        capture(exchange);
        byte[] response = "{\"error\":\"failed\"}".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(500, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private void capture(HttpExchange exchange) throws IOException {
        method.set(exchange.getRequestMethod());
        path.set(exchange.getRequestURI().getPath());
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        accept.set(exchange.getRequestHeaders().getFirst("Accept"));
        contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }
}
