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
public class MarkCollectorOnTheWayUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(MarkCollectorOnTheWayUseCase.class);
    private static final String EVENT_TYPE = "COLLECTION_ON_THE_WAY";

    @Inject
    CollectionRequestPort collectionRequestPort;

    @Inject
    EventPort eventPort;

    @Inject
    CollectionStatusNotificationUseCase notificationUseCase;

    public Uni<Void> markOnTheWay(String requestId, String collectorId) {
        String normalizedRequestId = normalizeRequired(requestId, "request id is required");
        String normalizedCollectorId = normalizeRequired(collectorId, "collector id is required");
        LOG.info("Collector marking request on the way: {} collectorId={}", normalizedRequestId, normalizedCollectorId);

        return collectionRequestPort.findById(normalizedRequestId)
                .onItem().ifNull().failWith(() -> new CollectionRequestNotFoundException(normalizedRequestId))
                .flatMap(request -> {
                    validate(request, normalizedCollectorId);
                    request.setStatus(CollectionRequest.Status.ON_THE_WAY);
                    return collectionRequestPort.update(request)
                            .flatMap(it -> publishOnTheWayEvent(request))
                            .flatMap(it -> notificationUseCase.notifyGenerator(request, EVENT_TYPE))
                            .invoke(() -> LOG.info("Collection request marked on the way: {}", normalizedRequestId));
                });
    }

    private void validate(CollectionRequest request, String collectorId) {
        if (!Objects.equals(request.getSelectedCollectorId(), collectorId)) {
            throw new CollectionCancellationForbiddenException("Collector cannot update this request");
        }
        if (CollectionRequest.Status.ON_THE_WAY.equals(request.getStatus())) {
            throw new CollectionCancellationConflictException("Request is already on the way");
        }
        if (!CollectionRequest.Status.IN_PROGRESS.equals(request.getStatus())) {
            throw new CollectionCancellationConflictException("Request cannot be marked on the way from status: " + request.getStatus());
        }
    }

    private Uni<Void> publishOnTheWayEvent(CollectionRequest request) {
        CollectionEvent event = new CollectionEvent();
        event.setEventType(EVENT_TYPE);
        event.setRequestId(request.getId());
        event.setGeneratorId(request.getGeneratorId());
        event.setCollectorId(request.getSelectedCollectorId());
        event.setStatus(request.getStatus().toString());
        event.setTimestamp(Instant.now().toString());
        event.setActorType("COLLECTOR");
        event.setActorId(request.getSelectedCollectorId());
        return eventPort.publishCollectionEvent(event);
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
