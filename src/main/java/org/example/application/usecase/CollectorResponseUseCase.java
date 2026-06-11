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

@Singleton
public class CollectorResponseUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CollectorResponseUseCase.class);

    @Inject
    CollectionRequestPort collectionRequestPort;

    @Inject
    EventPort eventPort;

    @Inject
    CollectionStatusNotificationUseCase notificationUseCase;

    public Uni<Void> acceptRequest(String requestId) {
        LOG.info("Collector accepting request: {}", requestId);

        return collectionRequestPort.findById(requestId)
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Request not found: " + requestId))
                .flatMap(request -> {
                    if (!CollectionRequest.Status.PENDING.equals(request.getStatus())) {
                        return Uni.createFrom().failure(new IllegalStateException("Request is not PENDING"));
                    }
                    request.setStatus(CollectionRequest.Status.IN_PROGRESS);
                    return collectionRequestPort.update(request)
                            .flatMap(it -> publishAcceptanceEvent(request))
                            .flatMap(it -> notificationUseCase.notifyGenerator(request, "COLLECTION_ACCEPTED"))
                            .invoke(() -> LOG.info("Collection started for request: {}", requestId));
                });
    }

    public Uni<Void> rejectRequest(String requestId) {
        LOG.info("Collector rejecting request: {}", requestId);

        return collectionRequestPort.findById(requestId)
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Request not found: " + requestId))
                .flatMap(request -> {
                    if (!CollectionRequest.Status.PENDING.equals(request.getStatus())) {
                        return Uni.createFrom().failure(new IllegalStateException("Request is not PENDING"));
                    }
                    request.setSelectedCollectorId(null);
                    return collectionRequestPort.update(request)
                            .flatMap(it -> publishRejectionEvent(request))
                            .flatMap(it -> notificationUseCase.notifyGenerator(request, "COLLECTION_REJECTED"))
                            .invoke(() -> LOG.info("Request rejected, available for other collectors: {}", requestId));
                });
    }

    private Uni<Void> publishAcceptanceEvent(CollectionRequest request) {
        CollectionEvent event = new CollectionEvent();
        event.setEventType("COLLECTION_ACCEPTED");
        event.setRequestId(request.getId());
        event.setGeneratorId(request.getGeneratorId());
        event.setCollectorId(request.getSelectedCollectorId());
        event.setStatus(request.getStatus().toString());
        event.setTimestamp(Instant.now().toString());

        return eventPort.publishCollectionEvent(event);
    }

    private Uni<Void> publishRejectionEvent(CollectionRequest request) {
        CollectionEvent event = new CollectionEvent();
        event.setEventType("COLLECTION_REJECTED");
        event.setRequestId(request.getId());
        event.setGeneratorId(request.getGeneratorId());
        event.setCollectorId(request.getSelectedCollectorId());
        event.setStatus(request.getStatus().toString());
        event.setTimestamp(Instant.now().toString());

        return eventPort.publishCollectionEvent(event);
    }
}

