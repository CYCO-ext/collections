package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import org.example.application.usecase.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class GeneratorResourceTest {
    private GeneratorResource resource;
    private CollectionRequestUseCase collectionRequestUseCase;
    private CancelCollectionRequestUseCase cancelCollectionRequestUseCase;

    @BeforeEach
    void setUp() {
        collectionRequestUseCase = mock(CollectionRequestUseCase.class);
        cancelCollectionRequestUseCase = mock(CancelCollectionRequestUseCase.class);
        resource = new GeneratorResource();
        resource.collectionRequestUseCase = collectionRequestUseCase;
        resource.cancelCollectionRequestUseCase = cancelCollectionRequestUseCase;
    }

    @Test
    void getNearbyCollectorsReturnsNotFoundForMissingAddress() {
        when(collectionRequestUseCase.findNearbyCollectors("request-1"))
                .thenReturn(Uni.createFrom().failure(new IllegalArgumentException("Address not found: addr-1")));

        Response response = resource.getNearbyCollectors("request-1").await().indefinitely();

        assertEquals(404, response.getStatus());
        assertEquals("Address not found: addr-1", response.getEntity());
    }

    @Test
    void getNearbyCollectorsReturnsNotFoundForMissingRequest() {
        when(collectionRequestUseCase.findNearbyCollectors("missing"))
                .thenReturn(Uni.createFrom().failure(new IllegalArgumentException("Request not found: missing")));

        Response response = resource.getNearbyCollectors("missing").await().indefinitely();

        assertEquals(404, response.getStatus());
        assertEquals("Request not found: missing", response.getEntity());
    }

    @Test
    void cancelRequestDelegatesToUseCase() {
        GeneratorResource.CancelRequestDTO dto = cancelDto("generator-1");
        when(cancelCollectionRequestUseCase.cancelByGenerator("request-1", "generator-1")).thenReturn(Uni.createFrom().voidItem());

        Response response = resource.cancelRequest("request-1", dto).await().indefinitely();

        assertEquals(200, response.getStatus());
        verify(cancelCollectionRequestUseCase).cancelByGenerator("request-1", "generator-1");
    }

    @Test
    void cancelRequestReturnsBadRequestForValidationError() {
        GeneratorResource.CancelRequestDTO dto = cancelDto(" ");
        when(cancelCollectionRequestUseCase.cancelByGenerator("request-1", " ")).thenReturn(Uni.createFrom().failure(
                new IllegalArgumentException("generator id is required")));

        Response response = resource.cancelRequest("request-1", dto).await().indefinitely();

        assertEquals(400, response.getStatus());
        assertEquals("generator id is required", response.getEntity());
    }

    @Test
    void cancelRequestReturnsNotFoundForMissingRequest() {
        GeneratorResource.CancelRequestDTO dto = cancelDto("generator-1");
        when(cancelCollectionRequestUseCase.cancelByGenerator("missing", "generator-1")).thenReturn(Uni.createFrom().failure(
                new CollectionRequestNotFoundException("missing")));

        Response response = resource.cancelRequest("missing", dto).await().indefinitely();

        assertEquals(404, response.getStatus());
        assertEquals("Request not found: missing", response.getEntity());
    }

    @Test
    void cancelRequestReturnsForbiddenForWrongGenerator() {
        GeneratorResource.CancelRequestDTO dto = cancelDto("generator-2");
        when(cancelCollectionRequestUseCase.cancelByGenerator("request-1", "generator-2")).thenReturn(Uni.createFrom().failure(
                new CollectionCancellationForbiddenException("Generator cannot cancel this request")));

        Response response = resource.cancelRequest("request-1", dto).await().indefinitely();

        assertEquals(403, response.getStatus());
        assertEquals("Generator cannot cancel this request", response.getEntity());
    }

    @Test
    void cancelRequestReturnsConflictForTerminalStatus() {
        GeneratorResource.CancelRequestDTO dto = cancelDto("generator-1");
        when(cancelCollectionRequestUseCase.cancelByGenerator("request-1", "generator-1")).thenReturn(Uni.createFrom().failure(
                new CollectionCancellationConflictException("Request is already completed")));

        Response response = resource.cancelRequest("request-1", dto).await().indefinitely();

        assertEquals(409, response.getStatus());
        assertEquals("Request is already completed", response.getEntity());
    }

    private GeneratorResource.CancelRequestDTO cancelDto(String generatorId) {
        GeneratorResource.CancelRequestDTO dto = new GeneratorResource.CancelRequestDTO();
        dto.setGeneratorId(generatorId);
        return dto;
    }
}
