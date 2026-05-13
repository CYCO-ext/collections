package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.AddressPort;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.usecase.SearchCollectionsUseCase.CollectionSearchQuery;
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
class SearchCollectionsUseCaseTest {

    private SearchCollectionsUseCase useCase;

    @Mock
    private CollectionRequestPort collectionRequestPort;

    @Mock
    private AddressPort addressPort;

    @BeforeEach
    void setUp() {
        useCase = new SearchCollectionsUseCase();
        useCase.collectionRequestPort = collectionRequestPort;
        useCase.addressPort = addressPort;
    }

    @Test
    void searchWithoutFiltersDelegatesToAllStatusSearchAndEnrichesAddress() {
        CollectionSearchQuery query = new CollectionSearchQuery(null, null, null);
        CollectionRequest request = request("request-1", "address-1", CollectionRequest.Status.PENDING, LocalDateTime.now());
        when(collectionRequestPort.search(query)).thenReturn(Uni.createFrom().item(List.of(request)));
        when(addressPort.findById("address-1")).thenReturn(Uni.createFrom().item(address("address-1")));

        List<SearchCollectionsUseCase.CollectionSearchResult> results = useCase.search(null, null, null).await().indefinitely();

        assertEquals(1, results.size());
        assertEquals("request-1", results.getFirst().id());
        assertEquals("address-1", results.getFirst().addressId());
        assertEquals("Main St", results.getFirst().address().street());
        assertEquals("100", results.getFirst().address().number());
        assertEquals("Sao Paulo", results.getFirst().address().city());
        assertEquals("SP", results.getFirst().address().state());
        assertEquals("01000-000", results.getFirst().address().zipCode());
        assertEquals(-23.5505, results.getFirst().address().latitude());
        assertEquals(-46.6333, results.getFirst().address().longitude());
        assertEquals("ENRICHED", results.getFirst().address().enrichmentStatus());
        assertEquals("nominatim", results.getFirst().address().enrichmentSource());
        assertEquals(CollectionRequest.Status.PENDING, results.getFirst().status());
        verify(collectionRequestPort).search(query);
        verify(addressPort).findById("address-1");
        verify(collectionRequestPort, never()).update(request);
    }

    @Test
    void searchWithValidStatusDelegatesWithParsedStatusAndPreservesOrder() {
        LocalDateTime now = LocalDateTime.now();
        CollectionSearchQuery query = new CollectionSearchQuery(CollectionRequest.Status.IN_PROGRESS, null, null);
        CollectionRequest newest = request("request-new", "address-new", CollectionRequest.Status.IN_PROGRESS, now);
        CollectionRequest oldest = request("request-old", "address-old", CollectionRequest.Status.IN_PROGRESS, now.minusDays(1));
        when(collectionRequestPort.search(query)).thenReturn(Uni.createFrom().item(List.of(newest, oldest)));
        when(addressPort.findById("address-new")).thenReturn(Uni.createFrom().item(address("address-new")));
        when(addressPort.findById("address-old")).thenReturn(Uni.createFrom().item(address("address-old")));

        List<SearchCollectionsUseCase.CollectionSearchResult> results = useCase.search("in_progress", null, null).await().indefinitely();

        assertEquals(2, results.size());
        assertEquals("request-new", results.get(0).id());
        assertEquals("request-old", results.get(1).id());
        assertEquals(CollectionRequest.Status.IN_PROGRESS, results.getFirst().status());
        verify(collectionRequestPort).search(query);
    }

    @Test
    void searchWithCancelledStatusDelegatesWithParsedStatus() {
        CollectionSearchQuery query = new CollectionSearchQuery(CollectionRequest.Status.CANCELLED, null, null);
        CollectionRequest request = request("request-cancelled", "address-cancelled", CollectionRequest.Status.CANCELLED, LocalDateTime.now());
        when(collectionRequestPort.search(query)).thenReturn(Uni.createFrom().item(List.of(request)));
        when(addressPort.findById("address-cancelled")).thenReturn(Uni.createFrom().item(address("address-cancelled")));

        List<SearchCollectionsUseCase.CollectionSearchResult> results = useCase.search("cancelled", null, null).await().indefinitely();

        assertEquals(1, results.size());
        assertEquals(CollectionRequest.Status.CANCELLED, results.getFirst().status());
        verify(collectionRequestPort).search(query);
    }

    @Test
    void searchWithCollectorAndGeneratorDelegatesWithTrimmedIds() {
        CollectionSearchQuery query = new CollectionSearchQuery(CollectionRequest.Status.COMPLETED, "collector-1", "generator-1");
        when(collectionRequestPort.search(query)).thenReturn(Uni.createFrom().item(List.of()));

        List<SearchCollectionsUseCase.CollectionSearchResult> results = useCase.search("COMPLETED", " collector-1 ", " generator-1 ").await().indefinitely();

        assertEquals(0, results.size());
        verify(collectionRequestPort).search(query);
    }

    @Test
    void searchWithInvalidStatusFailsBeforeRepositoryCall() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> useCase.search("ARCHIVED", null, null));

        assertEquals("Invalid collection request status: ARCHIVED", exception.getMessage());
        verify(collectionRequestPort, never()).search(org.mockito.ArgumentMatchers.any());
        verify(addressPort, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void searchFailsWhenAddressIsMissing() {
        CollectionSearchQuery query = new CollectionSearchQuery(null, null, null);
        CollectionRequest request = request("request-1", "missing-address", CollectionRequest.Status.PENDING, LocalDateTime.now());
        when(collectionRequestPort.search(query)).thenReturn(Uni.createFrom().item(List.of(request)));
        when(addressPort.findById("missing-address")).thenReturn(Uni.createFrom().nullItem());

        CollectionAddressNotFoundException exception = assertThrows(CollectionAddressNotFoundException.class,
                () -> useCase.search(null, null, null).await().indefinitely());

        assertEquals("Collection address not found: missing-address", exception.getMessage());
        verify(addressPort).findById("missing-address");
    }

    private CollectionRequest request(String id, String addressId, CollectionRequest.Status status, LocalDateTime createdAt) {
        CollectionRequest request = new CollectionRequest("generator-1", addressId, List.of("paper"), 10.0);
        request.setId(id);
        request.setStatus(status);
        request.setSelectedCollectorId("collector-1");
        request.setCreatedAt(createdAt);
        request.setUpdatedAt(createdAt.plusMinutes(5));
        return request;
    }

    private Address address(String id) {
        return new Address(id, "Main St", "Sao Paulo", "01000-000", "100", "SP", -23.5505, -46.6333, "ENRICHED", "nominatim");
    }
}
