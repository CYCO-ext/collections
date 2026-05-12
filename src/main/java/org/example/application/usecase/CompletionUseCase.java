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
public class CompletionUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CompletionUseCase.class);

    @Inject
    CollectionRequestPort collectionRequestPort;

    @Inject
    EventPort eventPort;

    @Inject
    CloseSavedRoutesUseCase closeSavedRoutesUseCase;

    public Uni<Void> confirmGeneratorCompletion(String requestId) {
        LOG.info("Generator confirming completion for request: {}", requestId);

        return collectionRequestPort.findById(requestId)
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Request not found: " + requestId))
                .flatMap(request -> {
                    if (!CollectionRequest.Status.IN_PROGRESS.equals(request.getStatus())) {
                        return Uni.createFrom().failure(new IllegalStateException("Request is not IN_PROGRESS"));
                    }
                    request.setGeneratorConfirmed(true);
                    return collectionRequestPort.update(request)
                            .flatMap(it -> tryMarkCompleted(request))
                            .invoke(() -> LOG.info("Generator confirmed completion for request: {}", requestId));
                });
    }

    public Uni<Void> confirmCollectorCompletion(String requestId) {
        LOG.info("Collector confirming completion for request: {}", requestId);

        return collectionRequestPort.findById(requestId)
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Request not found: " + requestId))
                .flatMap(request -> {
                    if (!CollectionRequest.Status.IN_PROGRESS.equals(request.getStatus())) {
                        return Uni.createFrom().failure(new IllegalStateException("Request is not IN_PROGRESS"));
                    }
                    request.setCollectorConfirmed(true);
                    return collectionRequestPort.update(request)
                            .flatMap(it -> tryMarkCompleted(request))
                            .invoke(() -> LOG.info("Collector confirmed completion for request: {}", requestId));
                });
    }

    private Uni<Void> tryMarkCompleted(CollectionRequest request) {
        if (request.canMarkCompleted()) {
            request.setStatus(CollectionRequest.Status.COMPLETED);
            return collectionRequestPort.update(request)
                    .flatMap(it -> publishCompletionEvent(request))
                    .flatMap(it -> closeSavedRoutesUseCase.closeRoutesContaining(request.getId()));
        }
        return Uni.createFrom().nullItem().replaceWithVoid();
    }

    private Uni<Void> publishCompletionEvent(CollectionRequest request) {
        CollectionEvent event = new CollectionEvent();
        event.setEventType("COLLECTION_COMPLETED");
        event.setRequestId(request.getId());
        event.setGeneratorId(request.getGeneratorId());
        event.setCollectorId(request.getSelectedCollectorId());
        event.setStatus(request.getStatus().toString());
        event.setTimestamp(Instant.now().toString());

        return eventPort.publishCollectionEvent(event);
    }
}
