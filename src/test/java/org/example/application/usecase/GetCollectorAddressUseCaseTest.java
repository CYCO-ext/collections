package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.CollectorDiscoveryPort;
import org.example.domain.entity.Address;
import org.example.domain.entity.Collector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCollectorAddressUseCaseTest {

    private GetCollectorAddressUseCase useCase;

    @Mock
    private CollectorDiscoveryPort collectorDiscoveryPort;

    @BeforeEach
    void setUp() {
        useCase = new GetCollectorAddressUseCase();
        useCase.collectorDiscoveryPort = collectorDiscoveryPort;
    }

    @Test
    void getAddressReturnsEmbeddedCollectorAddress() {
        Collector collector = collector("collector-1", address());
        when(collectorDiscoveryPort.findCollectorById("collector-1")).thenReturn(Uni.createFrom().item(collector));

        GetCollectorAddressUseCase.CollectorAddressResult result = useCase.getAddress(" collector-1 ").await().indefinitely();

        assertEquals("collector-1", result.collectorId());
        assertEquals("address-1", result.addressId());
        assertEquals("Main St", result.street());
        assertEquals("100", result.number());
        assertEquals("Sao Paulo", result.city());
        assertEquals("SP", result.state());
        assertEquals("01000-000", result.zipCode());
        assertEquals(-23.5505, result.latitude());
        assertEquals(-46.6333, result.longitude());
        assertEquals("ENRICHED", result.enrichmentStatus());
        assertEquals("nominatim", result.enrichmentSource());
        verify(collectorDiscoveryPort).findCollectorById("collector-1");
    }

    @Test
    void getAddressRejectsBlankCollectorIdBeforeLookup() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> useCase.getAddress("  "));

        assertEquals("collector id is required", exception.getMessage());
        verify(collectorDiscoveryPort, never()).findCollectorById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getAddressFailsWhenCollectorDoesNotExist() {
        when(collectorDiscoveryPort.findCollectorById("missing")).thenReturn(Uni.createFrom().nullItem());

        CollectorNotFoundException exception = assertThrows(CollectorNotFoundException.class,
                () -> useCase.getAddress("missing").await().indefinitely());

        assertEquals("Collector not found: missing", exception.getMessage());
        verify(collectorDiscoveryPort).findCollectorById("missing");
    }

    @Test
    void getAddressFailsWhenCollectorHasNoAddress() {
        Collector collector = collector("collector-1", null);
        when(collectorDiscoveryPort.findCollectorById("collector-1")).thenReturn(Uni.createFrom().item(collector));

        CollectorAddressNotFoundException exception = assertThrows(CollectorAddressNotFoundException.class,
                () -> useCase.getAddress("collector-1").await().indefinitely());

        assertEquals("Collector address not found: collector-1", exception.getMessage());
    }

    private Collector collector(String collectorId, Address address) {
        return new Collector(collectorId, "user-1", "Collector 1", address, List.of("paper"), 0.95);
    }

    private Address address() {
        return new Address(
                "address-1",
                "Main St",
                "Sao Paulo",
                "01000-000",
                "100",
                "SP",
                -23.5505,
                -46.6333,
                "ENRICHED",
                "nominatim"
        );
    }
}
