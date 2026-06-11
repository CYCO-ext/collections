package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.EventPort;
import org.example.domain.entity.CollectionRequest;
import org.example.infrastructure.event.CollectionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;

@Singleton
public class CancelCollectionRequestUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CancelCollectionRequestUseCase.class);
    private static final String ACTOR_GENERATOR = "GENERATOR";
    private static final String ACTOR_COLLECTOR = "COLLECTOR";

    @Inject
    CollectionRequestPort collectionRequestPort;

    @Inject
    EventPort eventPort;

    @Inject
    CollectionStatusNotificationUseCase notificationUseCase;

    public Uni<Void> cancelByGenerator(String requestId, String generatorId) {
        String normalizedRequestId = normalizeRequired(requestId, "request id is required");
        String normalizedGeneratorId = normalizeRequired(generatorId, "generator id is required");
        LOG.info("Generator cancelling request: {} generatorId={}", normalizedRequestId, normalizedGeneratorId);
        return cancel(normalizedRequestId, ACTOR_GENERATOR, normalizedGeneratorId);
    }

    public Uni<Void> cancelByCollector(String requestId, String collectorId) {
        String normalizedRequestId = normalizeRequired(requestId, "request id is required");
        String normalizedCollectorId = normalizeRequired(collectorId, "collector id is required");
        LOG.info("Collector cancelling request: {} collectorId={}", normalizedRequestId, normalizedCollectorId);
        return cancel(normalizedRequestId, ACTOR_COLLECTOR, normalizedCollectorId);
    }

    private Uni<Void> cancel(String requestId, String actorType, String actorId) {
        return collectionRequestPort.findById(requestId)
                .onItem().ifNull().failWith(() -> new CollectionRequestNotFoundException(requestId))
                .flatMap(request -> {
                    validateActor(request, actorType, actorId);
                    validateStatus(request);
                    request.setStatus(CollectionRequest.Status.CANCELLED);
                    return collectionRequestPort.update(request)
                            .flatMap(it -> publishCancellationEvent(request, actorType, actorId))
                            .flatMap(it -> notificationUseCase.notifyGenerator(request, "COLLECTION_CANCELLED"))
                            .invoke(() -> LOG.info("Collection request cancelled: {} actorType={} actorId={}", requestId, actorType, actorId));
                });
    }

    private void validateActor(CollectionRequest request, String actorType, String actorId) {
        if (ACTOR_GENERATOR.equals(actorType) && !Objects.equals(request.getGeneratorId(), actorId)) {
            throw new CollectionCancellationForbiddenException("Generator cannot cancel this request");
        }
        if (ACTOR_COLLECTOR.equals(actorType) && !Objects.equals(request.getSelectedCollectorId(), actorId)) {
            throw new CollectionCancellationForbiddenException("Collector cannot cancel this request");
        }
    }

    private void validateStatus(CollectionRequest request) {
        if (CollectionRequest.Status.COMPLETED.equals(request.getStatus())) {
            throw new CollectionCancellationConflictException("Request is already completed");
        }
        if (CollectionRequest.Status.CANCELLED.equals(request.getStatus())) {
            throw new CollectionCancellationConflictException("Request is already cancelled");
        }
        if (!request.canCancel()) {
            throw new CollectionCancellationConflictException("Request cannot be cancelled from status: " + request.getStatus());
        }
    }

    private Uni<Void> publishCancellationEvent(CollectionRequest request, String actorType, String actorId) {
        CollectionEvent event = new CollectionEvent();
        event.setEventType("COLLECTION_CANCELLED");
        event.setRequestId(request.getId());
        event.setGeneratorId(request.getGeneratorId());
        event.setCollectorId(request.getSelectedCollectorId());
        event.setStatus(request.getStatus().toString());
        event.setTimestamp(Instant.now().toString());
        event.setActorType(actorType);
        event.setActorId(actorId);
        return eventPort.publishCollectionEvent(event);
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
