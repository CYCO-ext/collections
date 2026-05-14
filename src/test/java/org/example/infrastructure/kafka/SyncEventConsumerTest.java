package org.example.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        consumer.objectMapper = new ObjectMapper();
        addressRepository = consumer.addressRepository;
        collectorRepository = consumer.collectorRepository;
    }

    @Test
    void consumeCollectorEnrichesAddressAndPersistsReturnedAddressReference() {
        SyncAddressEvent addressEvent = addressEvent();
        SyncCollectorEvent event = collectorEvent("COLLECTOR_CREATED", "collector-1", "user-1", "Collector One", addressEvent, List.of("paper"), 0.95);
        Address enrichedAddress = enrichedAddress("address-1");

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
        assertEquals("user-1", persistedCollector.getUserId());
        assertEquals("Collector One", persistedCollector.getName());
        assertEquals(List.of("paper"), persistedCollector.getAcceptedMaterialIds());
        assertEquals(0.95, persistedCollector.getAcceptanceRate());
        assertEquals("address-1", persistedCollector.getAddress().getId());
        assertEquals("100", persistedCollector.getAddress().getNumber());
        assertEquals("SP", persistedCollector.getAddress().getState());
        assertEquals("ENRICHED", persistedCollector.getAddress().getEnrichmentStatus());
    }

    @Test
    void consumeCollectorUpdateEnrichesAddressAndPersistsUpdatedCollectorFields() {
        SyncAddressEvent addressEvent = new SyncAddressEvent(
                "address-updated",
                "Rua Atualizada",
                "São Paulo",
                "01310-000",
                "200",
                -23.561,
                -46.656
        );
        SyncCollectorEvent event = collectorEvent(
                "COLLECTOR_UPDATED",
                "collector-1",
                "user-updated",
                "Collector Updated",
                addressEvent,
                List.of("paper", "plastic"),
                0.99
        );
        Address enrichedAddress = new Address(
                "address-updated",
                "Rua Atualizada",
                "São Paulo",
                "01310000",
                "200",
                "SP",
                -23.561,
                -46.656,
                "ENRICHED",
                "viacep+nominatim"
        );

        when(consumer.addressEnrichmentPort.enrich(addressEvent)).thenReturn(Uni.createFrom().item(enrichedAddress));
        when(addressRepository.upsert(enrichedAddress)).thenReturn(Uni.createFrom().voidItem());
        when(collectorRepository.upsert(any(Collector.class))).thenReturn(Uni.createFrom().voidItem());

        consumer.consumeCollectorUpdate(record(event)).await().indefinitely();

        ArgumentCaptor<Collector> collectorCaptor = ArgumentCaptor.forClass(Collector.class);
        verify(addressRepository).upsert(enrichedAddress);
        verify(collectorRepository).upsert(collectorCaptor.capture());

        Collector persistedCollector = collectorCaptor.getValue();
        assertEquals("collector-1", persistedCollector.getId());
        assertEquals("user-updated", persistedCollector.getUserId());
        assertEquals("Collector Updated", persistedCollector.getName());
        assertEquals(List.of("paper", "plastic"), persistedCollector.getAcceptedMaterialIds());
        assertEquals(0.99, persistedCollector.getAcceptanceRate());
        assertEquals("address-updated", persistedCollector.getAddress().getId());
        assertEquals("Rua Atualizada", persistedCollector.getAddress().getStreet());
    }

    @Test
    void consumeCollectorUpdateFallsBackWhenAddressEnrichmentFails() {
        SyncAddressEvent addressEvent = new SyncAddressEvent(
                null,
                "Rua Sem Enriquecimento",
                "São Paulo",
                "01311-000",
                "300",
                -23.562,
                -46.657
        );
        SyncCollectorEvent event = collectorEvent(
                "COLLECTOR_UPDATED",
                "collector-1",
                "user-1",
                "Collector Fallback",
                addressEvent,
                List.of("glass"),
                0.80
        );

        when(consumer.addressEnrichmentPort.enrich(addressEvent)).thenReturn(Uni.createFrom().failure(new RuntimeException("enrichment unavailable")));
        when(addressRepository.upsert(any(Address.class))).thenReturn(Uni.createFrom().voidItem());
        when(collectorRepository.upsert(any(Collector.class))).thenReturn(Uni.createFrom().voidItem());

        consumer.consumeCollectorUpdate(record(event)).await().indefinitely();

        ArgumentCaptor<Address> addressCaptor = ArgumentCaptor.forClass(Address.class);
        ArgumentCaptor<Collector> collectorCaptor = ArgumentCaptor.forClass(Collector.class);
        verify(addressRepository).upsert(addressCaptor.capture());
        verify(collectorRepository).upsert(collectorCaptor.capture());

        Address fallbackAddress = addressCaptor.getValue();
        assertEquals("Rua Sem Enriquecimento", fallbackAddress.getStreet());
        assertEquals("01311000", fallbackAddress.getZipCode());
        assertEquals("FAILED", fallbackAddress.getEnrichmentStatus());

        Collector persistedCollector = collectorCaptor.getValue();
        assertEquals("collector-1", persistedCollector.getId());
        assertEquals("Collector Fallback", persistedCollector.getName());
        assertEquals(List.of("glass"), persistedCollector.getAcceptedMaterialIds());
        assertEquals("FAILED", persistedCollector.getAddress().getEnrichmentStatus());
    }

    @Test
    void consumeCollectorReusesSameAddressIdForDuplicateCollectorAddress() {
        SyncAddressEvent firstAddress = new SyncAddressEvent(null, "Praça da Sé", "São Paulo", "01001-000", "100", null, null);
        SyncAddressEvent secondAddress = new SyncAddressEvent(null, "Praca da Se", "Sao Paulo", "01001000", "100", null, null);
        SyncCollectorEvent firstEvent = collectorEvent("COLLECTOR_CREATED", "collector-1", "user-1", "Collector One", firstAddress, List.of("paper"), 0.95);
        SyncCollectorEvent secondEvent = collectorEvent("COLLECTOR_CREATED", "collector-2", "user-2", "Collector Two", secondAddress, List.of("plastic"), 0.90);
        Address duplicateAddress = enrichedAddress("existing-address");

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

    private SyncAddressEvent addressEvent() {
        return new SyncAddressEvent(
                null,
                "Praça da Sé",
                "São Paulo",
                "01001-000",
                "100",
                null,
                null
        );
    }

    private SyncCollectorEvent collectorEvent(
            String eventType,
            String collectorId,
            String userId,
            String name,
            SyncAddressEvent address,
            List<String> acceptedMaterialIds,
            Double acceptanceRate) {
        return new SyncCollectorEvent(eventType, collectorId, userId, name, address, acceptedMaterialIds, acceptanceRate);
    }

    private Address enrichedAddress(String id) {
        return new Address(
                id,
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
    }

    @SuppressWarnings("unchecked")
    private KafkaRecord<String, String> record(SyncCollectorEvent event) {
        KafkaRecord<String, String> record = mock(KafkaRecord.class);
        try {
            when(record.getPayload()).thenReturn(new ObjectMapper().writeValueAsString(event));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return record;
    }
}
