package org.example.presentation.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class CompletionResourceTest {

    @Test
    void testConfirmGeneratorCompletion() {
        given()
                .contentType("application/json")
        .when()
                .post("/api/requests/req-001/confirm-generator")
        .then()
                .statusCode(400); // Expected to fail without request
    }

    @Test
    void testConfirmCollectorCompletion() {
        given()
                .contentType("application/json")
        .when()
                .post("/api/requests/req-001/confirm-collector")
        .then()
                .statusCode(400); // Expected to fail without request
    }
}
