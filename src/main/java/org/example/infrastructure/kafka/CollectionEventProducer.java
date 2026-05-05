package org.example.infrastructure.kafka;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.example.infrastructure.event.CollectionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class CollectionEventProducer {

    private static final Logger LOG = LoggerFactory.getLogger(CollectionEventProducer.class);

    @Inject
    @Channel("collection-events-out")
    Emitter<CollectionEvent> emitter;

    public Uni<Void> publishEvent(CollectionEvent event) {
        return Uni.createFrom().item(() -> {
            LOG.info("Publishing event: {} for request: {}", event.getEventType(), event.getRequestId());
            emitter.send(event);
            return null;
        });
    }
}

