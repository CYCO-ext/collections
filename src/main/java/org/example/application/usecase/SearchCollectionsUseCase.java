package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.CollectionRequestPort;
import org.example.domain.entity.CollectionRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Singleton
public class SearchCollectionsUseCase {

    @Inject
    CollectionRequestPort collectionRequestPort;

    public Uni<List<CollectionSearchResult>> search(String statusFilter, String collectorId, String generatorId) {
        CollectionSearchQuery query = new CollectionSearchQuery(
                parseStatus(statusFilter),
                normalizeOptionalId(collectorId),
                normalizeOptionalId(generatorId)
        );
        return collectionRequestPort.search(query)
                .onItem().transform(requests -> requests.stream()
                        .map(CollectionSearchResult::from)
                        .toList());
    }

    private CollectionRequest.Status parseStatus(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return null;
        }
        String normalized = statusFilter.trim().toUpperCase(Locale.ROOT);
        try {
            return CollectionRequest.Status.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid collection request status: " + statusFilter);
        }
    }

    private String normalizeOptionalId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record CollectionSearchQuery(
            CollectionRequest.Status status,
            String collectorId,
            String generatorId
    ) {
    }

    public record CollectionSearchResult(
            String id,
            String generatorId,
            String addressId,
            List<String> materialIds,
            Double weight,
            CollectionRequest.Status status,
            String selectedCollectorId,
            Boolean generatorConfirmed,
            Boolean collectorConfirmed,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        static CollectionSearchResult from(CollectionRequest request) {
            return new CollectionSearchResult(
                    request.getId(),
                    request.getGeneratorId(),
                    request.getAddressId(),
                    request.getMaterialIds(),
                    request.getWeight(),
                    request.getStatus(),
                    request.getSelectedCollectorId(),
                    request.getGeneratorConfirmed(),
                    request.getCollectorConfirmed(),
                    request.getCreatedAt(),
                    request.getUpdatedAt()
            );
        }
    }
}
