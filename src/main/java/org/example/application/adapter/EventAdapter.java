package org.example.application.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.EventPort;
import org.example.infrastructure.event.CollectionEvent;
import org.example.infrastructure.kafka.CollectionEventProducer;

@Singleton
public class EventAdapter implements EventPort {

    @Inject
    CollectionEventProducer producer;

    @Override
    public Uni<Void> publishCollectionEvent(CollectionEvent event) {
        return producer.publishEvent(event);
    }
}

