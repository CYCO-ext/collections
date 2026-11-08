package org.example.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteAssignment;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteLog;
import org.example.application.deliveryman.DeliverymanModels.RouteAssignmentStatus;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.DeliveryRouteAssignmentPort;
import org.example.application.port.out.DeliveryRouteLogPort;
import org.example.application.port.out.EventPort;
import org.example.application.usecase.SearchCollectionsUseCase.CollectionSearchQuery;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.CompletionCodeState;
import org.example.domain.entity.CompletionEvidence;
import org.example.infrastructure.event.CollectionEvent;
import org.example.infrastructure.repository.CollectionRequestRepository;
import org.example.infrastructure.repository.DeliveryRouteAssignmentRepository;
import org.example.infrastructure.repository.DeliveryRouteLogRepository;
import org.example.infrastructure.repository.MongoTestResource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
class CompletionEffectsRestartTest {
    private static final String REQUEST_ID = "effect-restart-request";
    private static final String COMPLETION_ID = "effect-restart-completion";

    @Inject
    CollectionRequestRepository repository;

    @Inject
    DeliveryRouteAssignmentRepository assignmentRepository;

    @Inject
    DeliveryRouteLogRepository routeLogRepository;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void freshProcessorResumesMongoPendingEffectsAfterCrashAndDeduplicatesProgressAndLog() throws Exception {
        CollectionRequest request = completedRequestWithPendingEffects();
        repository.save(request).await().indefinitely();
        DeliveryRouteAssignment seededAssignment = assignment();
        assignmentRepository.save(seededAssignment).await().indefinitely();
        MongoCollectionRequestPort mongoPort = new MongoCollectionRequestPort(repository);
        MongoAssignmentPort assignments = new MongoAssignmentPort(assignmentRepository);
        FailOnceRouteLogPort logs = new FailOnceRouteLogPort(routeLogRepository);
        RecordingEventPort events = new RecordingEventPort();
        CompletionEffectsUseCase firstProcessor = processor(mongoPort, assignments, logs, events);

        assertThrows(IllegalStateException.class,
                () -> firstProcessor.processPendingEffects(20).await().indefinitely());
        CollectionRequest afterInterruption = repository.findById(REQUEST_ID).await().indefinitely();
        assertEquals(List.of("ROUTE_PROGRESS", "ROUTE_CLOSE", "COLLECTION_EVENT", "NOTIFICATION"),
                afterInterruption.getCompletionEvidence().pendingEffects());
        assertEquals(List.of(REQUEST_ID), assignmentRepository.findById(seededAssignment.id()).await().indefinitely()
                .completedCollectionRequestIds());

        CompletionEffectsUseCase restartedProcessor = processor(mongoPort, assignments, logs, events);
        restartedProcessor.processPendingEffects(20).await().indefinitely();

        CollectionRequest reconciled = repository.findById(REQUEST_ID).await().indefinitely();
        assertEquals(List.of(), reconciled.getCompletionEvidence().pendingEffects());
        assertEquals(List.of(REQUEST_ID), assignmentRepository.findById(seededAssignment.id()).await().indefinitely()
                .completedCollectionRequestIds());
        List<DeliveryRouteLog> persistedLogs = routeLogRepository.findBySavedRouteIdAndVehicleIndex(
                seededAssignment.savedRouteId(), seededAssignment.vehicleIndex()).await().indefinitely();
        assertEquals(1, persistedLogs.size());
        assertEquals(COMPLETION_ID + ":stop:" + REQUEST_ID, persistedLogs.getFirst().id());
        assertEquals(1, events.events.size());
        assertEquals(COMPLETION_ID, events.events.getFirst().getCompletionId());
        String eventPayload = objectMapper.writeValueAsString(events.events.getFirst());
        assertFalse(eventPayload.contains("SECRET_CODE_MUST_NOT_APPEAR"));
        assertFalse(eventPayload.contains("code"));

        CompletionEffectsUseCase afterRestartRetry = processor(mongoPort, assignments, logs, events);
        afterRestartRetry.processPendingEffects(20).await().indefinitely();
        assertEquals(1, routeLogRepository.findBySavedRouteIdAndVehicleIndex(
                seededAssignment.savedRouteId(), seededAssignment.vehicleIndex()).await().indefinitely().size());
        assertEquals(1, events.events.size());
        assertEquals(2, assignments.markCalls);
    }

    private CompletionEffectsUseCase processor(
            CollectionRequestPort collectionRequests,
            MongoAssignmentPort assignments,
            FailOnceRouteLogPort logs,
            RecordingEventPort events
    ) {
        CompletionEffectsUseCase useCase = new CompletionEffectsUseCase();
        useCase.collectionRequestPort = collectionRequests;
        useCase.assignmentPort = assignments;
        useCase.routeLogPort = logs;
        CloseSavedRoutesUseCase closeRoutes = mock(CloseSavedRoutesUseCase.class);
        when(closeRoutes.closeRoutesContaining(anyString())).thenReturn(Uni.createFrom().voidItem());
        useCase.closeSavedRoutesUseCase = closeRoutes;
        useCase.eventPort = events;
        CollectionStatusNotificationUseCase notification = mock(CollectionStatusNotificationUseCase.class);
        when(notification.notifyGeneratorWithCompletionId(org.mockito.ArgumentMatchers.any(), anyString()))
                .thenReturn(Uni.createFrom().voidItem());
        useCase.notificationUseCase = notification;
        return useCase;
    }

