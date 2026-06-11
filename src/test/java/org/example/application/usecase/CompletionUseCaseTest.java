package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.EventPort;
import org.example.domain.entity.CollectionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class CompletionUseCaseTest {

    @InjectMocks
    private CompletionUseCase useCase;

    @Mock
    private CollectionRequestPort collectionRequestPort;

    @Mock
    private EventPort eventPort;

    @Mock
    private CloseSavedRoutesUseCase closeSavedRoutesUseCase;

    @Mock
    private CollectionStatusNotificationUseCase notificationUseCase;

    @Test
    void testConfirmGeneratorCompletion() {
        String requestId = "req-001";
        CollectionRequest request = request(requestId);

        when(collectionRequestPort.findById(requestId))
                .thenReturn(Uni.createFrom().item(request));
        when(collectionRequestPort.update(any(CollectionRequest.class)))
                .thenReturn(Uni.createFrom().nullItem().replaceWithVoid());

        Uni<Void> result = useCase.confirmGeneratorCompletion(requestId);

        assertNotNull(result);
        result.await().indefinitely();

        verify(collectionRequestPort).findById(requestId);
        verify(collectionRequestPort).update(any(CollectionRequest.class));
        verify(eventPort, never()).publishCollectionEvent(any());
        verify(closeSavedRoutesUseCase, never()).closeRoutesContaining(any());
    }

    @Test
    void testConfirmCollectorCompletion() {
        String requestId = "req-001";
        CollectionRequest request = request(requestId);
        request.setGeneratorConfirmed(true);

        when(collectionRequestPort.findById(requestId))
                .thenReturn(Uni.createFrom().item(request));
        when(collectionRequestPort.update(any(CollectionRequest.class)))
                .thenReturn(Uni.createFrom().nullItem().replaceWithVoid());
        when(eventPort.publishCollectionEvent(any()))
                .thenReturn(Uni.createFrom().nullItem().replaceWithVoid());
        when(closeSavedRoutesUseCase.closeRoutesContaining(requestId))
                .thenReturn(Uni.createFrom().nullItem().replaceWithVoid());
        when(notificationUseCase.notifyGenerator(any(CollectionRequest.class), any()))
                .thenReturn(Uni.createFrom().voidItem());

        Uni<Void> result = useCase.confirmCollectorCompletion(requestId);

        assertNotNull(result);
        result.await().indefinitely();

        verify(collectionRequestPort).findById(requestId);
        verify(collectionRequestPort, times(2)).update(any(CollectionRequest.class));
        verify(eventPort).publishCollectionEvent(any());
        verify(closeSavedRoutesUseCase).closeRoutesContaining(requestId);
    }

    @Test
    void testMarksCompletedWhenBothConfirm() {
        String requestId = "req-001";
        CollectionRequest request = request(requestId);
        request.setGeneratorConfirmed(true);

        when(collectionRequestPort.findById(requestId))
                .thenReturn(Uni.createFrom().item(request));
        when(collectionRequestPort.update(any(CollectionRequest.class)))
                .thenReturn(Uni.createFrom().nullItem().replaceWithVoid());
        when(eventPort.publishCollectionEvent(any()))
                .thenReturn(Uni.createFrom().nullItem().replaceWithVoid());
        when(closeSavedRoutesUseCase.closeRoutesContaining(requestId))
                .thenReturn(Uni.createFrom().nullItem().replaceWithVoid());
        when(notificationUseCase.notifyGenerator(any(CollectionRequest.class), any()))
                .thenReturn(Uni.createFrom().voidItem());

        Uni<Void> result = useCase.confirmCollectorCompletion(requestId);

        assertNotNull(result);
        result.await().indefinitely();

        verify(collectionRequestPort).findById(requestId);
        verify(eventPort).publishCollectionEvent(any());
        verify(closeSavedRoutesUseCase).closeRoutesContaining(requestId);
    }

    private CollectionRequest request(String requestId) {
        CollectionRequest request = new CollectionRequest("gen-001", "addr-001",
                java.util.Arrays.asList("mat-001"), 100.0);
        request.setId(requestId);
        request.setStatus(CollectionRequest.Status.IN_PROGRESS);
        request.setSelectedCollectorId("coll-001");
        return request;
    }
}
