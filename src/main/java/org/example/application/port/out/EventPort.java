package org.example.application.port.out;

import io.smallrye.mutiny.Uni;
import org.example.infrastructure.event.CollectionEvent;

public interface EventPort {
    Uni<Void> publishCollectionEvent(CollectionEvent event);
}

