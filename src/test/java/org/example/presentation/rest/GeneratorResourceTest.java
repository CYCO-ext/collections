package org.example.presentation.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
class GeneratorResourceTest {

    @Test
    void testCreateRequest() {
        String requestBody = """
                {
                    "generatorId": "gen-001",
                    "addressId": "addr-001",
                    "materialIds": ["mat-001", "mat-002"],
                    "weight": 100.0
                }
                """;

        given()
                .contentType("application/json")
                .body(requestBody)
        .when()
                .post("/api/generators/requests")
        .then()
                .statusCode(201);
    }

    @Test
    void testGetNearbyCollectors() {
        given()
        .when()
                .get("/api/generators/requests/req-001/collectors")
        .then()
                .statusCode(400);
    }
}

