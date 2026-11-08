package org.example.application.usecase;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteAssignment;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteLog;
import org.example.application.deliveryman.DeliverymanModels.RouteLogEventType;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.DeliveryRouteAssignmentPort;
import org.example.application.port.out.DeliveryRouteLogPort;
import org.example.application.port.out.EventPort;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.CompletionEvidence;
import org.example.infrastructure.event.CollectionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class CompletionEffectsUseCase {
    private static final Logger LOG = LoggerFactory.getLogger(CompletionEffectsUseCase.class);
    private static final String ROUTE_PROGRESS = "ROUTE_PROGRESS";
    private static final String ROUTE_CLOSE = "ROUTE_CLOSE";
    private static final String COLLECTION_EVENT = "COLLECTION_EVENT";
    private static final String NOTIFICATION = "NOTIFICATION";

    @Inject
    CollectionRequestPort collectionRequestPort;

    @Inject
    DeliveryRouteAssignmentPort assignmentPort;

    @Inject
    DeliveryRouteLogPort routeLogPort;

    @Inject
    CloseSavedRoutesUseCase closeSavedRoutesUseCase;

    @Inject
    EventPort eventPort;

    @Inject
    CollectionStatusNotificationUseCase notificationUseCase;

    @Scheduled(every = "30s", delayed = "10s")
    void scheduledReconciliation() {
        processPendingEffects(100).subscribe().with(
                ignored -> { },
                failure -> LOG.error("Failed to reconcile pending collection completion effects", failure));
    }

    public Uni<Void> processPendingEffects(int limit) {
        return collectionRequestPort.findPendingCompletionEffects(limit)
                .flatMap(requests -> processRequests(requests == null ? List.of() : requests, 0));
    }

    public Uni<Void> processCompletion(String requestId, String completionId) {
        if (requestId == null || requestId.isBlank() || completionId == null || completionId.isBlank()) {
            return Uni.createFrom().voidItem();
        }
        return collectionRequestPort.findById(requestId)
                .flatMap(request -> {
                    if (request == null || !CollectionRequest.Status.COMPLETED.equals(request.getStatus()) ||
                            request.getCompletionEvidence() == null ||
                            !completionId.equals(request.getCompletionEvidence().completionId())) {
                        return Uni.createFrom().voidItem();
                    }
                    return processRequest(request, 0);
                });
    }

    private Uni<Void> processRequests(List<CollectionRequest> requests, int index) {
        if (index >= requests.size()) return Uni.createFrom().voidItem();
        CollectionRequest request = requests.get(index);
        if (request == null || request.getCompletionEvidence() == null) {
            return processRequests(requests, index + 1);
        }
        return processRequest(request, 0).flatMap(ignored -> processRequests(requests, index + 1));
    }

    private Uni<Void> processRequest(CollectionRequest request, int index) {
        CompletionEvidence evidence = request.getCompletionEvidence();
        List<String> pendingEffects = evidence.pendingEffects();
        if (index >= pendingEffects.size()) return Uni.createFrom().voidItem();
        String effect = pendingEffects.get(index);
        return executeEffect(request, evidence, effect)
                .flatMap(ignored -> collectionRequestPort.markCompletionEffectProcessed(
                        request.getId(), evidence.completionId(), effect))
                .replaceWithVoid()
                .flatMap(ignored -> {
                    CompletionEvidence current = request.getCompletionEvidence();
                    request.setCompletionEvidence(new CompletionEvidence(
                            current.completionId(), current.actorUserId(), current.actorRole(), current.actorProfileId(),
                            current.validatedAt(), current.codeVersion(), current.idempotencyKey(), current.payloadDigest(),
                            current.pendingEffects().stream().filter(item -> !item.equals(effect)).toList()));
                    return processRequest(request, index);
                });
    }

    private Uni<Void> executeEffect(CollectionRequest request, CompletionEvidence evidence, String effect) {
        return switch (effect) {
            case ROUTE_PROGRESS -> reconcileRouteProgress(request, evidence);
            case ROUTE_CLOSE -> closeSavedRoutesUseCase.closeRoutesContaining(request.getId());
            case COLLECTION_EVENT -> publishCompletionEvent(request, evidence);
            case NOTIFICATION -> notificationUseCase.notifyGeneratorWithCompletionId(request, evidence.completionId());
            default -> Uni.createFrom().failure(new IllegalStateException("Unknown completion effect: " + effect));
        };
    }

    private Uni<Void> reconcileRouteProgress(CollectionRequest request, CompletionEvidence evidence) {
        return assignmentPort.findByCollectionRequestId(request.getId())
                .flatMap(assignments -> reconcileAssignments(
                        assignments == null ? List.of() : assignments, request, evidence, 0));
    }

    private Uni<Void> reconcileAssignments(
            List<DeliveryRouteAssignment> assignments,
            CollectionRequest request,
            CompletionEvidence evidence,
            int index
    ) {
        if (index >= assignments.size()) return Uni.createFrom().voidItem();
        DeliveryRouteAssignment assignment = assignments.get(index);
        return assignmentPort.markStopCompleted(assignment.id(), request.getId(), evidence.validatedAt())
                .flatMap(matched -> {
                    if (!matched) return Uni.createFrom().voidItem();
                    String logId = evidence.completionId() + ":stop:" + request.getId();
                    DeliveryRouteLog log = new DeliveryRouteLog(
                            logId,
                            assignment.savedRouteId(),
                            assignment.vehicleIndex(),
                            assignment.collectorId(),
                            assignment.deliverymanId(),
                            request.getId(),
                            RouteLogEventType.STOP_COMPLETED,
                            null,
                            null,
                            evidence.validatedAt());
                    return routeLogPort.append(log);
                })
                .flatMap(ignored -> reconcileAssignments(assignments, request, evidence, index + 1));
    }

    private Uni<Void> publishCompletionEvent(CollectionRequest request, CompletionEvidence evidence) {
        CollectionEvent event = new CollectionEvent();
        event.setEventType("COLLECTION_COMPLETED");
        event.setRequestId(request.getId());
        event.setGeneratorId(request.getGeneratorId());
        event.setCollectorId(request.getSelectedCollectorId());
        event.setStatus(CollectionRequest.Status.COMPLETED.toString());
        event.setTimestamp(evidence.validatedAt().toString());
        event.setActorType(evidence.actorRole());
        event.setActorId(evidence.actorProfileId());
        event.setCompletionId(evidence.completionId());
        return eventPort.publishCollectionEvent(event);
    }
}
