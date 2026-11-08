package org.example.application.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.usecase.SearchCollectionsUseCase.CollectionSearchQuery;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.CompletionCodeState;
import org.example.domain.entity.CompletionEvidence;
import org.example.infrastructure.repository.CollectionRequestRepository;

import java.time.LocalDateTime;
import java.util.List;

@Singleton
public class CollectionRequestAdapter implements CollectionRequestPort {

    @Inject
    CollectionRequestRepository repository;

    @Override
    public Uni<Void> save(CollectionRequest request) {
        return repository.save(request);
    }

    @Override
    public Uni<Void> update(CollectionRequest request) {
        return repository.update(request);
    }

    @Override
    public Uni<CollectionRequest> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Uni<List<CollectionRequest>> findByGeneratorId(String generatorId) {
        return repository.findByGeneratorId(generatorId);
    }

    @Override
    public Uni<List<CollectionRequest>> findByStatus(String status) {
        return repository.findByStatus(status);
    }

    @Override
    public Uni<List<CollectionRequest>> search(CollectionSearchQuery query) {
        return repository.search(query);
    }

    @Override
    public Uni<List<CollectionRequest>> findByIds(List<String> ids) {
        return repository.findByIds(ids);
    }

    @Override
    public Uni<List<CollectionRequest>> findInProgress(int limit) {
        return repository.findInProgress(limit);
    }

    @Override
    public Uni<List<CollectionRequest>> findBySelectedCollectorId(String collectorId) {
        return repository.findBySelectedCollectorId(collectorId);
    }

    @Override
    public Uni<Boolean> issueCompletionCodeIfAbsent(String requestId, CompletionCodeState code) {
        return repository.issueCompletionCodeIfAbsent(requestId, code);
    }

    @Override
    public Uni<Boolean> incrementCompletionCodeAttempts(String requestId, int codeVersion,
                                                         LocalDateTime now, LocalDateTime blockedUntil,
                                                         int maxAttempts) {
        return repository.incrementCompletionCodeAttempts(requestId, codeVersion, now, blockedUntil, maxAttempts);
    }

    @Override
    public Uni<Boolean> completeWithCode(String requestId, int codeVersion, CompletionEvidence evidence) {
        return repository.completeWithCode(requestId, codeVersion, evidence);
    }
}
