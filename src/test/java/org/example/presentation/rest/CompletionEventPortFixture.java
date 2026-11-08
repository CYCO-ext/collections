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
    @Override
    public Uni<Void> publishCollectionEvent(CollectionEvent event) {
        return Uni.createFrom().voidItem();
    }
}
