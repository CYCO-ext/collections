package org.example.presentation.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.CompletionCodeState;
import org.example.domain.entity.CompletionEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class CompletionResourceTest {
    @Inject
    CompletionRequestPortFixture requestPort;

    @Inject
    ObjectMapper objectMapper;

    @BeforeEach
    void clearRequests() {
        requestPort.clear();
    }

    @Test
    void legacyGeneratorConfirmationReturnsConflictWithoutMutation() {
        CollectionRequest request = activeRequest();
        requestPort.seed(request);

        given().contentType("application/json")
                .when().post("/api/requests/req-001/confirm-generator")
                .then().statusCode(409)
                .body("error.code", equalTo("COMPLETION_CODE_REQUIRED"));

        assertEquals(CollectionRequest.Status.IN_PROGRESS, request.getStatus());
        assertEquals(Boolean.FALSE, request.getCollectorConfirmed());
    }

    @Test
    void responsibleCollectorConfirmsWithZeroPaddedCodeAndIdempotentRetry() throws Exception {
        CollectionRequest request = activeRequest();
        requestPort.seed(request);
        String key = "0fb0c41d-67c6-4727-a944-62b78b7f74b9";

        String completionId = given()
                .contentType("application/json")
                .header("Authorization", "Bearer collector")
                .header("Idempotency-Key", key)
                .body(new CompletionResource.ConfirmCollectorCompletionDTO("001234"))
                .when().post("/api/requests/req-001/confirm-collector")
                .then().statusCode(200)
                .header("Cache-Control", "no-store")
                .body("requestId", equalTo("req-001"))
                .body("status", equalTo("COMPLETED"))
                .extract().path("completionId");

        given().contentType("application/json")
                .header("Authorization", "Bearer collector")
                .header("Idempotency-Key", key)
                .body(new CompletionResource.ConfirmCollectorCompletionDTO("001234"))
                .when().post("/api/requests/req-001/confirm-collector")
                .then().statusCode(200).body("completionId", equalTo(completionId));
        given().contentType("application/json")
                .header("Authorization", "Bearer collector")
                .header("Idempotency-Key", key)
                .body(new CompletionResource.ConfirmCollectorCompletionDTO("654321"))
                .when().post("/api/requests/req-001/confirm-collector")
                .then().statusCode(409)
                .body("error.code", equalTo("IDEMPOTENCY_CONFLICT"));

        assertEquals(CollectionRequest.Status.COMPLETED, request.getStatus());
        CompletionEvidence audit = request.getCompletionEvidence();
        assertNotNull(audit);
        assertEquals("req-001", request.getId());
        assertEquals("user-collector", audit.actorUserId());
        assertEquals("WASTE_COLLECTOR", audit.actorRole());
        assertEquals("collector-1", audit.actorProfileId());
        assertNotNull(audit.validatedAt());
        String persistedJson = objectMapper.writeValueAsString(request);
        assertFalse(persistedJson.contains("001234"));
        assertFalse(persistedJson.contains("cipher"));
        assertFalse(persistedJson.contains("test-verifier"));
    }

    @Test
    void invalidAuthenticationThirdPartyAndGeneratorAreRejected() {
        requestPort.seed(activeRequest());
        String key = "1fb0c41d-67c6-4727-a944-62b78b7f74b9";
        for (String bearer : List.of("Bearer invalid", "Bearer third", "Bearer generator")) {
            int expectedStatus = "Bearer invalid".equals(bearer) ? 401 : 403;
            given().contentType("application/json")
                    .header("Authorization", bearer)
                    .header("Idempotency-Key", key)
                    .body(new CompletionResource.ConfirmCollectorCompletionDTO("001234"))
                    .when().post("/api/requests/req-001/confirm-collector")
                    .then().statusCode(expectedStatus);
        }
        assertEquals(CollectionRequest.Status.IN_PROGRESS,
                requestPort.findById("req-001").await().indefinitely().getStatus());
    }

    @Test
    void codeFormatAndIdempotencyKeyAreValidatedBeforeAttemptOrCompletion() {
        CollectionRequest request = activeRequest();
        requestPort.seed(request);

        given().contentType("application/json")
                .header("Authorization", "Bearer collector")
                .header("Idempotency-Key", "bad-key")
                .body(new CompletionResource.ConfirmCollectorCompletionDTO("001234"))
                .when().post("/api/requests/req-001/confirm-collector")
                .then().statusCode(400);
        given().contentType("application/json")
                .header("Authorization", "Bearer collector")
                .header("Idempotency-Key", "2fb0c41d-67c6-4727-a944-62b78b7f74b9")
                .body(new CompletionResource.ConfirmCollectorCompletionDTO("12345"))
                .when().post("/api/requests/req-001/confirm-collector")
                .then().statusCode(400);

        assertEquals(0, request.getCompletionCode().failedAttempts());
        assertEquals(CollectionRequest.Status.IN_PROGRESS, request.getStatus());
    }

    @Test
    void validButWrongCodeReturns422AndFifthAttemptReturns429() {
        CollectionRequest request = activeRequest();
        request.setGeneratorConfirmed(true);
        request.setCollectorConfirmed(true);
        requestPort.seed(request);
        String key = "3fb0c41d-67c6-4727-a944-62b78b7f74b9";

        for (int attempt = 1; attempt <= 5; attempt++) {
            given().contentType("application/json")
                    .header("Authorization", "Bearer collector")
                    .header("Idempotency-Key", key)
                    .body(new CompletionResource.ConfirmCollectorCompletionDTO("111111"))
                    .when().post("/api/requests/req-001/confirm-collector")
                    .then().statusCode(attempt < 5 ? 422 : 429)
                    .header("Retry-After", attempt < 5 ? org.hamcrest.Matchers.nullValue() : org.hamcrest.Matchers.notNullValue());
        }
        assertEquals(CollectionRequest.Status.IN_PROGRESS, request.getStatus());
        assertEquals(5, request.getCompletionCode().failedAttempts());
        assertEquals("collector-1", request.getSelectedCollectorId());
        assertEquals(Boolean.TRUE, request.getGeneratorConfirmed());
        assertEquals(Boolean.TRUE, request.getCollectorConfirmed());
        assertEquals(null, request.getCompletionEvidence());
    }

    private CollectionRequest activeRequest() {
        CollectionRequest request = new CollectionRequest("generator-1", "address-1", List.of("paper"), 10.0);
        request.setId("req-001");
        request.setSelectedCollectorId("collector-1");
        request.setStatus(CollectionRequest.Status.IN_PROGRESS);
        request.setCompletionCode(new CompletionCodeState(1, "cipher", "nonce", "test-key",
                "test-verifier:001234", LocalDateTime.now(), 0, null, null));
        return request;
    }
}
