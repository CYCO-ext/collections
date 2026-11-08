package org.example.infrastructure.identity;

import org.example.application.port.out.AuthenticatedActorPort.AuthenticatedActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.Authenticator;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegisterAuthenticatedActorAdapterTest {
    private RegisterAuthenticatedActorAdapter adapter;
    private FakeHttpClient httpClient;

    @BeforeEach
    void setUp() {
        adapter = new RegisterAuthenticatedActorAdapter();
        adapter.registerBaseUrl = "http://register.test/";
        httpClient = new FakeHttpClient();
        adapter.httpClient = httpClient;
    }

    @Test
    void resolvesIdentityFromRegisterAndForwardsBearerWithoutChangingIt() throws Exception {
        httpClient.status = 200;
        httpClient.body = "{\"userId\":\"user-1\",\"role\":\"DELIVERYMAN\",\"deliverymanId\":\"driver-1\",\"ownerCollectorId\":\"collector-1\"}";

        AuthenticatedActor actor = adapter.resolve("Bearer signed-token").await().indefinitely();

        assertEquals("user-1", actor.userId());
        assertEquals("DELIVERYMAN", actor.role());
        assertEquals("driver-1", actor.deliverymanId());
        assertEquals("collector-1", actor.ownerCollectorId());
        assertEquals("Bearer signed-token", httpClient.request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("http://register.test/session/me", httpClient.request.uri().toString());
    }

    @Test
    void distinguishesUnauthorizedTokenFromRegisterUnavailable() {
        httpClient.status = 401;
        assertThrows(RegisterAuthenticatedActorAdapter.ActorUnauthenticatedException.class,
                () -> adapter.resolve("token").await().indefinitely());

        httpClient.status = 503;
        IllegalStateException unavailable = assertThrows(IllegalStateException.class,
                () -> adapter.resolve("token").await().indefinitely());
        assertEquals("Register service is unavailable", unavailable.getMessage());
    }

    private record TestHttpResponse(int statusCode, String body) implements HttpResponse<String> {
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (name, value) -> true); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return URI.create("http://register.test/session/me"); }
        @Override public Version version() { return Version.HTTP_1_1; }
    }

    private static final class FakeHttpClient extends HttpClient {
        int status;
        String body = "";
        HttpRequest request;

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return null; }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            this.request = request;
            @SuppressWarnings("unchecked") HttpResponse<T> response = (HttpResponse<T>) new TestHttpResponse(status, body);
            return CompletableFuture.completedFuture(response);
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }
    }
}
