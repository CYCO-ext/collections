package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.usecase.SearchCollectionsUseCase.CollectionSearchQuery;
import org.example.domain.entity.CollectionRequest;

import java.util.List;

@Alternative
@Priority(1)
@ApplicationScoped
public class CompletionRequestPortFixture implements CollectionRequestPort {
    @Override public Uni<Void> save(CollectionRequest request) { return Uni.createFrom().voidItem(); }
    @Override public Uni<Void> update(CollectionRequest request) { return Uni.createFrom().voidItem(); }
    @Override public Uni<CollectionRequest> findById(String id) { return Uni.createFrom().nullItem(); }
    @Override public Uni<List<CollectionRequest>> findByGeneratorId(String id) { return Uni.createFrom().item(List.of()); }
    @Override public Uni<List<CollectionRequest>> findByStatus(String status) { return Uni.createFrom().item(List.of()); }
    @Override public Uni<List<CollectionRequest>> search(CollectionSearchQuery query) { return Uni.createFrom().item(List.of()); }
    @Override public Uni<List<CollectionRequest>> findByIds(List<String> ids) { return Uni.createFrom().item(List.of()); }
    @Override public Uni<List<CollectionRequest>> findInProgress(int limit) { return Uni.createFrom().item(List.of()); }
    @Override public Uni<List<CollectionRequest>> findBySelectedCollectorId(String id) { return Uni.createFrom().item(List.of()); }
}
