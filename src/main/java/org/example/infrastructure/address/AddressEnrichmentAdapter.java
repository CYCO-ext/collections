package org.example.infrastructure.address;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.AddressEnrichmentPort;
import org.example.domain.entity.Address;
import org.example.infrastructure.repository.AddressCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Singleton
public class AddressEnrichmentAdapter implements AddressEnrichmentPort {
    private static final Logger LOG = LoggerFactory.getLogger(AddressEnrichmentAdapter.class);

    @Inject
    NominatimClient nominatimClient;

    @Inject
    AddressCacheRepository cacheRepository;

    // user-agent should be configurable; using default for now
    private static final String USER_AGENT = "collections-service/1.0 (contact@example.com)";

    @Override
    public Uni<Address> enrich(Address address) {
        if (address.getLatitude() != null && address.getLongitude() != null) {
            return Uni.createFrom().item(address);
        }

        String key = buildKey(address);

        return cacheRepository.findByKey(key)
                .flatMap(cached -> {
                    if (cached != null && cached.getLatitude() != null && cached.getLongitude() != null) {
                        LOG.info("Address cache hit for key {}", key);
                        address.setLatitude(cached.getLatitude());
                        address.setLongitude(cached.getLongitude());
                        return Uni.createFrom().item(address);
                    }
                    return Uni.createFrom().nullItem();
                })
                .onItem().ifNull().switchTo(() -> {
                    String query = buildQuery(address);
                    return nominatimClient.geocode(query, USER_AGENT)
                            .flatMap(opt -> {
                                Optional<double[]> maybe = opt;
                                if (maybe.isPresent()) {
                                    double[] latlon = maybe.get();
                                    address.setLatitude(latlon[0]);
                                    address.setLongitude(latlon[1]);
                                    return cacheRepository.upsert(key, address, "nominatim")
                                            .replaceWith(address);
                                } else {
                                    LOG.warn("Nominatim failed to find coords for {}", query);
                                    return Uni.createFrom().item(address);
                                }
                            });
                });
    }

    private String buildKey(Address address) {
        // simple key: zipCode + street
        return (address.getZipCode() == null ? "" : address.getZipCode()) + "|" + (address.getStreet() == null ? "" : address.getStreet()).toLowerCase();
    }

    private String buildQuery(Address address) {
        StringBuilder sb = new StringBuilder();
        if (address.getStreet() != null) sb.append(address.getStreet());
        if (address.getZipCode() != null) sb.append(", ").append(address.getZipCode());
        if (address.getCity() != null) sb.append(", ").append(address.getCity());
        sb.append(", Brazil");
        return sb.toString();
    }
}
