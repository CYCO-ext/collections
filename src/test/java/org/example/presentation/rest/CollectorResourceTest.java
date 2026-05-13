package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import org.example.application.usecase.CancelCollectionRequestUseCase;
import org.example.application.usecase.CollectionCancellationConflictException;
import org.example.application.usecase.CollectionCancellationForbiddenException;
import org.example.application.usecase.CollectionRequestNotFoundException;
import org.example.application.usecase.CollectorAddressNotFoundException;
import org.example.application.usecase.CollectorNotFoundException;
import org.example.application.usecase.CollectorResponseUseCase;
import org.example.application.usecase.CollectorSelectionUseCase;
import org.example.application.usecase.GetCollectorAddressUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectorResourceTest {
    private CollectorResource resource;
    private CollectorSelectionUseCase collectorSelectionUseCase;
    private CollectorResponseUseCase collectorResponseUseCase;
    private GetCollectorAddressUseCase getCollectorAddressUseCase;
    private CancelCollectionRequestUseCase cancelCollectionRequestUseCase;

    @BeforeEach
    void setUp() {
        collectorSelectionUseCase = mock(CollectorSelectionUseCase.class);
        collectorResponseUseCase = mock(CollectorResponseUseCase.class);
        getCollectorAddressUseCase = mock(GetCollectorAddressUseCase.class);
        cancelCollectionRequestUseCase = mock(CancelCollectionRequestUseCase.class);
        resource = new CollectorResource();
        resource.collectorSelectionUseCase = collectorSelectionUseCase;
        resource.collectorResponseUseCase = collectorResponseUseCase;
        resource.getCollectorAddressUseCase = getCollectorAddressUseCase;
        resource.cancelCollectionRequestUseCase = cancelCollectionRequestUseCase;
    }

    @Test
    void getCollectorAddressReturnsUseCaseResult() {
        GetCollectorAddressUseCase.CollectorAddressResult result = addressResult();
        when(getCollectorAddressUseCase.getAddress("collector-1")).thenReturn(Uni.createFrom().item(result));

        Response response = resource.getCollectorAddress("collector-1").await().indefinitely();

        assertEquals(200, response.getStatus());
        assertEquals(result, response.getEntity());
        verify(getCollectorAddressUseCase).getAddress("collector-1");
    }

    @Test
    void getCollectorAddressReturnsBadRequestForValidationError() {
        when(getCollectorAddressUseCase.getAddress(" ")).thenReturn(Uni.createFrom().failure(
                new IllegalArgumentException("collector id is required")));

        Response response = resource.getCollectorAddress(" ").await().indefinitely();

        assertEquals(400, response.getStatus());
        assertEquals("collector id is required", response.getEntity());
    }

    @Test
    void getCollectorAddressReturnsNotFoundForMissingCollector() {
        when(getCollectorAddressUseCase.getAddress("missing")).thenReturn(Uni.createFrom().failure(
                new CollectorNotFoundException("missing")));

        Response response = resource.getCollectorAddress("missing").await().indefinitely();

        assertEquals(404, response.getStatus());
        assertEquals("Collector not found: missing", response.getEntity());
    }

    @Test
    void getCollectorAddressReturnsNotFoundForMissingAddress() {
        when(getCollectorAddressUseCase.getAddress("collector-1")).thenReturn(Uni.createFrom().failure(
                new CollectorAddressNotFoundException("collector-1")));

        Response response = resource.getCollectorAddress("collector-1").await().indefinitely();

        assertEquals(404, response.getStatus());
        assertEquals("Collector address not found: collector-1", response.getEntity());
    }

    @Test
    void acceptRequestStillDelegatesToCollectorResponseUseCase() {
        when(collectorResponseUseCase.acceptRequest("request-1")).thenReturn(Uni.createFrom().voidItem());

        Response response = resource.acceptRequest("request-1").await().indefinitely();

        assertEquals(200, response.getStatus());
        verify(collectorResponseUseCase).acceptRequest("request-1");
    }

    @Test
    void cancelRequestDelegatesToUseCase() {
        CollectorResource.CancelRequestDTO dto = cancelDto("collector-1");
        when(cancelCollectionRequestUseCase.cancelByCollector("request-1", "collector-1")).thenReturn(Uni.createFrom().voidItem());

        Response response = resource.cancelRequest("request-1", dto).await().indefinitely();

        assertEquals(200, response.getStatus());
        verify(cancelCollectionRequestUseCase).cancelByCollector("request-1", "collector-1");
    }

    @Test
    void cancelRequestReturnsBadRequestForValidationError() {
        CollectorResource.CancelRequestDTO dto = cancelDto(" ");
        when(cancelCollectionRequestUseCase.cancelByCollector("request-1", " ")).thenReturn(Uni.createFrom().failure(
                new IllegalArgumentException("collector id is required")));

        Response response = resource.cancelRequest("request-1", dto).await().indefinitely();

        assertEquals(400, response.getStatus());
        assertEquals("collector id is required", response.getEntity());
    }

    @Test
    void cancelRequestReturnsNotFoundForMissingRequest() {
        CollectorResource.CancelRequestDTO dto = cancelDto("collector-1");
        when(cancelCollectionRequestUseCase.cancelByCollector("missing", "collector-1")).thenReturn(Uni.createFrom().failure(
                new CollectionRequestNotFoundException("missing")));

        Response response = resource.cancelRequest("missing", dto).await().indefinitely();

        assertEquals(404, response.getStatus());
        assertEquals("Request not found: missing", response.getEntity());
    }

    @Test
    void cancelRequestReturnsForbiddenForWrongCollector() {
        CollectorResource.CancelRequestDTO dto = cancelDto("collector-2");
        when(cancelCollectionRequestUseCase.cancelByCollector("request-1", "collector-2")).thenReturn(Uni.createFrom().failure(
                new CollectionCancellationForbiddenException("Collector cannot cancel this request")));

        Response response = resource.cancelRequest("request-1", dto).await().indefinitely();

        assertEquals(403, response.getStatus());
        assertEquals("Collector cannot cancel this request", response.getEntity());
    }

    @Test
    void cancelRequestReturnsConflictForTerminalStatus() {
        CollectorResource.CancelRequestDTO dto = cancelDto("collector-1");
        when(cancelCollectionRequestUseCase.cancelByCollector("request-1", "collector-1")).thenReturn(Uni.createFrom().failure(
                new CollectionCancellationConflictException("Request is already cancelled")));

        Response response = resource.cancelRequest("request-1", dto).await().indefinitely();

        assertEquals(409, response.getStatus());
        assertEquals("Request is already cancelled", response.getEntity());
    }

    private CollectorResource.CancelRequestDTO cancelDto(String collectorId) {
        CollectorResource.CancelRequestDTO dto = new CollectorResource.CancelRequestDTO();
        dto.setCollectorId(collectorId);
        return dto;
    }

    private GetCollectorAddressUseCase.CollectorAddressResult addressResult() {
        return new GetCollectorAddressUseCase.CollectorAddressResult(
                "collector-1",
                "address-1",
                "Main St",
                "100",
                "Sao Paulo",
                "SP",
                "01000-000",
                -23.5505,
                -46.6333,
                "ENRICHED",
                "nominatim"
        );
    }
}
