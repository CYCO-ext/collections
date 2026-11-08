package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.AuthenticatedActorPort;
import org.example.application.port.out.CompletionCodeCryptoPort;
import org.example.application.port.out.CollectionRequestPort;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.CompletionCodeState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompletionCodeUseCaseTest {
    private static final String REQUEST_ID = "request-1";

    private CompletionCodeUseCase useCase;

    @Mock
    private CollectionRequestPort collectionRequestPort;
    @Mock
    private AuthenticatedActorPort authenticatedActorPort;
    @Mock
    private CompletionCodeCryptoPort cryptoPort;

    @BeforeEach
    void setUp() {
        useCase = new CompletionCodeUseCase();
        useCase.collectionRequestPort = collectionRequestPort;
        useCase.authenticatedActorPort = authenticatedActorPort;
        useCase.cryptoPort = cryptoPort;
        when(authenticatedActorPort.resolve("Bearer valid")).thenReturn(Uni.createFrom().item(generatorActor("generator-1")));
    }

    @Test
    void firstQueryIssuesSixDigitCodeAndSecondQueryReturnsSameCode() {
        CollectionRequest request = activeRequest();
        when(collectionRequestPort.findById(REQUEST_ID)).thenReturn(Uni.createFrom().item(request));
        when(cryptoPort.generateCode()).thenReturn("001234");
        when(cryptoPort.encrypt(REQUEST_ID, 1, "001234"))
                .thenReturn(new CompletionCodeCryptoPort.EncryptedCode(1, "cipher", "nonce", "key-1"));
        when(cryptoPort.createVerifier(REQUEST_ID, 1, "001234")).thenReturn("verifier");
        when(collectionRequestPort.issueCompletionCodeIfAbsent(eq(REQUEST_ID), any(CompletionCodeState.class)))
                .thenReturn(Uni.createFrom().item(true));

        CompletionCodeUseCase.IssuedCompletionCode issued = useCase.issueOrRead(REQUEST_ID, "Bearer valid")
                .await().indefinitely();
        assertEquals(REQUEST_ID, issued.requestId());
        assertEquals("001234", issued.code());

        request.setCompletionCode(codeState("cipher", "verifier"));
        when(cryptoPort.decrypt(eq(REQUEST_ID), any(CompletionCodeCryptoPort.EncryptedCode.class)))
                .thenReturn("001234");
        CompletionCodeUseCase.IssuedCompletionCode reread = useCase.issueOrRead(REQUEST_ID, "Bearer valid")
                .await().indefinitely();
        assertEquals("001234", reread.code());
        verify(cryptoPort).generateCode();
        verify(collectionRequestPort).issueCompletionCodeIfAbsent(eq(REQUEST_ID), any(CompletionCodeState.class));
    }

    @Test
    void concurrentLoserReadsAndReturnsWinningCode() {
        CollectionRequest initial = activeRequest();
        CollectionRequest winner = activeRequest();
        winner.setCompletionCode(codeState("winning-cipher", "winning-verifier"));
        when(collectionRequestPort.findById(REQUEST_ID)).thenReturn(
                Uni.createFrom().item(initial), Uni.createFrom().item(winner));
        when(cryptoPort.generateCode()).thenReturn("005678");
        when(cryptoPort.encrypt(REQUEST_ID, 1, "005678"))
                .thenReturn(new CompletionCodeCryptoPort.EncryptedCode(1, "losing-cipher", "nonce", "key-1"));
        when(cryptoPort.createVerifier(REQUEST_ID, 1, "005678")).thenReturn("losing-verifier");
        when(collectionRequestPort.issueCompletionCodeIfAbsent(eq(REQUEST_ID), any(CompletionCodeState.class)))
                .thenReturn(Uni.createFrom().item(false));
        when(cryptoPort.decrypt(eq(REQUEST_ID), any(CompletionCodeCryptoPort.EncryptedCode.class)))
                .thenReturn("004321");

        CompletionCodeUseCase.IssuedCompletionCode result = useCase.issueOrRead(REQUEST_ID, "Bearer valid")
                .await().indefinitely();

        assertEquals("004321", result.code());
        verify(collectionRequestPort, times(2)).findById(REQUEST_ID);
        verify(collectionRequestPort).issueCompletionCodeIfAbsent(eq(REQUEST_ID), any(CompletionCodeState.class));
    }

    @Test
    void fourInactiveStatesCannotIssueCode() {
        List<CollectionRequest.Status> inactive = List.of(
                CollectionRequest.Status.PENDING,
                CollectionRequest.Status.COMPLETED,
                CollectionRequest.Status.CANCELLED,
                CollectionRequest.Status.REJECTED);
        for (CollectionRequest.Status status : inactive) {
            CollectionRequest request = activeRequest();
            request.setStatus(status);
            when(collectionRequestPort.findById(REQUEST_ID)).thenReturn(Uni.createFrom().item(request));

            CompletionCodeConflictException exception = assertThrows(CompletionCodeConflictException.class,
                    () -> useCase.issueOrRead(REQUEST_ID, "Bearer valid").await().indefinitely());

            assertEquals("COLLECTION_NOT_ACTIVE", exception.code());
        }
        verify(collectionRequestPort, never()).issueCompletionCodeIfAbsent(anyString(), any(CompletionCodeState.class));
        verify(cryptoPort, never()).generateCode();
    }

    @Test
    void onlyOwningGeneratorCanReadOrIssueCode() {
        CollectionRequest request = activeRequest();
        when(collectionRequestPort.findById(REQUEST_ID)).thenReturn(Uni.createFrom().item(request));
        when(authenticatedActorPort.resolve("Bearer valid"))
                .thenReturn(Uni.createFrom().item(generatorActor("another-generator")));

        assertThrows(CompletionCodeForbiddenException.class,
                () -> useCase.issueOrRead(REQUEST_ID, "Bearer valid").await().indefinitely());

        when(authenticatedActorPort.resolve("Bearer valid"))
                .thenReturn(Uni.createFrom().item(new AuthenticatedActorPort.AuthenticatedActor(
                        "user-2", "WASTE_COLLECTOR", null, "collector-1", null, null)));
        assertThrows(CompletionCodeForbiddenException.class,
                () -> useCase.issueOrRead(REQUEST_ID, "Bearer valid").await().indefinitely());
        verify(collectionRequestPort, never()).issueCompletionCodeIfAbsent(anyString(), any(CompletionCodeState.class));
    }

    @Test
    void consumedCodeCannotBeReissuedOrRead() {
        CollectionRequest request = activeRequest();
        request.setCompletionCode(new CompletionCodeState(1, "cipher", "nonce", "key-1", "verifier",
                LocalDateTime.now().minusMinutes(1), 0, null, LocalDateTime.now()));
        when(collectionRequestPort.findById(REQUEST_ID)).thenReturn(Uni.createFrom().item(request));

        CompletionCodeConflictException exception = assertThrows(CompletionCodeConflictException.class,
                () -> useCase.issueOrRead(REQUEST_ID, "Bearer valid").await().indefinitely());

        assertEquals("CODE_CONSUMED", exception.code());
        verify(cryptoPort, never()).decrypt(anyString(), any(CompletionCodeCryptoPort.EncryptedCode.class));
        verify(collectionRequestPort, never()).issueCompletionCodeIfAbsent(anyString(), any(CompletionCodeState.class));
    }

    private CollectionRequest activeRequest() {
        CollectionRequest request = new CollectionRequest("generator-1", "address-1", List.of("paper"), 10.0);
        request.setId(REQUEST_ID);
        request.setSelectedCollectorId("collector-1");
        request.setStatus(CollectionRequest.Status.IN_PROGRESS);
        return request;
    }

    private CompletionCodeState codeState(String ciphertext, String verifier) {
        return new CompletionCodeState(1, ciphertext, "nonce", "key-1", verifier,
                LocalDateTime.now(), 0, null, null);
    }

    private AuthenticatedActorPort.AuthenticatedActor generatorActor(String generatorId) {
        return new AuthenticatedActorPort.AuthenticatedActor("user-1", "GENERATOR", generatorId,
                null, null, null);
    }
}
