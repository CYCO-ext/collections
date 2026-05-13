package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.AddressPort;
import org.example.application.port.out.CollectionRequestPort;
import org.example.domain.entity.Address;
import org.example.domain.entity.CollectionRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Singleton
public class SearchCollectionsUseCase {

    @Inject
    CollectionRequestPort collectionRequestPort;

    @Inject
    AddressPort addressPort;

    public Uni<List<CollectionSearchResult>> search(String statusFilter, String collectorId, String generatorId) {
        CollectionSearchQuery query = new CollectionSearchQuery(
                parseStatus(statusFilter),
                normalizeOptionalId(collectorId),
                normalizeOptionalId(generatorId)
        );
        return collectionRequestPort.search(query)
                .flatMap(requests -> {
                    List<Uni<CollectionSearchResult>> resultLookups = requests.stream()
                            .map(this::toResult)
                            .toList();
                    if (resultLookups.isEmpty()) {
                        return Uni.createFrom().item(List.of());
                    }
                    return Uni.combine().all().unis(resultLookups)
                            .with(items -> items.stream()
                                    .map(CollectionSearchResult.class::cast)
                                    .toList());
                });
    }

    Uni<CollectionSearchResult> toResult(CollectionRequest request) {
        return addressPort.findById(request.getAddressId())
                .onItem().ifNull().failWith(() -> new CollectionAddressNotFoundException(request.getAddressId()))
                .onItem().transform(address -> CollectionSearchResult.from(request, address));
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

    public record CollectionAddressResult(
            String id,
            String street,
            String number,
            String city,
            String state,
            String zipCode,
            Double latitude,
            Double longitude,
            String enrichmentStatus,
            String enrichmentSource
    ) {
        static CollectionAddressResult from(Address address) {
            return new CollectionAddressResult(
                    address.getId(),
                    address.getStreet(),
                    address.getNumber(),
                    address.getCity(),
                    address.getState(),
                    address.getZipCode(),
                    address.getLatitude(),
                    address.getLongitude(),
                    address.getEnrichmentStatus(),
                    address.getEnrichmentSource()
            );
        }
    }

    public record CollectionSearchResult(
            String id,
            String generatorId,
            String addressId,
            CollectionAddressResult address,
            List<String> materialIds,
            Double weight,
            CollectionRequest.Status status,
            String selectedCollectorId,
            Boolean generatorConfirmed,
            Boolean collectorConfirmed,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        static CollectionSearchResult from(CollectionRequest request, Address address) {
            return new CollectionSearchResult(
                    request.getId(),
                    request.getGeneratorId(),
                    request.getAddressId(),
                    CollectionAddressResult.from(address),
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
