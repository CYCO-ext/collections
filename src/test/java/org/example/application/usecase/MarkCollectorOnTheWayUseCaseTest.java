package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.EventPort;
import org.example.domain.entity.CollectionRequest;
import org.example.infrastructure.event.CollectionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarkCollectorOnTheWayUseCaseTest {

    private MarkCollectorOnTheWayUseCase useCase;
    private CollectionRequestPort collectionRequestPort;
    private EventPort eventPort;
    private CollectionStatusNotificationUseCase notificationUseCase;

    @BeforeEach
    void setUp() {
        collectionRequestPort = mock(CollectionRequestPort.class);
        eventPort = mock(EventPort.class);
        notificationUseCase = mock(CollectionStatusNotificationUseCase.class);
        useCase = new MarkCollectorOnTheWayUseCase();
        useCase.collectionRequestPort = collectionRequestPort;
        useCase.eventPort = eventPort;
        useCase.notificationUseCase = notificationUseCase;
    }

    @Test
    void marksAssignedInProgressRequestOnTheWay() {
        CollectionRequest request = request(CollectionRequest.Status.IN_PROGRESS, "collector-1");
        when(collectionRequestPort.findById("request-1")).thenReturn(Uni.createFrom().item(request));
        when(collectionRequestPort.update(request)).thenReturn(Uni.createFrom().voidItem());
        when(eventPort.publishCollectionEvent(org.mockito.ArgumentMatchers.any())).thenReturn(Uni.createFrom().voidItem());
        when(notificationUseCase.notifyGenerator(request, "COLLECTION_ON_THE_WAY")).thenReturn(Uni.createFrom().voidItem());

        useCase.markOnTheWay(" request-1 ", " collector-1 ").await().indefinitely();

        assertEquals(CollectionRequest.Status.ON_THE_WAY, request.getStatus());
        CollectionEvent event = publishedEvent();
        assertEquals("COLLECTION_ON_THE_WAY", event.getEventType());
        assertEquals("request-1", event.getRequestId());
        assertEquals("collector-1", event.getCollectorId());
        assertEquals("ON_THE_WAY", event.getStatus());
        verify(notificationUseCase).notifyGenerator(request, "COLLECTION_ON_THE_WAY");
    }

    @Test
    void wrongCollectorCannotMarkOnTheWay() {
        CollectionRequest request = request(CollectionRequest.Status.IN_PROGRESS, "collector-1");
        when(collectionRequestPort.findById("request-1")).thenReturn(Uni.createFrom().item(request));

        CollectionCancellationForbiddenException exception = assertThrows(CollectionCancellationForbiddenException.class,
                () -> useCase.markOnTheWay("request-1", "collector-2").await().indefinitely());

        assertEquals("Collector cannot update this request", exception.getMessage());
        verify(collectionRequestPort, never()).update(request);
    }

    @Test
    void pendingRequestCannotBeMarkedOnTheWay() {
        CollectionRequest request = request(CollectionRequest.Status.PENDING, "collector-1");
        when(collectionRequestPort.findById("request-1")).thenReturn(Uni.createFrom().item(request));

        CollectionCancellationConflictException exception = assertThrows(CollectionCancellationConflictException.class,
                () -> useCase.markOnTheWay("request-1", "collector-1").await().indefinitely());

        assertEquals("Request cannot be marked on the way from status: PENDING", exception.getMessage());
        verify(collectionRequestPort, never()).update(request);
    }

    private CollectionEvent publishedEvent() {
        ArgumentCaptor<CollectionEvent> captor = ArgumentCaptor.forClass(CollectionEvent.class);
        verify(eventPort).publishCollectionEvent(captor.capture());
        return captor.getValue();
    }

    private CollectionRequest request(CollectionRequest.Status status, String collectorId) {
        CollectionRequest request = new CollectionRequest("generator-1", "address-1", List.of("paper"), 10.0);
        request.setId("request-1");
        request.setStatus(status);
        request.setSelectedCollectorId(collectorId);
        return request;
    }
}
