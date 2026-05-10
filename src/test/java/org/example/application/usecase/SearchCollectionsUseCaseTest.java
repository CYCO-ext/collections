package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.usecase.SearchCollectionsUseCase.CollectionSearchQuery;
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

    @BeforeEach
    void setUp() {
        useCase = new SearchCollectionsUseCase();
        useCase.collectionRequestPort = collectionRequestPort;
    }

    @Test
    void searchWithoutFiltersDelegatesToAllStatusSearch() {
        CollectionSearchQuery query = new CollectionSearchQuery(null, null, null);
        CollectionRequest request = request("request-1", CollectionRequest.Status.PENDING, LocalDateTime.now());
        when(collectionRequestPort.search(query)).thenReturn(Uni.createFrom().item(List.of(request)));

        List<SearchCollectionsUseCase.CollectionSearchResult> results = useCase.search(null, null, null).await().indefinitely();

        assertEquals(1, results.size());
        assertEquals("request-1", results.getFirst().id());
        assertEquals(CollectionRequest.Status.PENDING, results.getFirst().status());
        verify(collectionRequestPort).search(query);
        verify(collectionRequestPort, never()).update(request);
    }

    @Test
    void searchWithValidStatusDelegatesWithParsedStatus() {
        CollectionSearchQuery query = new CollectionSearchQuery(CollectionRequest.Status.IN_PROGRESS, null, null);
        CollectionRequest request = request("request-2", CollectionRequest.Status.IN_PROGRESS, LocalDateTime.now());
        when(collectionRequestPort.search(query)).thenReturn(Uni.createFrom().item(List.of(request)));

        List<SearchCollectionsUseCase.CollectionSearchResult> results = useCase.search("in_progress", null, null).await().indefinitely();

        assertEquals(1, results.size());
        assertEquals("request-2", results.getFirst().id());
        assertEquals(CollectionRequest.Status.IN_PROGRESS, results.getFirst().status());
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
    }

    private CollectionRequest request(String id, CollectionRequest.Status status, LocalDateTime createdAt) {
        CollectionRequest request = new CollectionRequest("generator-1", "address-1", List.of("paper"), 10.0);
        request.setId(id);
        request.setStatus(status);
        request.setSelectedCollectorId("collector-1");
        request.setCreatedAt(createdAt);
        request.setUpdatedAt(createdAt.plusMinutes(5));
        return request;
    }
}
