package org.example.infrastructure.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.notification.NotificationModels.PushNotificationMessage;
import org.example.application.port.out.PushNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class FirebasePushNotificationAdapter implements PushNotificationPort {

    private static final Logger LOG = LoggerFactory.getLogger(FirebasePushNotificationAdapter.class);
    private static final String FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

    @ConfigProperty(name = "firebase.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "firebase.credentials-path", defaultValue = "")
    String credentialsPath;


    @ConfigProperty(name = "firebase.project-id", defaultValue = "")
    String configuredProjectId;

    @ConfigProperty(name = "firebase.dry-run", defaultValue = "false")
    boolean dryRun;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private volatile AccessToken cachedAccessToken;
    private volatile ServiceAccount serviceAccount;

    @Override
    public Uni<Void> send(PushNotificationMessage message) {
        if (!enabled) {
            LOG.info("Skipping FCM notification because firebase.enabled=false recipientUserId={}",
                    message == null ? null : message.recipientUserId());
            return Uni.createFrom().voidItem();
        }
        if (message == null || message.tokens().isEmpty()) {
            LOG.info("Skipping FCM notification because message or tokens are empty");
            return Uni.createFrom().voidItem();
        }

        return Uni.createFrom().item(() -> {
                    try {
                        ServiceAccount account = serviceAccount();
                        String accessToken = accessToken(account);
                        for (String token : message.tokens()) {
                            publish(account.projectId(), accessToken, token, message);
                        }
                        LOG.info("Published FCM notification recipientUserId={} tokenCount={}",
                                message.recipientUserId(), message.tokens().size());
                        return true;
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("FCM publish interrupted", ex);
                    } catch (IOException | GeneralSecurityException ex) {
                        throw new IllegalStateException("FCM publish failed", ex);
                    }
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .replaceWithVoid();
    }

    private void publish(String projectId, String accessToken, String token, PushNotificationMessage message)
            throws IOException, InterruptedException {
        Map<String, Object> notification = Map.of(
                "title", message.title(),
                "body", message.body()
        );
        Map<String, Object> fcmMessage = new HashMap<>();
        fcmMessage.put("token", token);
        fcmMessage.put("notification", notification);
        fcmMessage.put("data", message.data());
        fcmMessage.put("webpush", Map.of(
                "notification", notification,
                "data", message.data()
        ));

        Map<String, Object> body = new HashMap<>();
        body.put("validate_only", dryRun);
        body.put("message", fcmMessage);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("FCM publish failed status=" + response.statusCode() + " body=" + response.body());
        }
        JsonNode responseBody = objectMapper.readTree(response.body());
        LOG.info("FCM accepted message recipientUserId={} token={} messageName={}",
                message.recipientUserId(), maskedToken(token), responseBody.path("name").asText());
    }

    private String accessToken(ServiceAccount account)
            throws IOException, InterruptedException, GeneralSecurityException {
        AccessToken current = cachedAccessToken;
        Instant now = Instant.now();
        if (current != null && current.expiresAt().isAfter(now.plusSeconds(60))) {
            return current.value();
        }
        synchronized (this) {
            current = cachedAccessToken;
            if (current != null && current.expiresAt().isAfter(now.plusSeconds(60))) {
                return current.value();
            }
            cachedAccessToken = requestAccessToken(account, now);
            return cachedAccessToken.value();
        }
    }

    private AccessToken requestAccessToken(ServiceAccount account, Instant now)
            throws IOException, InterruptedException, GeneralSecurityException {
        String assertion = signedJwt(account, now);
        String form = "grant_type=" + urlEncode("urn:ietf:params:oauth:grant-type:jwt-bearer")
                + "&assertion=" + urlEncode(assertion);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(account.tokenUri()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Firebase token request failed status=" + response.statusCode() + " body=" + response.body());
        }
        JsonNode json = objectMapper.readTree(response.body());
        return new AccessToken(json.get("access_token").asText(), now.plusSeconds(json.get("expires_in").asLong(3600)));
    }

    private String signedJwt(ServiceAccount account, Instant now) throws GeneralSecurityException, IOException {
        String header = objectMapper.writeValueAsString(Map.of("alg", "RS256", "typ", "JWT"));
        String payload = objectMapper.writeValueAsString(Map.of(
                "iss", account.clientEmail(),
                "scope", FCM_SCOPE,
                "aud", account.tokenUri(),
                "iat", now.getEpochSecond(),
                "exp", now.plusSeconds(3600).getEpochSecond()
        ));
        String unsigned = base64Url(header.getBytes(StandardCharsets.UTF_8)) + "."
                + base64Url(payload.getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey(account.privateKeyPem()));
        signature.update(unsigned.getBytes(StandardCharsets.UTF_8));
        return unsigned + "." + base64Url(signature.sign());
    }

    private PrivateKey privateKey(String privateKeyPem) throws GeneralSecurityException {
        String normalized = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private ServiceAccount serviceAccount() throws IOException {
        ServiceAccount current = serviceAccount;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (serviceAccount == null) {
                JsonNode json = objectMapper.readTree(credentialsFileContent());
                String projectId = configuredProjectId == null || configuredProjectId.isBlank()
                        ? text(json, "project_id")
                        : configuredProjectId.trim();
                serviceAccount = new ServiceAccount(
                        projectId,
                        text(json, "client_email"),
                        text(json, "private_key"),
                        textOrDefault(json, "token_uri", DEFAULT_TOKEN_URI)
                );
            }
            return serviceAccount;
        }
    }

    private String credentialsFileContent() throws IOException {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            throw new IllegalStateException("Firebase is enabled but credentials path is not configured");
        }
        return Files.readString(Path.of(credentialsPath.trim()), StandardCharsets.UTF_8);
    }

    private String text(JsonNode json, String field) {
        String value = textOrDefault(json, field, "");
        if (value.isBlank()) {
            throw new IllegalStateException("Firebase service account is missing field: " + field);
        }
        return value;
    }

    private String textOrDefault(JsonNode json, String field, String defaultValue) {
        JsonNode value = json.get(field);
        return value == null || value.isNull() ? defaultValue : value.asText();
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String maskedToken(String token) {
        if (token == null || token.length() <= 12) {
            return "***";
        }
        return token.substring(0, 6) + "..." + token.substring(token.length() - 6);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record ServiceAccount(String projectId, String clientEmail, String privateKeyPem, String tokenUri) {
    }

    private record AccessToken(String value, Instant expiresAt) {
    }
}
