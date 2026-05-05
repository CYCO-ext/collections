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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompletionUseCaseTest {

    @InjectMocks
    private CompletionUseCase useCase;

    @Mock
    private CollectionRequestPort collectionRequestPort;

    @Mock
    private EventPort eventPort;

    @Test
    void testConfirmGeneratorCompletion() {
        // Given
        String requestId = "req-001";
        CollectionRequest request = new CollectionRequest("gen-001", "addr-001",
                java.util.Arrays.asList("mat-001"), 100.0);
        request.setId(requestId);
        request.setStatus(CollectionRequest.Status.IN_PROGRESS);
        request.setSelectedCollectorId("coll-001");

        when(collectionRequestPort.findById(requestId))
                .thenReturn(Uni.createFrom().item(request));
        when(collectionRequestPort.update(any(CollectionRequest.class)))
                .thenReturn(Uni.createFrom().nullItem().replaceWithVoid());
        // Note: eventPort.publishCollectionEvent() is not called when only generator confirms

        // When
        Uni<Void> result = useCase.confirmGeneratorCompletion(requestId);

        // Then
        assertNotNull(result);
        result.subscribe().with(it -> {
            // Completion successful
        });

        verify(collectionRequestPort).findById(requestId);
        verify(collectionRequestPort).update(any(CollectionRequest.class));
        verify(eventPort, never()).publishCollectionEvent(any()); // Ensure it's not called
    }

    @Test
    void testConfirmCollectorCompletion() {
        // Given
        String requestId = "req-001";
        CollectionRequest request = new CollectionRequest("gen-001", "addr-001",
                java.util.Arrays.asList("mat-001"), 100.0);
        request.setId(requestId);
        request.setStatus(CollectionRequest.Status.IN_PROGRESS);
        request.setSelectedCollectorId("coll-001");
        request.setGeneratorConfirmed(true);

        when(collectionRequestPort.findById(requestId))
                .thenReturn(Uni.createFrom().item(request));
        when(collectionRequestPort.update(any(CollectionRequest.class)))
                .thenReturn(Uni.createFrom().nullItem().replaceWithVoid());
        when(eventPort.publishCollectionEvent(any()))
                .thenReturn(Uni.createFrom().nullItem().replaceWithVoid());

        // When
        Uni<Void> result = useCase.confirmCollectorCompletion(requestId);

        // Then
        assertNotNull(result);
        result.subscribe().with(it -> {
            // Completion successful
        });

        verify(collectionRequestPort).findById(requestId);
        verify(collectionRequestPort, times(2)).update(any(CollectionRequest.class)); // Called twice: set collectorConfirmed + mark completed
        verify(eventPort).publishCollectionEvent(any());
    }

    @Test
    void testMarksCompletedWhenBothConfirm() {
        // Given
        String requestId = "req-001";
        CollectionRequest request = new CollectionRequest("gen-001", "addr-001",
                java.util.Arrays.asList("mat-001"), 100.0);
        request.setId(requestId);
        request.setStatus(CollectionRequest.Status.IN_PROGRESS);
        request.setSelectedCollectorId("coll-001");
        request.setGeneratorConfirmed(true);

        when(collectionRequestPort.findById(requestId))
                .thenReturn(Uni.createFrom().item(request));
        when(collectionRequestPort.update(any(CollectionRequest.class)))
                .thenReturn(Uni.createFrom().nullItem().replaceWithVoid());
        when(eventPort.publishCollectionEvent(any()))
                .thenReturn(Uni.createFrom().nullItem().replaceWithVoid());

        // When
        Uni<Void> result = useCase.confirmCollectorCompletion(requestId);

        // Then
        assertNotNull(result);
        result.subscribe().with(it -> {
            // Completion successful
        });

        verify(collectionRequestPort).findById(requestId);
        verify(eventPort).publishCollectionEvent(any());
    }
}
