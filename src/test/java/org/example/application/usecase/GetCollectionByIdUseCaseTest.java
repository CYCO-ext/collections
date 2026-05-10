package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.usecase.SearchCollectionsUseCase.CollectionSearchResult;
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

    @Mock
    private CollectionRequestPort collectionRequestPort;

    @BeforeEach
    void setUp() {
        useCase = new GetCollectionByIdUseCase();
        useCase.collectionRequestPort = collectionRequestPort;
    }

    @Test
    void getByIdReturnsMappedCollection() {
        CollectionRequest request = request("request-1");
        when(collectionRequestPort.findById("request-1")).thenReturn(Uni.createFrom().item(request));

        CollectionSearchResult result = useCase.getById(" request-1 ").await().indefinitely();

        assertEquals("request-1", result.id());
        assertEquals("generator-1", result.generatorId());
        assertEquals("address-1", result.addressId());
        assertEquals(CollectionRequest.Status.IN_PROGRESS, result.status());
        verify(collectionRequestPort).findById("request-1");
        verify(collectionRequestPort, never()).update(request);
    }

    @Test
    void getByIdRejectsBlankIdBeforeRepositoryCall() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> useCase.getById("  "));

        assertEquals("collection id is required", exception.getMessage());
        verify(collectionRequestPort, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getByIdFailsWhenCollectionIsMissing() {
        when(collectionRequestPort.findById("missing")).thenReturn(Uni.createFrom().nullItem());

        CollectionNotFoundException exception = assertThrows(CollectionNotFoundException.class,
                () -> useCase.getById("missing").await().indefinitely());

        assertEquals("Collection request not found: missing", exception.getMessage());
        verify(collectionRequestPort).findById("missing");
    }

    private CollectionRequest request(String id) {
        LocalDateTime now = LocalDateTime.now();
        CollectionRequest request = new CollectionRequest("generator-1", "address-1", List.of("paper"), 10.0);
        request.setId(id);
        request.setStatus(CollectionRequest.Status.IN_PROGRESS);
        request.setSelectedCollectorId("collector-1");
        request.setCreatedAt(now);
        request.setUpdatedAt(now.plusMinutes(5));
        return request;
    }
}
