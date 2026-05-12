package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.CollectorDiscoveryPort;
import org.example.domain.entity.Address;

@Singleton
public class GetCollectorAddressUseCase {

    @Inject
    CollectorDiscoveryPort collectorDiscoveryPort;

    public Uni<CollectorAddressResult> getAddress(String collectorId) {
        String normalizedCollectorId = normalizeCollectorId(collectorId);
        return collectorDiscoveryPort.findCollectorById(normalizedCollectorId)
                .onItem().ifNull().failWith(() -> new CollectorNotFoundException(normalizedCollectorId))
                .onItem().transform(collector -> {
                    Address address = collector.getAddress();
                    if (address == null) {
                        throw new CollectorAddressNotFoundException(normalizedCollectorId);
                    }
                    return CollectorAddressResult.from(normalizedCollectorId, address);
                });
    }

    private String normalizeCollectorId(String collectorId) {
        if (collectorId == null || collectorId.isBlank()) {
            throw new IllegalArgumentException("collector id is required");
        }
        return collectorId.trim();
    }

    public record CollectorAddressResult(
            String collectorId,
            String addressId,
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
        static CollectorAddressResult from(String collectorId, Address address) {
            return new CollectorAddressResult(
                    collectorId,
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
}
