package org.example.application.port.out;

import io.smallrye.mutiny.Uni;
import org.example.application.usecase.SearchCollectionsUseCase.CollectionSearchQuery;
import org.example.domain.entity.CollectionRequest;

import java.util.List;

public interface CollectionRequestPort {
    Uni<Void> save(CollectionRequest request);

    Uni<Void> update(CollectionRequest request);

    Uni<CollectionRequest> findById(String id);

    Uni<List<CollectionRequest>> findByGeneratorId(String generatorId);

    Uni<List<CollectionRequest>> findByStatus(String status);

    Uni<List<CollectionRequest>> search(CollectionSearchQuery query);

    Uni<List<CollectionRequest>> findByIds(List<String> ids);

    Uni<List<CollectionRequest>> findInProgress(int limit);

    Uni<List<CollectionRequest>> findBySelectedCollectorId(String collectorId);
}
