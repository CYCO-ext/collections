package org.example.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    ObjectMapper objectMapper;

    @Inject
    @Channel("collection-events-out")
    Emitter<String> emitter;

    public Uni<Void> publishEvent(CollectionEvent event) {
        return Uni.createFrom().voidItem()
                .invoke(() -> {
                    try {
                        LOG.info(
                                "Publishing event: {} for request: {}",
                                event.getEventType(),
                                event.getRequestId()
                        );

                        String payload = objectMapper.writeValueAsString(event);
                        emitter.send(payload);

                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("Failed to serialize CollectionEvent", e);
                    }
                });
    }
}