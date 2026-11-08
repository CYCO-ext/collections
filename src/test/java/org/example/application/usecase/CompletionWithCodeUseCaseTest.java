package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.AuthenticatedActorPort;
import org.example.application.port.out.CompletionCodeCryptoPort;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.EventPort;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.CompletionCodeState;
import org.example.domain.entity.CompletionEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompletionWithCodeUseCaseTest {
    private static final String REQUEST_ID = "request-1";
    private static final String BEARER = "Bearer valid";
    private static final String IDEMPOTENCY_KEY = "6d2b9c98-7339-4ed5-a9e2-3f5433f89ab1";

    private CompletionUseCase useCase;
    private CollectionRequest request;
    private AuthenticatedActorPort.AuthenticatedActor collector;

    @Mock private CollectionRequestPort collectionRequestPort;
    @Mock private AuthenticatedActorPort authenticatedActorPort;
    @Mock private CompletionCodeCryptoPort cryptoPort;
    @Mock private EventPort eventPort;
    @Mock private CloseSavedRoutesUseCase closeSavedRoutesUseCase;
    @Mock private CollectionStatusNotificationUseCase notificationUseCase;
    @Mock private CompletionEffectsUseCase completionEffectsUseCase;

    @BeforeEach
    void setUp() {
        useCase = new CompletionUseCase();
        useCase.collectionRequestPort = collectionRequestPort;
        useCase.authenticatedActorPort = authenticatedActorPort;
        useCase.cryptoPort = cryptoPort;
        useCase.eventPort = eventPort;
        useCase.closeSavedRoutesUseCase = closeSavedRoutesUseCase;
        useCase.notificationUseCase = notificationUseCase;
        useCase.completionEffectsUseCase = completionEffectsUseCase;
        request = new CollectionRequest("generator-1", "address-1", List.of("paper"), 10.0);
        request.setId(REQUEST_ID);
        request.setSelectedCollectorId("collector-1");
        request.setStatus(CollectionRequest.Status.ON_THE_WAY);
        request.setCompletionCode(codeState(0, null, null));
        collector = new AuthenticatedActorPort.AuthenticatedActor(
                "user-1", "WASTE_COLLECTOR", null, "collector-1", null, null);
        lenient().when(authenticatedActorPort.resolve(BEARER)).thenReturn(Uni.createFrom().item(collector));
        lenient().when(collectionRequestPort.findById(REQUEST_ID)).thenReturn(Uni.createFrom().item(request));
        lenient().when(completionEffectsUseCase.processCompletion(any(), any())).thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void correctCodeAtomicallyCompletesWithEvidenceWithoutPublishingBusinessEffects() {
        when(cryptoPort.matchesVerifier(REQUEST_ID, 1, "001234", "expected-verifier")).thenReturn(true);
        when(cryptoPort.createVerifier(REQUEST_ID, 1, "001234")).thenReturn("authenticated-payload-digest");
        when(collectionRequestPort.completeWithCode(eq(REQUEST_ID), eq(1), any(CompletionEvidence.class)))
                .thenReturn(Uni.createFrom().item(true));

        CompletionUseCase.CompletionResult result = useCase.confirmWithCode(
                REQUEST_ID, BEARER, "001234", IDEMPOTENCY_KEY).await().indefinitely();

        assertEquals("COMPLETED", result.status());
        assertEquals(REQUEST_ID, result.requestId());
        assertNotNull(result.completionId());
        ArgumentCaptor<CompletionEvidence> evidence = ArgumentCaptor.forClass(CompletionEvidence.class);
        verify(collectionRequestPort).completeWithCode(eq(REQUEST_ID), eq(1), evidence.capture());
        assertEquals(IDEMPOTENCY_KEY, evidence.getValue().idempotencyKey());
        assertEquals("user-1", evidence.getValue().actorUserId());
        assertEquals("WASTE_COLLECTOR", evidence.getValue().actorRole());
        assertEquals("collector-1", evidence.getValue().actorProfileId());
        assertEquals("authenticated-payload-digest", evidence.getValue().payloadDigest());
        verify(collectionRequestPort, never()).incrementCompletionCodeAttempts(any(), any(Integer.class), any(), any(), any(Integer.class));
        verify(eventPort, never()).publishCollectionEvent(any());
        verify(closeSavedRoutesUseCase, never()).closeRoutesContaining(any());
        verify(notificationUseCase, never()).notifyGenerator(any(), any());
    }

    @Test
    void missingHmacKeyPreventsConfirmationWithoutConsumingAttemptOrCompleting() {
        useCase.cryptoPort = new org.example.infrastructure.confirmation.AesGcmCompletionCodeCryptoAdapter();

        assertThrows(IllegalStateException.class,
                () -> useCase.confirmCollectorWithCode(REQUEST_ID, BEARER, "001234", IDEMPOTENCY_KEY)
                        .await().indefinitely());

        verify(collectionRequestPort, never())
                .incrementCompletionCodeAttempts(any(), any(Integer.class), any(), any(), any(Integer.class));
        verify(collectionRequestPort, never()).completeWithCode(any(), anyInt(), any(CompletionEvidence.class));
        verify(completionEffectsUseCase, never()).processCompletion(any(), any());
    }

    @Test
    void malformedOrMissingCodeIsBadRequestAndDoesNotConsumeAnAttempt() {
        CompletionCodeValidationException missing = assertThrows(CompletionCodeValidationException.class,
                () -> useCase.confirmWithCode(REQUEST_ID, BEARER, null, IDEMPOTENCY_KEY).await().indefinitely());
        CompletionCodeValidationException malformed = assertThrows(CompletionCodeValidationException.class,
                () -> useCase.confirmWithCode(REQUEST_ID, BEARER, "12345", IDEMPOTENCY_KEY).await().indefinitely());

        assertEquals(400, missing.status());
        assertEquals(400, malformed.status());
        verify(authenticatedActorPort, never()).resolve(any());
        verify(collectionRequestPort, never()).incrementCompletionCodeAttempts(any(), any(Integer.class), any(), any(), any(Integer.class));
        verify(collectionRequestPort, never()).completeWithCode(any(), any(Integer.class), any());
    }

    @Test
    void invalidAuthenticationStopsBeforeCollectionLookupOrCodeValidation() {
        when(authenticatedActorPort.resolve(BEARER)).thenReturn(Uni.createFrom().failure(
                new org.example.infrastructure.identity.RegisterAuthenticatedActorAdapter.ActorUnauthenticatedException()));

        assertThrows(CompletionCodeUnauthenticatedException.class,
                () -> useCase.confirmWithCode(REQUEST_ID, BEARER, "001234", IDEMPOTENCY_KEY).await().indefinitely());

        verify(collectionRequestPort, never()).findById(any());
        verify(cryptoPort, never()).matchesVerifier(any(), any(Integer.class), any(), any());
        verify(collectionRequestPort, never()).incrementCompletionCodeAttempts(any(), any(Integer.class), any(), any(), any(Integer.class));
    }

    @Test
    void invalidValidFormatCodeIncrementsAttemptAndReturns422() {
        when(cryptoPort.matchesVerifier(REQUEST_ID, 1, "111111", "expected-verifier")).thenReturn(false);
        when(collectionRequestPort.incrementCompletionCodeAttempts(eq(REQUEST_ID), eq(1), any(), any(), eq(5)))
                .thenReturn(Uni.createFrom().item(true));

        CompletionCodeValidationException exception = assertThrows(CompletionCodeValidationException.class,
                () -> useCase.confirmWithCode(REQUEST_ID, BEARER, "111111", IDEMPOTENCY_KEY).await().indefinitely());

        assertEquals(422, exception.status());
        assertEquals("CODE_INVALID", exception.code());
        verify(collectionRequestPort, never()).completeWithCode(any(), any(Integer.class), any());
        verify(eventPort, never()).publishCollectionEvent(any());
    }

    @Test
    void fifthInvalidCodeBlocksForFifteenMinutesWith429() {
        request.setCompletionCode(codeState(4, null, null));
        when(cryptoPort.matchesVerifier(REQUEST_ID, 1, "111111", "expected-verifier")).thenReturn(false);
        when(collectionRequestPort.incrementCompletionCodeAttempts(eq(REQUEST_ID), eq(1), any(), any(), eq(5)))
                .thenAnswer(invocation -> {
                    request.setCompletionCode(codeState(5, invocation.getArgument(3), null));
                    return Uni.createFrom().item(true);
                });

        CompletionCodeValidationException exception = assertThrows(CompletionCodeValidationException.class,
                () -> useCase.confirmWithCode(REQUEST_ID, BEARER, "111111", IDEMPOTENCY_KEY).await().indefinitely());

        assertEquals(429, exception.status());
        assertEquals("TOO_MANY_ATTEMPTS", exception.code());
        assertNotNull(exception.retryAfterSeconds());
    }

    @Test
    void blockedCodeIsRejectedBeforeVerifierComparison() {
        request.setCompletionCode(codeState(5, LocalDateTime.now().plusMinutes(10), null));

        CompletionCodeValidationException exception = assertThrows(CompletionCodeValidationException.class,
                () -> useCase.confirmWithCode(REQUEST_ID, BEARER, "001234", IDEMPOTENCY_KEY).await().indefinitely());

        assertEquals(429, exception.status());
        verify(cryptoPort, never()).matchesVerifier(any(), any(Integer.class), any(), any());
        verify(collectionRequestPort, never()).incrementCompletionCodeAttempts(any(), any(Integer.class), any(), any(), any(Integer.class));
    }

    @Test
    void codeMustBeIssuedBeforeConfirmation() {
        request.setCompletionCode(null);

        CompletionCodeConflictException exception = assertThrows(CompletionCodeConflictException.class,
                () -> useCase.confirmWithCode(REQUEST_ID, BEARER, "001234", IDEMPOTENCY_KEY).await().indefinitely());

        assertEquals("CODE_NOT_ISSUED", exception.code());
        verify(cryptoPort, never()).matchesVerifier(any(), any(Integer.class), any(), any());
        verify(collectionRequestPort, never()).incrementCompletionCodeAttempts(any(), any(Integer.class), any(), any(), any(Integer.class));
    }

    @Test
    void exactRetryReturnsPriorResultButDifferentPayloadWithSameKeyConflicts() {
        request.setStatus(CollectionRequest.Status.COMPLETED);
        request.setCompletionCode(new CompletionCodeState(1, "cipher", "nonce", "key-1", "expected-verifier",
                LocalDateTime.now().minusMinutes(1), 0, null, LocalDateTime.now()));
        request.setCompletionEvidence(new CompletionEvidence("completion-1", "user-1", "WASTE_COLLECTOR",
                "collector-1", LocalDateTime.now(), 1, IDEMPOTENCY_KEY, "authenticated-payload-digest"));
        when(cryptoPort.createVerifier(REQUEST_ID, 1, "001234")).thenReturn("authenticated-payload-digest", "different-digest");

        CompletionUseCase.CompletionResult retry = useCase.confirmWithCode(
                REQUEST_ID, BEARER, "001234", IDEMPOTENCY_KEY).await().indefinitely();
        assertEquals("completion-1", retry.completionId());

        CompletionCodeConflictException conflict = assertThrows(CompletionCodeConflictException.class,
                () -> useCase.confirmWithCode(REQUEST_ID, BEARER, "654321", IDEMPOTENCY_KEY).await().indefinitely());
        assertEquals("IDEMPOTENCY_KEY_CONFLICT", conflict.code());
        verify(collectionRequestPort, never()).completeWithCode(any(), any(Integer.class), any());
    }

    @Test
    void wrongActorIsRejectedBeforeVerifierOrAttemptCounter() {
        when(authenticatedActorPort.resolve(BEARER)).thenReturn(Uni.createFrom().item(
                new AuthenticatedActorPort.AuthenticatedActor("user-x", "GENERATOR", "generator-1", null, null, null)));

        assertThrows(CompletionCodeForbiddenException.class,
                () -> useCase.confirmWithCode(REQUEST_ID, BEARER, "001234", IDEMPOTENCY_KEY).await().indefinitely());

        verify(cryptoPort, never()).matchesVerifier(any(), any(Integer.class), any(), any());
        verify(collectionRequestPort, never()).incrementCompletionCodeAttempts(any(), any(Integer.class), any(), any(), any(Integer.class));
    }

    @Test
    void failedAtomicPersistenceDoesNotPublishEffectsOrReturnSuccess() {
        when(cryptoPort.matchesVerifier(REQUEST_ID, 1, "001234", "expected-verifier")).thenReturn(true);
        when(cryptoPort.createVerifier(REQUEST_ID, 1, "001234")).thenReturn("authenticated-payload-digest");
        when(collectionRequestPort.completeWithCode(eq(REQUEST_ID), eq(1), any(CompletionEvidence.class)))
                .thenReturn(Uni.createFrom().item(false));

        CompletionCodeConflictException exception = assertThrows(CompletionCodeConflictException.class,
                () -> useCase.confirmWithCode(REQUEST_ID, BEARER, "001234", IDEMPOTENCY_KEY).await().indefinitely());

        assertEquals("COLLECTION_CHANGED", exception.code());
        verify(collectionRequestPort).completeWithCode(eq(REQUEST_ID), eq(1), any(CompletionEvidence.class));
        verify(eventPort, never()).publishCollectionEvent(any());
        verify(closeSavedRoutesUseCase, never()).closeRoutesContaining(any());
        verify(notificationUseCase, never()).notifyGenerator(any(), any());
    }

    private CompletionCodeState codeState(int attempts, LocalDateTime blockedUntil, LocalDateTime consumedAt) {
        return new CompletionCodeState(1, "cipher", "nonce", "key-1", "expected-verifier",
                LocalDateTime.now().minusMinutes(1), attempts, blockedUntil, consumedAt);
    }
}
