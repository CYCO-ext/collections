package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.CollectorDiscoveryPort;
import org.example.application.port.out.AddressPort;
import org.example.domain.entity.Address;
import org.example.domain.entity.Collector;
import org.example.domain.entity.CollectionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollectionRequestUseCaseTest {

    @InjectMocks
    private CollectionRequestUseCase useCase;

    @Mock
    private CollectionRequestPort collectionRequestPort;

    @Mock
    private CollectorDiscoveryPort collectorDiscoveryPort;

    @Mock
    private AddressPort addressPort;

    @Test
    void testCreateRequest() {
        // Given
        String generatorId = "gen-001";
        String addressId = "addr-001";
        List<String> materialIds = Arrays.asList("mat-001", "mat-002");
        Double weight = 100.0;

        when(collectionRequestPort.save(any(CollectionRequest.class)))
                .thenReturn(Uni.createFrom().nullItem().replaceWithVoid());

        // When
        Uni<CollectionRequest> result = useCase.createRequest(generatorId, addressId, materialIds, weight);

        // Then
        assertNotNull(result);
        result.subscribe().with(request -> {
            assertNotNull(request);
            assertNotNull(request.getId());
            assertEquals(generatorId, request.getGeneratorId());
            assertEquals(addressId, request.getAddressId());
            assertEquals(materialIds, request.getMaterialIds());
            assertEquals(weight, request.getWeight());
            assertEquals(CollectionRequest.Status.PENDING, request.getStatus());
        });

        verify(collectionRequestPort).save(any(CollectionRequest.class));
    }

    @Test
    void testFindNearbyCollectors() {
        // Given
        String requestId = "req-001";
        Address requestAddress = new Address("addr-001", "Main St", "City", "12345", 40.0, -74.0);
        Address collectorAddress1 = new Address("coll-addr-001", "Main St", "City", "12345", 40.01, -74.0);
        Address collectorAddress2 = new Address("coll-addr-002", "Main St", "City", "12345", 40.02, -74.0);

        Collector collector1 = new Collector("coll-001", "user-001", "Collector 1", collectorAddress1,
                Arrays.asList("mat-001", "mat-002"), 0.9);
        Collector collector2 = new Collector("coll-002", "user-002", "Collector 2", collectorAddress2,
                Arrays.asList("mat-001", "mat-002"), 0.85);

        CollectionRequest request = new CollectionRequest("gen-001", "addr-001",
                Arrays.asList("mat-001", "mat-002"), 100.0);
        request.setId(requestId);

        when(collectionRequestPort.findById(requestId))
                .thenReturn(Uni.createFrom().item(request));
        when(addressPort.findById("addr-001"))
                .thenReturn(Uni.createFrom().item(requestAddress));
        when(collectorDiscoveryPort.findCollectorsAcceptingMaterials(Arrays.asList("mat-001", "mat-002")))
                .thenReturn(Uni.createFrom().item(Arrays.asList(collector1, collector2)));

        // When
        Uni<List<Collector>> result = useCase.findNearbyCollectors(requestId);

        // Then
        assertNotNull(result);
        result.subscribe().with(collectors -> {
            assertNotNull(collectors);
            assertEquals(2, collectors.size());
        });

        verify(collectionRequestPort).findById(requestId);
        verify(addressPort).findById("addr-001");
        verify(collectorDiscoveryPort).findCollectorsAcceptingMaterials(Arrays.asList("mat-001", "mat-002"));
    }
}
