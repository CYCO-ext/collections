package org.example.infrastructure.kafka;

import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.example.domain.entity.Address;
import org.example.domain.entity.Collector;
import org.example.domain.entity.Material;
import org.example.infrastructure.event.SyncAddressEvent;
import org.example.infrastructure.event.SyncCollectorEvent;
import org.example.infrastructure.event.SyncMaterialEvent;
import org.example.infrastructure.repository.AddressRepository;
import org.example.infrastructure.repository.CollectorRepository;
import org.example.infrastructure.repository.MaterialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SyncEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(SyncEventConsumer.class);

    @Inject
    MaterialRepository materialRepository;

    @Inject
    AddressRepository addressRepository;

    @Inject
    CollectorRepository collectorRepository;

    @Incoming("sync-addresses")
    @Blocking
    public Uni<Void> consumeAddress(KafkaRecord<String, SyncAddressEvent> record) {
        SyncAddressEvent event = record.getPayload();
        LOG.info("Syncing address: {}", event.id());

        Address address = new Address(
                event.id(),
                event.street(),
                event.city(),
                event.zipCode(),
                event.latitude(),
                event.longitude()
        );

        return addressRepository.upsert(address)
                .invoke(() -> LOG.info("Address synced: {}", event.id()))
                .onFailure().invoke(ex -> LOG.error("Failed to sync address", ex));
    }

    @Incoming("collector-sync")
    @Blocking
    public Uni<Void> consumeCollector(KafkaRecord<String, SyncCollectorEvent> record) {
        SyncCollectorEvent event = record.getPayload();
        LOG.info("Syncing collector: {}", event.collectorId());

        Address address = new Address(
                event.address().id(),
                event.address().street(),
                event.address().city(),
                event.address().zipCode(),
                event.address().latitude(),
                event.address().longitude()
        );

        Collector collector = new Collector(
                event.collectorId(),
                event.userId(),
                event.name(),
                address,
                event.acceptedMaterialIds(),
                event.acceptanceRate()
        );

        return collectorRepository.upsert(collector)
                .invoke(() -> LOG.info("Collector synced: {}", event.collectorId()))
                .onFailure().invoke(ex -> LOG.error("Failed to sync collector", ex));
    }
}