    private CollectionRequest completedRequestWithPendingEffects() {
        CollectionRequest request = new CollectionRequest("generator-1", "address-1", List.of("paper"), 10.0);
        request.setId(REQUEST_ID);
        request.setSelectedCollectorId("collector-1");
        request.setStatus(CollectionRequest.Status.COMPLETED);
        LocalDateTime now = LocalDateTime.now();
        request.setCompletionCode(new CompletionCodeState(1, "SECRET_CODE_MUST_NOT_APPEAR", "nonce", "key-1", "verifier",
                now.minusMinutes(1), 0, null, now));
        request.setCompletionEvidence(new CompletionEvidence(COMPLETION_ID, "user-1", "DELIVERYMAN", "driver-1",
                now, 1, "86b9a51c-4755-49d3-8d50-e20bb4a58ec5", "payload-hmac",
                List.of("ROUTE_PROGRESS", "ROUTE_CLOSE", "COLLECTION_EVENT", "NOTIFICATION")));
        request.setCollectorConfirmed(true);
        return request;
    }

    private DeliveryRouteAssignment assignment() {
        return new DeliveryRouteAssignment("assignment-1", "collector-1", "driver-1", "saved-route-1", 0,
                RouteAssignmentStatus.IN_PROGRESS, List.of(REQUEST_ID), List.of(), LocalDateTime.now(),
                LocalDateTime.now(), null, LocalDateTime.now());
    }

    private static final class MongoCollectionRequestPort implements CollectionRequestPort {
        private final CollectionRequestRepository repository;
        private MongoCollectionRequestPort(CollectionRequestRepository repository) { this.repository = repository; }
        @Override public Uni<Void> save(CollectionRequest request) { return repository.save(request); }
        @Override public Uni<Void> update(CollectionRequest request) { return repository.update(request); }
        @Override public Uni<CollectionRequest> findById(String id) { return repository.findById(id); }
        @Override public Uni<List<CollectionRequest>> findByGeneratorId(String id) { return repository.findByGeneratorId(id); }
        @Override public Uni<List<CollectionRequest>> findByStatus(String status) { return repository.findByStatus(status); }
        @Override public Uni<List<CollectionRequest>> search(CollectionSearchQuery query) { return repository.search(query); }
        @Override public Uni<List<CollectionRequest>> findByIds(List<String> ids) { return repository.findByIds(ids); }
        @Override public Uni<List<CollectionRequest>> findInProgress(int limit) { return repository.findInProgress(limit); }
        @Override public Uni<List<CollectionRequest>> findBySelectedCollectorId(String id) { return repository.findBySelectedCollectorId(id); }
        @Override public Uni<Boolean> issueCompletionCodeIfAbsent(String id, CompletionCodeState code) { return repository.issueCompletionCodeIfAbsent(id, code); }
        @Override public Uni<Boolean> incrementCompletionCodeAttempts(String id, int version, LocalDateTime now,
                LocalDateTime blockedUntil, int maxAttempts) {
            return repository.incrementCompletionCodeAttempts(id, version, now, blockedUntil, maxAttempts);
        }
        @Override public Uni<Boolean> completeWithCode(String id, int version, CompletionEvidence evidence) {
            return repository.completeWithCode(id, version, evidence);
        }
        @Override public Uni<List<CollectionRequest>> findPendingCompletionEffects(int limit) {
            return repository.findPendingCompletionEffects(limit);
        }
        @Override public Uni<Boolean> markCompletionEffectProcessed(String id, String completionId, String effect) {
            return repository.markCompletionEffectProcessed(id, completionId, effect);
        }
    }

    private static final class MongoAssignmentPort implements DeliveryRouteAssignmentPort {
        private final DeliveryRouteAssignmentRepository repository;
        private int markCalls;
        private MongoAssignmentPort(DeliveryRouteAssignmentRepository repository) { this.repository = repository; }
        @Override public Uni<Void> save(DeliveryRouteAssignment assignment) { return repository.save(assignment); }
        @Override public Uni<Void> update(DeliveryRouteAssignment assignment) { return repository.update(assignment); }
        @Override public Uni<DeliveryRouteAssignment> findById(String id) { return repository.findById(id); }
        @Override public Uni<DeliveryRouteAssignment> findOpenBySavedRouteIdAndVehicleIndex(String id, int index) {
            return repository.findOpenBySavedRouteIdAndVehicleIndex(id, index);
        }
        @Override public Uni<List<DeliveryRouteAssignment>> findByDeliverymanIdAndStatuses(String id, List<RouteAssignmentStatus> statuses) {
            return repository.findByDeliverymanIdAndStatuses(id, statuses);
        }
        @Override public Uni<List<DeliveryRouteAssignment>> findByCollectionRequestId(String id) {
            return repository.findByCollectionRequestId(id);
        }
        @Override public Uni<Boolean> markStopCompleted(String assignmentId, String requestId, LocalDateTime completedAt) {
            markCalls++;
            return repository.markStopCompleted(assignmentId, requestId, completedAt);
        }
    }

    private static final class FailOnceRouteLogPort implements DeliveryRouteLogPort {
        private final DeliveryRouteLogRepository repository;
        private boolean failFirstAppend = true;
        private FailOnceRouteLogPort(DeliveryRouteLogRepository repository) { this.repository = repository; }
        @Override public Uni<Void> append(DeliveryRouteLog log) {
            if (failFirstAppend) {
                failFirstAppend = false;
                return Uni.createFrom().failure(new IllegalStateException("simulated crash before log persistence"));
            }
            return repository.append(log);
        }
        @Override public Uni<List<DeliveryRouteLog>> findBySavedRouteIdAndVehicleIndex(String id, int index) {
            return repository.findBySavedRouteIdAndVehicleIndex(id, index);
        }
    }

    private static final class RecordingEventPort implements EventPort {
        private final List<CollectionEvent> events = new ArrayList<>();
        @Override public Uni<Void> publishCollectionEvent(CollectionEvent event) {
            events.add(event);
            return Uni.createFrom().voidItem();
        }
    }
}
