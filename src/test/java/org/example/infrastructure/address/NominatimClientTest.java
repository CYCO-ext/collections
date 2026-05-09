package org.example.infrastructure.address;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NominatimClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void geocodeReturnsCoordinates() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search", exchange -> {
            byte[] body = "[{\"lat\":\"-23.55052\",\"lon\":\"-46.633308\"}]"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        NominatimClient client = new NominatimClient();
        client.mapper = new ObjectMapper();
        client.endpoint = "http://localhost:" + server.getAddress().getPort();
        client.timeoutMs = 1000;

        Optional<double[]> result = client.geocode("Praça da Sé, São Paulo, SP, Brazil", "collections-service-test")
                .await().indefinitely();

        assertTrue(result.isPresent());
        assertArrayEquals(new double[]{-23.55052, -46.633308}, result.get());
    }

    @Test
    void geocodeReturnsEmptyForRateLimit() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/search", exchange -> {
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });
        server.start();

        NominatimClient client = new NominatimClient();
        client.mapper = new ObjectMapper();
        client.endpoint = "http://localhost:" + server.getAddress().getPort();
        client.timeoutMs = 1000;

        Optional<double[]> result = client.geocode("Praça da Sé, São Paulo, SP, Brazil", "collections-service-test")
                .await().indefinitely();

        assertTrue(result.isEmpty());
    }
}
