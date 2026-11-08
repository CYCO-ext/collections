package org.example.presentation.rest;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteAssignment;
import org.example.application.deliveryman.DeliverymanModels.RouteAssignmentStatus;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.CompletionCodeState;
import org.example.infrastructure.repository.DeliveryRouteAssignmentRepository;
import org.example.infrastructure.repository.DeliveryRouteLogRepository;
import org.example.infrastructure.repository.MongoTestResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
class DeliverymanCompletionResourceTest {
    @Inject
    CompletionRequestPortFixture requestPort;

    @Inject
    DeliveryRouteAssignmentRepository assignmentRepository;

    @Inject
    DeliveryRouteLogRepository routeLogRepository;

    @BeforeEach
    void resetFixtures() {
        requestPort.clear();
        CompletionEventPortFixture.failCompletionEvent = false;
    }

    @Test
    void assignedDeliverymanConfirmsStopAndRetryAfterTerminalAssignmentIsIdempotent() {
        String requestId = "delivery-stop-" + UUID.randomUUID();
        DeliveryRouteAssignment assignment = seedAssignment(requestId, List.of(requestId));
        String key = UUID.randomUUID().toString();

        String completionId = given().contentType("application/json")
                .header("Authorization", "Bearer deliveryman")
                .header("Idempotency-Key", key)
                .body(new DeliverymanResource.ConfirmDeliveryStopDTO("001234"))
                .when().post(stopUrl(assignment, requestId))
                .then().statusCode(200)
                .header("Cache-Control", equalTo("no-store"))
                .body("requestId", equalTo(requestId))
                .body("assignment.deliverymanId", equalTo("deliveryman-1"))
                .body("assignment.completedCollectionRequestIds[0]", equalTo(requestId))
                .extract().path("completionId");

        DeliveryRouteAssignment completed = assignmentRepository.findById(assignment.id()).await().indefinitely();
        assertEquals("deliveryman-1", completed.deliverymanId());
        assertTrue(completed.completedCollectionRequestIds().contains(requestId));

        DeliveryRouteAssignment terminal = completed.finish(RouteAssignmentStatus.COMPLETED, LocalDateTime.now());
        assignmentRepository.update(terminal).await().indefinitely();
        given().contentType("application/json")
                .header("Authorization", "Bearer deliveryman")
                .header("Idempotency-Key", key)
                .body(new DeliverymanResource.ConfirmDeliveryStopDTO("001234"))
                .when().post(stopUrl(assignment, requestId))
                .then().statusCode(200).body("completionId", equalTo(completionId));
    }

    @Test
    void directAccessByAnotherDeliverymanIsForbiddenWithoutAttemptOrSuccessLog() {
        String requestId = "delivery-forbidden-" + UUID.randomUUID();
        DeliveryRouteAssignment assignment = seedAssignment(requestId, List.of(requestId));

        given().contentType("application/json")
                .header("Authorization", "Bearer other-deliveryman")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(new DeliverymanResource.ConfirmDeliveryStopDTO("001234"))
                .when().post(stopUrl(assignment, requestId))
                .then().statusCode(403).body("error.code", equalTo("FORBIDDEN"));

        assertEquals(0, requestPort.findById(requestId).await().indefinitely().getCompletionCode().failedAttempts());
        assertEquals(List.of(), routeLogRepository.findBySavedRouteIdAndVehicleIndex(
                assignment.savedRouteId(), assignment.vehicleIndex()).await().indefinitely());
    }

    @Test
    void unassignedStopIsForbiddenAndDoesNotConsumeCodeAttempt() {
        String requestId = "delivery-unassigned-" + UUID.randomUUID();
        DeliveryRouteAssignment assignment = seedAssignment(requestId, List.of("another-request"));

        given().contentType("application/json")
                .header("Authorization", "Bearer deliveryman")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(new DeliverymanResource.ConfirmDeliveryStopDTO("999999"))
                .when().post(stopUrl(assignment, requestId))
                .then().statusCode(403).body("error.code", equalTo("FORBIDDEN"));

        assertEquals(0, requestPort.findById(requestId).await().indefinitely().getCompletionCode().failedAttempts());
        assertEquals(List.of(), routeLogRepository.findBySavedRouteIdAndVehicleIndex(
                assignment.savedRouteId(), assignment.vehicleIndex()).await().indefinitely());
    }

