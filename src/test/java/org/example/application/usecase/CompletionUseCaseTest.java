package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.CollectionRequestPort;
import org.example.domain.entity.CollectionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompletionUseCaseTest {

    @InjectMocks
    private CompletionUseCase useCase;

    @Mock
    private CollectionRequestPort collectionRequestPort;

    @Test
    void legacyGeneratorConfirmationFailsClosedWithoutChangingRequestOrPublishingEffects() {
        CollectionRequest request = activeRequestWithLegacyBooleansSet();

        CompletionCodeConflictException error = assertThrows(CompletionCodeConflictException.class,
                () -> useCase.confirmGeneratorCompletion(request.getId()).await().indefinitely());

        assertEquals("COMPLETION_CODE_REQUIRED", error.code());
        assertUnchanged(request);
        verifyNoCompletionMutationOrLookup();
    }

    @Test
    void legacyCollectorConfirmationFailsClosedWithoutChangingRequestOrPublishingEffects() {
        CollectionRequest request = activeRequestWithLegacyBooleansSet();

        CompletionCodeConflictException error = assertThrows(CompletionCodeConflictException.class,
                () -> useCase.confirmCollectorCompletion(request.getId()).await().indefinitely());

        assertEquals("COMPLETION_CODE_REQUIRED", error.code());
        assertUnchanged(request);
        verifyNoCompletionMutationOrLookup();
    }

    private CollectionRequest activeRequestWithLegacyBooleansSet() {
        CollectionRequest request = new CollectionRequest("gen-001", "addr-001", List.of("mat-001"), 100.0);
        request.setId("req-001");
        request.setStatus(CollectionRequest.Status.IN_PROGRESS);
        request.setSelectedCollectorId("coll-001");
        request.setGeneratorConfirmed(true);
        request.setCollectorConfirmed(true);
        return request;
    }

    private void assertUnchanged(CollectionRequest request) {
        assertEquals(CollectionRequest.Status.IN_PROGRESS, request.getStatus());
        assertEquals(Boolean.TRUE, request.getGeneratorConfirmed());
        assertEquals(Boolean.TRUE, request.getCollectorConfirmed());
        assertNull(request.getCompletionEvidence());
    }

    private void verifyNoCompletionMutationOrLookup() {
        verify(collectionRequestPort, never()).findById("req-001");
        verify(collectionRequestPort, never()).update(org.mockito.ArgumentMatchers.any());
        verify(collectionRequestPort, never()).completeWithCode(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.any());
    }
}
