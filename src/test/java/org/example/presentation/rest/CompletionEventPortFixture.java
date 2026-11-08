package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.example.application.port.out.EventPort;
import org.example.infrastructure.event.CollectionEvent;

@Alternative
@Priority(1)
@ApplicationScoped
public class CompletionEventPortFixture implements EventPort {
    public static volatile boolean failCompletionEvent;

    @Override
    public Uni<Void> publishCollectionEvent(CollectionEvent event) {
        if (failCompletionEvent && "COLLECTION_COMPLETED".equals(event.getEventType())) {
            return Uni.createFrom().failure(new IllegalStateException("event fixture unavailable"));
        }
        return Uni.createFrom().voidItem();
    }
}
