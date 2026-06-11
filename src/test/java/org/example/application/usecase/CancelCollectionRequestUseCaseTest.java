package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.EventPort;
import org.example.domain.entity.CollectionRequest;
import org.example.infrastructure.event.CollectionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelCollectionRequestUseCaseTest {

    private CancelCollectionRequestUseCase useCase;

    @Mock
    private CollectionRequestPort collectionRequestPort;

    @Mock
    private EventPort eventPort;

    @Mock
    private CollectionStatusNotificationUseCase notificationUseCase;

    @BeforeEach
    void setUp() {
        useCase = new CancelCollectionRequestUseCase();
        useCase.collectionRequestPort = collectionRequestPort;
        useCase.eventPort = eventPort;
        useCase.notificationUseCase = notificationUseCase;
    }

    @Test
    void generatorCancelsOwnPendingRequest() {
        CollectionRequest request = request(CollectionRequest.Status.PENDING, "generator-1", null);
        when(collectionRequestPort.findById("request-1")).thenReturn(Uni.createFrom().item(request));
        when(collectionRequestPort.update(request)).thenReturn(Uni.createFrom().voidItem());
        when(eventPort.publishCollectionEvent(org.mockito.ArgumentMatchers.any())).thenReturn(Uni.createFrom().voidItem());
        when(notificationUseCase.notifyGenerator(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Uni.createFrom().voidItem());

        useCase.cancelByGenerator(" request-1 ", " generator-1 ").await().indefinitely();

        assertEquals(CollectionRequest.Status.CANCELLED, request.getStatus());
        verify(collectionRequestPort).update(request);
        CollectionEvent event = publishedEvent();
        assertEquals("COLLECTION_CANCELLED", event.getEventType());
        assertEquals("request-1", event.getRequestId());
        assertEquals("generator-1", event.getGeneratorId());
        assertEquals("CANCELLED", event.getStatus());
        assertEquals("GENERATOR", event.getActorType());
        assertEquals("generator-1", event.getActorId());
    }

    @Test
    void generatorCancelsOwnInProgressRequest() {
        CollectionRequest request = request(CollectionRequest.Status.IN_PROGRESS, "generator-1", "collector-1");
        when(collectionRequestPort.findById("request-1")).thenReturn(Uni.createFrom().item(request));
        when(collectionRequestPort.update(request)).thenReturn(Uni.createFrom().voidItem());
        when(eventPort.publishCollectionEvent(org.mockito.ArgumentMatchers.any())).thenReturn(Uni.createFrom().voidItem());
        when(notificationUseCase.notifyGenerator(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Uni.createFrom().voidItem());

        useCase.cancelByGenerator("request-1", "generator-1").await().indefinitely();

        assertEquals(CollectionRequest.Status.CANCELLED, request.getStatus());
        CollectionEvent event = publishedEvent();
        assertEquals("collector-1", event.getCollectorId());
        assertEquals("GENERATOR", event.getActorType());
    }

    @Test
    void collectorCancelsAssignedPendingRequest() {
        CollectionRequest request = request(CollectionRequest.Status.PENDING, "generator-1", "collector-1");
        when(collectionRequestPort.findById("request-1")).thenReturn(Uni.createFrom().item(request));
        when(collectionRequestPort.update(request)).thenReturn(Uni.createFrom().voidItem());
        when(eventPort.publishCollectionEvent(org.mockito.ArgumentMatchers.any())).thenReturn(Uni.createFrom().voidItem());
        when(notificationUseCase.notifyGenerator(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Uni.createFrom().voidItem());

        useCase.cancelByCollector("request-1", "collector-1").await().indefinitely();

        assertEquals(CollectionRequest.Status.CANCELLED, request.getStatus());
        CollectionEvent event = publishedEvent();
        assertEquals("COLLECTOR", event.getActorType());
        assertEquals("collector-1", event.getActorId());
    }

    @Test
    void collectorCancelsAssignedInProgressRequest() {
        CollectionRequest request = request(CollectionRequest.Status.IN_PROGRESS, "generator-1", "collector-1");
        when(collectionRequestPort.findById("request-1")).thenReturn(Uni.createFrom().item(request));
        when(collectionRequestPort.update(request)).thenReturn(Uni.createFrom().voidItem());
        when(eventPort.publishCollectionEvent(org.mockito.ArgumentMatchers.any())).thenReturn(Uni.createFrom().voidItem());
        when(notificationUseCase.notifyGenerator(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Uni.createFrom().voidItem());

        useCase.cancelByCollector("request-1", "collector-1").await().indefinitely();

        assertEquals(CollectionRequest.Status.CANCELLED, request.getStatus());
        verify(collectionRequestPort).update(request);
    }

    @Test
    void completedRequestCannotBeCancelled() {
        CollectionRequest request = request(CollectionRequest.Status.COMPLETED, "generator-1", "collector-1");
        when(collectionRequestPort.findById("request-1")).thenReturn(Uni.createFrom().item(request));

        CollectionCancellationConflictException exception = assertThrows(CollectionCancellationConflictException.class,
                () -> useCase.cancelByGenerator("request-1", "generator-1").await().indefinitely());

        assertEquals("Request is already completed", exception.getMessage());
        verify(collectionRequestPort, never()).update(request);
        verify(eventPort, never()).publishCollectionEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cancelledRequestCannotBeCancelledAgain() {
        CollectionRequest request = request(CollectionRequest.Status.CANCELLED, "generator-1", "collector-1");
        when(collectionRequestPort.findById("request-1")).thenReturn(Uni.createFrom().item(request));

        CollectionCancellationConflictException exception = assertThrows(CollectionCancellationConflictException.class,
                () -> useCase.cancelByCollector("request-1", "collector-1").await().indefinitely());

        assertEquals("Request is already cancelled", exception.getMessage());
        verify(collectionRequestPort, never()).update(request);
    }

    @Test
    void wrongGeneratorCannotCancelRequest() {
        CollectionRequest request = request(CollectionRequest.Status.PENDING, "generator-1", null);
        when(collectionRequestPort.findById("request-1")).thenReturn(Uni.createFrom().item(request));

        CollectionCancellationForbiddenException exception = assertThrows(CollectionCancellationForbiddenException.class,
                () -> useCase.cancelByGenerator("request-1", "generator-2").await().indefinitely());

        assertEquals("Generator cannot cancel this request", exception.getMessage());
        verify(collectionRequestPort, never()).update(request);
    }

    @Test
    void wrongCollectorCannotCancelRequest() {
        CollectionRequest request = request(CollectionRequest.Status.IN_PROGRESS, "generator-1", "collector-1");
        when(collectionRequestPort.findById("request-1")).thenReturn(Uni.createFrom().item(request));

        CollectionCancellationForbiddenException exception = assertThrows(CollectionCancellationForbiddenException.class,
                () -> useCase.cancelByCollector("request-1", "collector-2").await().indefinitely());

        assertEquals("Collector cannot cancel this request", exception.getMessage());
        verify(collectionRequestPort, never()).update(request);
    }

    @Test
    void collectorCannotCancelUnassignedRequest() {
        CollectionRequest request = request(CollectionRequest.Status.PENDING, "generator-1", null);
        when(collectionRequestPort.findById("request-1")).thenReturn(Uni.createFrom().item(request));

        CollectionCancellationForbiddenException exception = assertThrows(CollectionCancellationForbiddenException.class,
                () -> useCase.cancelByCollector("request-1", "collector-1").await().indefinitely());

        assertEquals("Collector cannot cancel this request", exception.getMessage());
    }

    @Test
    void missingRequestFailsNotFound() {
        when(collectionRequestPort.findById("missing")).thenReturn(Uni.createFrom().nullItem());

        CollectionRequestNotFoundException exception = assertThrows(CollectionRequestNotFoundException.class,
                () -> useCase.cancelByGenerator("missing", "generator-1").await().indefinitely());

        assertEquals("Request not found: missing", exception.getMessage());
    }

    @Test
    void blankIdsFailBeforeRepositoryCall() {
        IllegalArgumentException requestException = assertThrows(IllegalArgumentException.class,
                () -> useCase.cancelByGenerator(" ", "generator-1"));
        IllegalArgumentException actorException = assertThrows(IllegalArgumentException.class,
                () -> useCase.cancelByCollector("request-1", " "));

        assertEquals("request id is required", requestException.getMessage());
        assertEquals("collector id is required", actorException.getMessage());
        verify(collectionRequestPort, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    private CollectionEvent publishedEvent() {
        ArgumentCaptor<CollectionEvent> captor = ArgumentCaptor.forClass(CollectionEvent.class);
        verify(eventPort).publishCollectionEvent(captor.capture());
        return captor.getValue();
    }

    private CollectionRequest request(CollectionRequest.Status status, String generatorId, String collectorId) {
        CollectionRequest request = new CollectionRequest(generatorId, "address-1", List.of("paper"), 10.0);
        request.setId("request-1");
        request.setStatus(status);
        request.setSelectedCollectorId(collectorId);
        return request;
    }
}
