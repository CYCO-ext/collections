package org.example.infrastructure.kafka;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.example.application.port.out.AddressEnrichmentPort;
import org.example.domain.entity.Address;
import org.example.domain.entity.Collector;
import org.example.infrastructure.event.SyncAddressEvent;
import org.example.infrastructure.event.SyncCollectorEvent;
import org.example.infrastructure.repository.AddressRepository;
import org.example.infrastructure.repository.CollectorRepository;
import org.example.infrastructure.repository.MaterialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

public class SyncEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(SyncEventConsumer.class);

    @Inject
    MaterialRepository materialRepository;

    @Inject
    AddressRepository addressRepository;

    @Inject
    AddressEnrichmentPort addressEnrichmentPort;

    @Inject
    CollectorRepository collectorRepository;

    @Incoming("addresses-sync")
    @Blocking
    public Uni<Void> consumeAddress(KafkaRecord<String, SyncAddressEvent> record) {
        SyncAddressEvent event = record.getPayload();
        LOG.info("Syncing address: {}", event.zipCode());

        return addressEnrichmentPort.enrich(event)
                .flatMap(enriched -> addressRepository.upsert(enriched)
                        .invoke(() -> LOG.info("Address enriched and synced: {}", enriched.getId()))
                )
                .onFailure().recoverWithUni(ex -> {
                    LOG.error("Failed to enrich address: {}", event.id(), ex);
                    Address address = new Address(
                            resolveAddressId(event),
                            event.street(),
                            event.city(),
                            normalizeCep(event.zipCode()),
                            event.number(),
                            null,
                            event.latitude(),
                            event.longitude(),
                            "FAILED",
                            null
                    );
                    return addressRepository.upsert(address)
                            .invoke(() -> LOG.info("Address synced with failure status: {}", address.getId()));
                });
    }

    @Incoming("collector-sync")
    @Blocking
    public Uni<Void> consumeCollector(KafkaRecord<String, SyncCollectorEvent> record) {
        SyncCollectorEvent event = record.getPayload();
        LOG.info("Syncing collector: {}", event.collectorId());

        return addressEnrichmentPort.enrich(event.address())
                .flatMap(enriched -> addressRepository.upsert(enriched)
                        .flatMap(ignored -> collectorRepository.upsert(toCollector(event, enriched)))
                        .invoke(() -> LOG.info("Collector synced with enriched address: {}", event.collectorId()))
                )
                .onFailure().recoverWithUni(ex -> {
                    LOG.error("Failed to enrich collector address: {}", event.collectorId(), ex);
                    Address address = fallbackAddress(event.address());
                    return addressRepository.upsert(address)
                            .flatMap(ignored -> collectorRepository.upsert(toCollector(event, address)))
                            .invoke(() -> LOG.info("Collector synced with failure status: {}", event.collectorId()));
                });
    }

    private Collector toCollector(SyncCollectorEvent event, Address address) {
        return new Collector(
                event.collectorId(),
                event.userId(),
                event.name(),
                address,
                event.acceptedMaterialIds(),
                event.acceptanceRate()
        );
    }

    private Address fallbackAddress(SyncAddressEvent event) {
        return new Address(
                resolveAddressId(event),
                event.street(),
                event.city(),
                normalizeCep(event.zipCode()),
                event.number(),
                null,
                event.latitude(),
                event.longitude(),
                "FAILED",
                null
        );
    }

    private String resolveAddressId(SyncAddressEvent event) {
        if (event.id() != null && !event.id().isBlank()) {
            return event.id();
        }
        String cep = normalizeCep(event.zipCode());
        if (cep == null) {
            return UUID.randomUUID().toString();
        }
        return hashId(String.join("|",
                cep,
                normalizeKey(event.street()),
                normalizeKey(event.number()),
                normalizeKey(event.city())
        ));
    }

    private String normalizeCep(String cep) {
        if (cep == null) {
            return null;
        }
        String digits = cep.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
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
            return UUID.randomUUID().toString();
        }
    }
}
