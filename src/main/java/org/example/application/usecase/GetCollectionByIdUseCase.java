package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.usecase.SearchCollectionsUseCase.CollectionSearchResult;

@Singleton
public class GetCollectionByIdUseCase {

    @Inject
    CollectionRequestPort collectionRequestPort;

    public Uni<CollectionSearchResult> getById(String id) {
        String normalizedId = normalizeId(id);
        return collectionRequestPort.findById(normalizedId)
                .onItem().ifNull().failWith(() -> new CollectionNotFoundException(normalizedId))
                .onItem().transform(CollectionSearchResult::from);
    }

    private String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("collection id is required");
        }
        return id.trim();
    }
}
