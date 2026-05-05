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
public class CollectorSelectionUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CollectorSelectionUseCase.class);

    @Inject
    CollectionRequestPort collectionRequestPort;

    @Inject
    EventPort eventPort;

    public Uni<Void> selectCollector(String requestId, String collectorId) {
        LOG.info("Selecting collector: {} for request: {}", collectorId, requestId);

        return collectionRequestPort.findById(requestId)
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Request not found: " + requestId))
                .flatMap(request -> {
                    request.setSelectedCollectorId(collectorId);
                    return collectionRequestPort.update(request)
                            .flatMap(it -> publishSelectionEvent(request, collectorId))
                            .invoke(() -> LOG.info("Collector selected for request: {}", requestId));
                });
    }

    private Uni<Void> publishSelectionEvent(CollectionRequest request, String collectorId) {
        CollectionEvent event = new CollectionEvent();
        event.setEventType("COLLECTOR_SELECTED");
        event.setRequestId(request.getId());
        event.setGeneratorId(request.getGeneratorId());
        event.setCollectorId(collectorId);
        event.setStatus(request.getStatus().toString());
        event.setTimestamp(Instant.now().toString());

        return eventPort.publishCollectionEvent(event);
    }
}

