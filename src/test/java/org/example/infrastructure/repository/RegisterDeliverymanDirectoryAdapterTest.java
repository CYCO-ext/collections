package org.example.infrastructure.repository;

import org.example.application.port.out.DeliverymanDirectoryPort.DeliverymanRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpHeaders;
import java.net.URI;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegisterDeliverymanDirectoryAdapterTest {
    private RegisterDeliverymanDirectoryAdapter adapter;
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        adapter = new RegisterDeliverymanDirectoryAdapter();
        adapter.registerBaseUrl = "http://register.test/";
        adapter.objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        httpClient = mock(HttpClient.class);
        adapter.httpClient = httpClient;
    }

    @Test
    void findByIdMapsRegisterDeliverymanDetail() {
        HttpResponse<String> response = response(200,
                "{\"id\":\"deliveryman-1\",\"wasteCollectorId\":\"collector-1\",\"name\":\"Driver One\",\"active\":true}");
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        DeliverymanRef result = adapter.findById("deliveryman-1").await().indefinitely();

        assertEquals("deliveryman-1", result.id());
        assertEquals("collector-1", result.collectorId());
        assertEquals("Driver One", result.name());
        assertEquals(true, result.active());
    }

    @Test
    void findByIdReturnsNullForRegisterNotFound() {
        HttpResponse<String> response = response(404, "");
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        DeliverymanRef result = adapter.findById("missing").await().indefinitely();

        assertNull(result);
    }

    @Test
    void findByIdFailsForRegisterServiceError() {
        HttpResponse<String> response = response(500, "boom");
        when(httpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> adapter.findById("deliveryman-1").await().indefinitely());

        assertEquals("Register service returned HTTP 500", exception.getMessage());
    }

    private HttpResponse<String> response(int status, String body) {
        return new TestHttpResponse(status, body);
    }

    private record TestHttpResponse(int statusCode, String body) implements HttpResponse<String> {
        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("http://register.test/deliveryman/deliveryman-1");
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }
    }
}
