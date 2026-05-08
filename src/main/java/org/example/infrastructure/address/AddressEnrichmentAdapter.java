package org.example.infrastructure.address;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.AddressEnrichmentPort;
import org.example.domain.entity.Address;
import org.example.infrastructure.repository.AddressCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

@Singleton
public class AddressEnrichmentAdapter implements AddressEnrichmentPort {
    private static final Logger LOG = LoggerFactory.getLogger(AddressEnrichmentAdapter.class);

    @Inject
    NominatimClient nominatimClient;

    @Inject
    ViacepClient viacepClient;

    @Inject
    AddressCacheRepository cacheRepository;

    @ConfigProperty(name = "viacep.enabled", defaultValue = "false")
    boolean viacepEnabled;

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
                    // Try ViaCEP first if enabled and zip code present
                    if (viacepEnabled && address.getZipCode() != null && !address.getZipCode().isBlank()) {
                        return viacepClient.geocodeCep(address.getZipCode())
                                .flatMap(opt -> {
                                    if (opt.isPresent()) {
                                        Address v = opt.get();
                                        if (address.getStreet() == null && v.getStreet() != null) address.setStreet(v.getStreet());
                                        if (address.getCity() == null && v.getCity() != null) address.setCity(v.getCity());
                                        if (address.getZipCode() == null && v.getZipCode() != null) address.setZipCode(v.getZipCode());

                                        // attempt to geocode enriched address
                                        String query = buildQuery(address);
                                        return nominatimClient.geocode(query, USER_AGENT)
                                                .flatMap(optCoords -> {
                                                    if (optCoords.isPresent()) {
                                                        double[] latlon = optCoords.get();
                                                        address.setLatitude(latlon[0]);
                                                        address.setLongitude(latlon[1]);
                                                        return cacheRepository.upsert(key, address, "viacep")
                                                                .replaceWith(address);
                                                    } else {
                                                        LOG.warn("Nominatim failed to find coords for {}", query);
                                                        // still cache the address info returned by ViaCEP
                                                        return cacheRepository.upsert(key, address, "viacep")
                                                                .replaceWith(address);
                                                    }
                                                });
                                    } else {
                                        // fallback to nominatim only
                                        String query = buildQuery(address);
                                        return nominatimClient.geocode(query, USER_AGENT)
                                                .flatMap(optCoords -> {
                                                    if (optCoords.isPresent()) {
                                                        double[] latlon = optCoords.get();
                                                        address.setLatitude(latlon[0]);
                                                        address.setLongitude(latlon[1]);
                                                        return cacheRepository.upsert(key, address, "nominatim")
                                                                .replaceWith(address);
                                                    } else {
                                                        LOG.warn("Nominatim failed to find coords for {}", query);
                                                        return Uni.createFrom().item(address);
                                                    }
                                                });
                                    }
                                });
                    }

                    // default: call nominatim directly
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