    @Test
    void malformedAndWrongCodesDoNotRegisterSuccessfulStop() {
        String requestId = "delivery-invalid-" + UUID.randomUUID();
        DeliveryRouteAssignment assignment = seedAssignment(requestId, List.of(requestId));
        String key = UUID.randomUUID().toString();

        given().contentType("application/json")
                .header("Authorization", "Bearer deliveryman")
                .header("Idempotency-Key", key)
                .body(new DeliverymanResource.ConfirmDeliveryStopDTO(null))
                .when().post(stopUrl(assignment, requestId))
                .then().statusCode(400);

        for (int attempt = 1; attempt <= 5; attempt++) {
            given().contentType("application/json")
                    .header("Authorization", "Bearer deliveryman")
                    .header("Idempotency-Key", key)
                    .body(new DeliverymanResource.ConfirmDeliveryStopDTO("654321"))
                    .when().post(stopUrl(assignment, requestId))
                    .then().statusCode(attempt == 5 ? 429 : 422);
        }

        CollectionRequest request = requestPort.findById(requestId).await().indefinitely();
        assertEquals(CollectionRequest.Status.IN_PROGRESS, request.getStatus());
        assertEquals(5, request.getCompletionCode().failedAttempts());
        assertEquals(List.of(), assignmentRepository.findById(assignment.id()).await().indefinitely()
                .completedCollectionRequestIds());
        assertEquals(List.of(), routeLogRepository.findBySavedRouteIdAndVehicleIndex(
                assignment.savedRouteId(), assignment.vehicleIndex()).await().indefinitely());
    }

    @Test
    void persistedCompletionWithPendingEffectsReturns202AndCompletionId() {
        String requestId = "delivery-pending-" + UUID.randomUUID();
        DeliveryRouteAssignment assignment = seedAssignment(requestId, List.of(requestId));
        CompletionEventPortFixture.failCompletionEvent = true;

        given().contentType("application/json")
                .header("Authorization", "Bearer deliveryman")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .body(new DeliverymanResource.ConfirmDeliveryStopDTO("001234"))
                .when().post(stopUrl(assignment, requestId))
                .then().statusCode(202)
                .header("Cache-Control", equalTo("no-store"))
                .body("requestId", equalTo(requestId))
                .body("reconciliationPending", equalTo(true))
                .body("completionId", org.hamcrest.Matchers.notNullValue());

        CollectionRequest request = requestPort.findById(requestId).await().indefinitely();
        assertEquals(CollectionRequest.Status.COMPLETED, request.getStatus());
        assertTrue(!request.getCompletionEvidence().pendingEffects().isEmpty());
        assertTrue(assignmentRepository.findById(assignment.id()).await().indefinitely()
                .completedCollectionRequestIds().contains(requestId));
    }

    private DeliveryRouteAssignment seedAssignment(String requestId, List<String> assignedStops) {
        requestPort.seed(activeRequest(requestId));
        LocalDateTime now = LocalDateTime.now();
        DeliveryRouteAssignment assignment = DeliveryRouteAssignment.create(
                "collector-1", "deliveryman-1", "saved-" + UUID.randomUUID(), 0, assignedStops, now).start(now);
        assignmentRepository.save(assignment).await().indefinitely();
        return assignment;
    }

    private CollectionRequest activeRequest(String requestId) {
        CollectionRequest request = new CollectionRequest("generator-1", "address-1", List.of("paper"), 10.0);
        request.setId(requestId);
        request.setSelectedCollectorId("collector-1");
        request.setStatus(CollectionRequest.Status.IN_PROGRESS);
        request.setCompletionCode(new CompletionCodeState(1, "cipher", "nonce", "test-key",
                "test-verifier:001234", LocalDateTime.now(), 0, null, null));
        return request;
    }

    private String stopUrl(DeliveryRouteAssignment assignment, String requestId) {
        return "/api/deliverymen/" + assignment.deliverymanId() + "/assignments/" + assignment.id()
                + "/stops/" + requestId + "/complete";
    }
}
