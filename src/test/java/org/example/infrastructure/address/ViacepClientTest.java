package org.example.infrastructure.address;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.example.domain.entity.Address;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViacepClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void geocodeCepReturnsNormalizedAddressFields() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/ws/01001000/json/", exchange -> {
            byte[] body = "{\"cep\":\"01001-000\",\"logradouro\":\"Praça da Sé\",\"localidade\":\"São Paulo\",\"uf\":\"SP\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        ViacepClient client = new ViacepClient();
        client.mapper = new ObjectMapper();
        client.endpoint = "http://localhost:" + server.getAddress().getPort() + "/ws";
        client.timeoutMs = 1000;
        client.userAgent = "collections-service-test";

        Optional<Address> result = client.geocodeCep("01001-000").await().indefinitely();

        assertTrue(result.isPresent());
        assertEquals("01001000", result.get().getZipCode());
        assertEquals("Praça da Sé", result.get().getStreet());
        assertEquals("São Paulo", result.get().getCity());
        assertEquals("SP", result.get().getState());
    }
}
