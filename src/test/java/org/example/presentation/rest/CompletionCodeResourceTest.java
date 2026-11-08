package org.example.presentation.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.CompletionCodeState;
import org.example.domain.entity.CompletionEvidence;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class CompletionCodeResourceTest {
    @Inject
    ObjectMapper objectMapper;

    @Test
    void ownerReceivesCodeAndNoStoreHeader() {
        given()
                .contentType("application/json")
                .header("Authorization", "Bearer owner")
        .when()
                .post("/api/requests/request-1/completion-code")
        .then()
                .statusCode(200)
                .header("Cache-Control", "no-store")
                .body("requestId", org.hamcrest.Matchers.equalTo("request-1"))
                .body("code", org.hamcrest.Matchers.equalTo("001234"));
    }

    @Test
    void missingOrInvalidAuthenticationReturns401() {
        given().contentType("application/json").when().post("/api/requests/request-1/completion-code").then().statusCode(401);
        given().contentType("application/json").header("Authorization", "Bearer invalid")
                .when().post("/api/requests/request-1/completion-code").then().statusCode(401);
    }

    @Test
    void otherProfileReceives403() {
        given().contentType("application/json").header("Authorization", "Bearer forbidden")
                .when().post("/api/requests/request-1/completion-code")
                .then().statusCode(403).body("error.code", org.hamcrest.Matchers.equalTo("FORBIDDEN"));
    }

    @Test
    void ineligibleCollectionReturns409AndIdentityOutageReturns503() {
        given().contentType("application/json").header("Authorization", "Bearer inactive")
                .when().post("/api/requests/request-1/completion-code")
                .then().statusCode(409)
                .body("error.code", org.hamcrest.Matchers.equalTo("COLLECTION_NOT_ACTIVE"));
        given().contentType("application/json").header("Authorization", "Bearer unavailable")
                .when().post("/api/requests/request-1/completion-code")
                .then().statusCode(503)
                .body("error.code", org.hamcrest.Matchers.equalTo("SERVICE_UNAVAILABLE"));
    }

    @Test
    void commonCollectionJsonDoesNotExposeCompletionSecretsOrEvidence() throws Exception {
        CollectionRequest request = new CollectionRequest("generator-1", "address-1", List.of("paper"), 1.0);
        request.setCompletionCode(new CompletionCodeState(1, "encrypted-secret", "nonce-secret", "key-1",
                "verifier-secret", LocalDateTime.now(), 0, null, null));
        request.setCompletionEvidence(new CompletionEvidence("completion-secret", "user-1", "GENERATOR",
                "generator-1", LocalDateTime.now(), 1, "idempotency-secret", "digest-secret"));

        String json = objectMapper.writeValueAsString(request);

        assertFalse(json.contains("encrypted-secret"));
        assertFalse(json.contains("verifier-secret"));
        assertFalse(json.contains("completion-secret"));
        assertFalse(json.contains("revision"));
        assertTrue(json.contains("generatorId"));
    }
}
