package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import org.example.application.usecase.CollectionNotFoundException;
import org.example.application.usecase.GetCollectionByIdUseCase;
import org.example.application.usecase.SearchCollectionsUseCase;
import org.example.domain.entity.CollectionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectionSearchResourceTest {
    private CollectionSearchResource resource;
    private SearchCollectionsUseCase searchUseCase;
    private GetCollectionByIdUseCase getByIdUseCase;

    @BeforeEach
    void setUp() {
        searchUseCase = mock(SearchCollectionsUseCase.class);
        getByIdUseCase = mock(GetCollectionByIdUseCase.class);
        resource = new CollectionSearchResource();
        resource.searchCollectionsUseCase = searchUseCase;
        resource.getCollectionByIdUseCase = getByIdUseCase;
    }

    @Test
    void searchReturnsUseCaseResults() {
        SearchCollectionsUseCase.CollectionSearchResult newest = result("request-new", CollectionRequest.Status.PENDING, LocalDateTime.now());
        SearchCollectionsUseCase.CollectionSearchResult oldest = result("request-old", CollectionRequest.Status.PENDING, LocalDateTime.now().minusDays(1));
        when(searchUseCase.search(null, null, null)).thenReturn(Uni.createFrom().item(List.of(newest, oldest)));

        Response response = resource.search(null, null, null).await().indefinitely();

        assertEquals(200, response.getStatus());
        List<?> results = (List<?>) response.getEntity();
        assertEquals(2, results.size());
        assertEquals(newest, results.getFirst());
        verify(searchUseCase).search(null, null, null);
    }

    @Test
    void searchPassesAllFiltersToUseCase() {
        when(searchUseCase.search("PENDING", "collector-1", "generator-1")).thenReturn(Uni.createFrom().item(List.of()));

        Response response = resource.search("PENDING", "collector-1", "generator-1").await().indefinitely();

        assertEquals(200, response.getStatus());
        verify(searchUseCase).search("PENDING", "collector-1", "generator-1");
    }

    @Test
    void searchReturnsBadRequestForInvalidStatus() {
        when(searchUseCase.search("ARCHIVED", null, null)).thenReturn(Uni.createFrom().failure(
                new IllegalArgumentException("Invalid collection request status: ARCHIVED")));

        Response response = resource.search("ARCHIVED", null, null).await().indefinitely();

        assertEquals(400, response.getStatus());
        assertEquals("Invalid collection request status: ARCHIVED", response.getEntity());
    }

    @Test
    void getByIdReturnsUseCaseResult() {
        SearchCollectionsUseCase.CollectionSearchResult result = result("request-1", CollectionRequest.Status.IN_PROGRESS, LocalDateTime.now());
        when(getByIdUseCase.getById("request-1")).thenReturn(Uni.createFrom().item(result));

        Response response = resource.getById("request-1").await().indefinitely();

        assertEquals(200, response.getStatus());
        assertEquals(result, response.getEntity());
        verify(getByIdUseCase).getById("request-1");
    }

    @Test
    void getByIdReturnsBadRequestForValidationError() {
        when(getByIdUseCase.getById(" ")).thenReturn(Uni.createFrom().failure(
                new IllegalArgumentException("collection id is required")));

        Response response = resource.getById(" ").await().indefinitely();

        assertEquals(400, response.getStatus());
        assertEquals("collection id is required", response.getEntity());
    }

    @Test
    void getByIdReturnsNotFoundForMissingCollection() {
        when(getByIdUseCase.getById("missing")).thenReturn(Uni.createFrom().failure(
                new CollectionNotFoundException("missing")));

        Response response = resource.getById("missing").await().indefinitely();

        assertEquals(404, response.getStatus());
        assertEquals("Collection request not found: missing", response.getEntity());
    }

    private SearchCollectionsUseCase.CollectionSearchResult result(String id, CollectionRequest.Status status, LocalDateTime createdAt) {
        return new SearchCollectionsUseCase.CollectionSearchResult(
                id,
                "generator-1",
                "address-1",
                List.of("paper"),
                10.0,
                status,
                "collector-1",
                false,
                false,
                createdAt,
                createdAt.plusMinutes(5)
        );
    }
}
