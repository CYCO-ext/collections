package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.AddressPort;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.usecase.SearchCollectionsUseCase.CollectionSearchResult;
import org.example.domain.entity.Address;
import org.example.domain.entity.CollectionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCollectionByIdUseCaseTest {

    private GetCollectionByIdUseCase useCase;
    private SearchCollectionsUseCase searchCollectionsUseCase;

    @Mock
    private CollectionRequestPort collectionRequestPort;

    @Mock
    private AddressPort addressPort;

    @BeforeEach
    void setUp() {
        searchCollectionsUseCase = new SearchCollectionsUseCase();
        searchCollectionsUseCase.addressPort = addressPort;
        useCase = new GetCollectionByIdUseCase();
        useCase.collectionRequestPort = collectionRequestPort;
        useCase.searchCollectionsUseCase = searchCollectionsUseCase;
    }

    @Test
    void getByIdReturnsMappedCollectionWithAddress() {
        CollectionRequest request = request("request-1", "address-1");
        when(collectionRequestPort.findById("request-1")).thenReturn(Uni.createFrom().item(request));
        when(addressPort.findById("address-1")).thenReturn(Uni.createFrom().item(address("address-1")));

        CollectionSearchResult result = useCase.getById(" request-1 ").await().indefinitely();

        assertEquals("request-1", result.id());
        assertEquals("generator-1", result.generatorId());
        assertEquals("address-1", result.addressId());
        assertEquals("Main St", result.address().street());
        assertEquals("100", result.address().number());
        assertEquals("Sao Paulo", result.address().city());
        assertEquals("SP", result.address().state());
        assertEquals("01000-000", result.address().zipCode());
        assertEquals(CollectionRequest.Status.IN_PROGRESS, result.status());
        verify(collectionRequestPort).findById("request-1");
        verify(addressPort).findById("address-1");
        verify(collectionRequestPort, never()).update(request);
    }

    @Test
    void getByIdRejectsBlankIdBeforeRepositoryCall() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> useCase.getById("  "));

        assertEquals("collection id is required", exception.getMessage());
        verify(collectionRequestPort, never()).findById(org.mockito.ArgumentMatchers.any());
        verify(addressPort, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getByIdFailsWhenCollectionIsMissing() {
        when(collectionRequestPort.findById("missing")).thenReturn(Uni.createFrom().nullItem());

        CollectionNotFoundException exception = assertThrows(CollectionNotFoundException.class,
                () -> useCase.getById("missing").await().indefinitely());

        assertEquals("Collection request not found: missing", exception.getMessage());
        verify(collectionRequestPort).findById("missing");
        verify(addressPort, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getByIdFailsWhenAddressIsMissing() {
        CollectionRequest request = request("request-1", "missing-address");
        when(collectionRequestPort.findById("request-1")).thenReturn(Uni.createFrom().item(request));
        when(addressPort.findById("missing-address")).thenReturn(Uni.createFrom().nullItem());

        CollectionAddressNotFoundException exception = assertThrows(CollectionAddressNotFoundException.class,
                () -> useCase.getById("request-1").await().indefinitely());

        assertEquals("Collection address not found: missing-address", exception.getMessage());
        verify(addressPort).findById("missing-address");
    }

    private CollectionRequest request(String id, String addressId) {
        LocalDateTime now = LocalDateTime.now();
        CollectionRequest request = new CollectionRequest("generator-1", addressId, List.of("paper"), 10.0);
        request.setId(id);
        request.setStatus(CollectionRequest.Status.IN_PROGRESS);
        request.setSelectedCollectorId("collector-1");
        request.setCreatedAt(now);
        request.setUpdatedAt(now.plusMinutes(5));
        return request;
    }

    private Address address(String id) {
        return new Address(id, "Main St", "Sao Paulo", "01000-000", "100", "SP", -23.5505, -46.6333, "ENRICHED", "nominatim");
    }
}
