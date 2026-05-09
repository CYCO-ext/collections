package org.example.infrastructure.address;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.port.out.AddressEnrichmentPort;
import org.example.domain.entity.Address;
import org.example.infrastructure.event.SyncAddressEvent;
import org.example.infrastructure.repository.AddressCacheRepository;
import org.example.infrastructure.repository.AddressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.UUID;

@Singleton
public class AddressEnrichmentAdapter implements AddressEnrichmentPort {

    private static final Logger LOG = LoggerFactory.getLogger(AddressEnrichmentAdapter.class);

    @Inject
    AddressRepository addressRepository;

    @Inject
    NominatimClient nominatimClient;

    @Inject
    ViacepClient viacepClient;

    @Inject
    AddressCacheRepository cacheRepository;

    @ConfigProperty(name = "enrichment.enabled", defaultValue = "true")
    boolean enrichmentEnabled;

    @ConfigProperty(name = "viacep.enabled", defaultValue = "true")
    boolean viacepEnabled;

    @ConfigProperty(name = "enrichment.user-agent", defaultValue = "collections-service/1.0")
    String userAgent;

    @Override
    public Uni<Address> enrich(SyncAddressEvent event) {
        Address address = fromEvent(event);

        if (!enrichmentEnabled) {
            address.setId(generateOrValidateId(address));
            address.setEnrichmentStatus("SKIPPED");
            return Uni.createFrom().item(address);
        }

        return normalizeWithViaCep(address)
                .flatMap(normalized -> {
                    normalized.setId(generateOrValidateId(normalized));
                    return addressRepository.findDuplicate(normalized)
                            .flatMap(duplicate -> {
                                if (duplicate != null) {
                                    LOG.info("Duplicate address found. Reusing address id {}", duplicate.getId());
                                    return Uni.createFrom().item(duplicate);
                                }
                                return enrichCoordinates(normalized);
                            });
                });
    }

    private Address fromEvent(SyncAddressEvent event) {
        Address address = new Address();
        address.setId(normalize(event.id()));
        address.setStreet(normalize(event.street()));
        address.setCity(normalize(event.city()));
        address.setZipCode(normalizeCep(event.zipCode()));
        address.setNumber(normalize(event.number()));
        address.setLatitude(event.latitude());
        address.setLongitude(event.longitude());
        address.setEnrichmentStatus("PENDING");
        return address;
    }

    private Uni<Address> normalizeWithViaCep(Address address) {
        if (!viacepEnabled || address.getZipCode() == null) {
            return Uni.createFrom().item(address);
        }

        return viacepClient.geocodeCep(address.getZipCode())
                .onFailure().recoverWithItem(java.util.Optional.empty())
                .map(viaCepResult -> {
                    viaCepResult.ifPresent(enriched -> applyViaCepFields(address, enriched));
                    return address;
                });
    }

    private void applyViaCepFields(Address target, Address enriched) {
        target.setZipCode(firstNonBlank(enriched.getZipCode(), target.getZipCode()));
        target.setStreet(firstNonBlank(target.getStreet(), enriched.getStreet()));
        target.setCity(firstNonBlank(target.getCity(), enriched.getCity()));
        target.setState(firstNonBlank(target.getState(), enriched.getState()));
    }

    private Uni<Address> enrichCoordinates(Address address) {
        if (address.getLatitude() != null && address.getLongitude() != null) {
            address.setEnrichmentStatus("ENRICHED");
            address.setEnrichmentSource("provided");
            return Uni.createFrom().item(address);
        }

        String cacheKey = buildCacheKey(address);
        return cacheRepository.findByKey(cacheKey)
                .flatMap(cached -> {
                    if (cached != null && cached.getLatitude() != null && cached.getLongitude() != null) {
                        address.setLatitude(cached.getLatitude());
                        address.setLongitude(cached.getLongitude());
                        address.setEnrichmentStatus("ENRICHED");
                        address.setEnrichmentSource("cache");
                        return Uni.createFrom().item(address);
                    }
                    return enrichWithNominatim(address, cacheKey);
                });
    }

    private Uni<Address> enrichWithNominatim(Address address, String cacheKey) {
        String query = buildQuery(address);
        return nominatimClient.geocode(query, userAgent)
                .flatMap(coords -> {
                    if (coords.isPresent()) {
                        double[] latlon = coords.get();
                        address.setLatitude(latlon[0]);
                        address.setLongitude(latlon[1]);
                        address.setEnrichmentStatus("ENRICHED");
                        address.setEnrichmentSource(viacepEnabled && address.getZipCode() != null ? "viacep+nominatim" : "nominatim");
                    } else {
                        address.setEnrichmentStatus("ADDRESS_UNVERIFIED");
                        address.setEnrichmentSource(viacepEnabled && address.getZipCode() != null ? "viacep+nominatim" : "nominatim");
                    }

                    return cacheRepository.upsert(cacheKey, address, address.getEnrichmentSource())
                            .replaceWith(address);
                });
    }

    private String generateOrValidateId(Address address) {
        if (address.getId() != null) {
            return address.getId();
        }
        if (address.getZipCode() != null) {
            return hashId(String.join("|",
                    address.getZipCode(),
                    nullToEmpty(normalizeKey(address.getStreet())),
                    nullToEmpty(normalizeKey(address.getNumber())),
                    nullToEmpty(normalizeKey(address.getCity())),
                    nullToEmpty(normalizeKey(address.getState()))
            ));
        }
        return UUID.randomUUID().toString();
    }

    private String hashId(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                String value = Integer.toHexString(0xff & b);
                if (value.length() == 1) {
                    hex.append('0');
                }
                hex.append(value);
            }
            return hex.toString();
        } catch (Exception e) {
            LOG.error("Could not generate deterministic address id", e);
            return UUID.randomUUID().toString();
        }
    }

    private String buildCacheKey(Address address) {
        return String.join("|",
                nullToEmpty(address.getZipCode()),
                nullToEmpty(normalizeKey(address.getNumber())),
                nullToEmpty(normalizeKey(address.getStreet())),
                nullToEmpty(normalizeKey(address.getCity())),
                nullToEmpty(normalizeKey(address.getState()))
        );
    }

    private String buildQuery(Address address) {
        StringBuilder query = new StringBuilder();
        appendPart(query, address.getStreet());
        appendPart(query, address.getNumber());
        appendPart(query, address.getCity());
        appendPart(query, address.getState());
        appendPart(query, address.getZipCode());
        appendPart(query, "Brazil");
        return query.toString();
    }

    private void appendPart(StringBuilder query, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!query.isEmpty()) {
            query.append(", ");
        }
        query.append(value.trim());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeCep(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.replaceAll("\\D", "");
    }

    private String normalizeKey(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        return Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }

    private String firstNonBlank(String preferred, String fallback) {
        String normalized = normalize(preferred);
        return normalized != null ? normalized : normalize(fallback);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
