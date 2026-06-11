package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.GeneratorNotificationTokenPort;
import org.example.domain.entity.GeneratorNotificationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterGeneratorNotificationTokenUseCaseTest {

    private RegisterGeneratorNotificationTokenUseCase useCase;
    private GeneratorNotificationTokenPort tokenPort;

    @BeforeEach
    void setUp() {
        tokenPort = mock(GeneratorNotificationTokenPort.class);
        useCase = new RegisterGeneratorNotificationTokenUseCase();
        useCase.tokenPort = tokenPort;
    }

    @Test
    void registersNormalizedToken() {
        when(tokenPort.saveOrUpdate(org.mockito.ArgumentMatchers.any())).thenReturn(Uni.createFrom().voidItem());

        useCase.register(" generator-1 ", " token-1 ", " android ").await().indefinitely();

        ArgumentCaptor<GeneratorNotificationToken> captor = ArgumentCaptor.forClass(GeneratorNotificationToken.class);
        verify(tokenPort).saveOrUpdate(captor.capture());
        GeneratorNotificationToken token = captor.getValue();
        assertEquals("generator-1", token.getGeneratorId());
        assertEquals("token-1", token.getToken());
        assertEquals("ANDROID", token.getPlatform());
    }

    @Test
    void rejectsBlankToken() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> useCase.register("generator-1", " ", "ios"));

        assertEquals("notification token is required", exception.getMessage());
        verify(tokenPort, never()).saveOrUpdate(org.mockito.ArgumentMatchers.any());
    }
}
