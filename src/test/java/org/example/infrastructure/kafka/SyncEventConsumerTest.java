package org.example.infrastructure.kafka;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import org.example.domain.entity.Address;
import org.example.domain.entity.Collector;
import org.example.infrastructure.event.SyncAddressEvent;
import org.example.infrastructure.event.SyncCollectorEvent;
import org.example.infrastructure.repository.AddressRepository;
import org.example.infrastructure.repository.CollectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SyncEventConsumerTest {

    private SyncEventConsumer consumer;
    private AddressRepository addressRepository;
    private CollectorRepository collectorRepository;

    @BeforeEach
    void setUp() {
        consumer = new SyncEventConsumer();
        consumer.addressEnrichmentPort = mock(org.example.application.port.out.AddressEnrichmentPort.class);
        consumer.addressRepository = mock(AddressRepository.class);
        consumer.collectorRepository = mock(CollectorRepository.class);
        addressRepository = consumer.addressRepository;
        collectorRepository = consumer.collectorRepository;
    }

    @Test
    void consumeCollectorEnrichesAddressAndPersistsReturnedAddressReference() {
        SyncAddressEvent addressEvent = new SyncAddressEvent(
                null,
                "Praça da Sé",
                "São Paulo",
                "01001-000",
                "100",
                null,
                null
        );
        SyncCollectorEvent event = new SyncCollectorEvent(
                "COLLECTOR_UPDATED",
                "collector-1",
                "user-1",
                "Collector One",
                addressEvent,
                List.of("paper"),
                0.95
        );
        Address enrichedAddress = new Address(
                "address-1",
                "Praça da Sé",
                "São Paulo",
                "01001000",
                "100",
                "SP",
                -23.55052,
                -46.633308,
                "ENRICHED",
                "viacep+nominatim"
        );

        when(consumer.addressEnrichmentPort.enrich(addressEvent)).thenReturn(Uni.createFrom().item(enrichedAddress));
        when(addressRepository.upsert(enrichedAddress)).thenReturn(Uni.createFrom().voidItem());
        when(collectorRepository.upsert(any(Collector.class))).thenReturn(Uni.createFrom().voidItem());

        consumer.consumeCollector(record(event)).await().indefinitely();

        ArgumentCaptor<Collector> collectorCaptor = ArgumentCaptor.forClass(Collector.class);
        verify(consumer.addressEnrichmentPort).enrich(addressEvent);
        verify(addressRepository).upsert(enrichedAddress);
        verify(collectorRepository).upsert(collectorCaptor.capture());

        Collector persistedCollector = collectorCaptor.getValue();
        assertEquals("collector-1", persistedCollector.getId());
        assertEquals("address-1", persistedCollector.getAddress().getId());
        assertEquals("100", persistedCollector.getAddress().getNumber());
        assertEquals("SP", persistedCollector.getAddress().getState());
        assertEquals("ENRICHED", persistedCollector.getAddress().getEnrichmentStatus());
    }

    @Test
    void consumeCollectorReusesSameAddressIdForDuplicateCollectorAddress() {
        SyncAddressEvent firstAddress = new SyncAddressEvent(null, "Praça da Sé", "São Paulo", "01001-000", "100", null, null);
        SyncAddressEvent secondAddress = new SyncAddressEvent(null, "Praca da Se", "Sao Paulo", "01001000", "100", null, null);
        SyncCollectorEvent firstEvent = new SyncCollectorEvent("COLLECTOR_UPDATED", "collector-1", "user-1", "Collector One", firstAddress, List.of("paper"), 0.95);
        SyncCollectorEvent secondEvent = new SyncCollectorEvent("COLLECTOR_UPDATED", "collector-2", "user-2", "Collector Two", secondAddress, List.of("plastic"), 0.90);
        Address duplicateAddress = new Address(
                "existing-address",
                "Praça da Sé",
                "São Paulo",
                "01001000",
                "100",
                "SP",
                -23.55052,
                -46.633308,
                "ENRICHED",
                "viacep+nominatim"
        );

        when(consumer.addressEnrichmentPort.enrich(firstAddress)).thenReturn(Uni.createFrom().item(duplicateAddress));
        when(consumer.addressEnrichmentPort.enrich(secondAddress)).thenReturn(Uni.createFrom().item(duplicateAddress));
        when(addressRepository.upsert(duplicateAddress)).thenReturn(Uni.createFrom().voidItem());
        when(collectorRepository.upsert(any(Collector.class))).thenReturn(Uni.createFrom().voidItem());

        consumer.consumeCollector(record(firstEvent)).await().indefinitely();
        consumer.consumeCollector(record(secondEvent)).await().indefinitely();

        ArgumentCaptor<Collector> collectorCaptor = ArgumentCaptor.forClass(Collector.class);
        verify(addressRepository, times(2)).upsert(duplicateAddress);
        verify(collectorRepository, times(2)).upsert(collectorCaptor.capture());

        List<Collector> persistedCollectors = collectorCaptor.getAllValues();
        assertEquals("existing-address", persistedCollectors.get(0).getAddress().getId());
        assertEquals("existing-address", persistedCollectors.get(1).getAddress().getId());
    }

    @SuppressWarnings("unchecked")
    private KafkaRecord<String, SyncCollectorEvent> record(SyncCollectorEvent event) {
        KafkaRecord<String, SyncCollectorEvent> record = mock(KafkaRecord.class);
        when(record.getPayload()).thenReturn(event);
        return record;
    }
}
