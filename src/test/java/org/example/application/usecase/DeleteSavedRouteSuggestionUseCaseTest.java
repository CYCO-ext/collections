package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.SavedRoutePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteSavedRouteSuggestionUseCaseTest {
    private DeleteSavedRouteSuggestionUseCase useCase;

    @Mock
    private SavedRoutePort savedRoutePort;

    @BeforeEach
    void setUp() {
        useCase = new DeleteSavedRouteSuggestionUseCase();
        useCase.savedRoutePort = savedRoutePort;
    }

    @Test
    void deleteDelegatesWithTrimmedId() {
        when(savedRoutePort.deleteById("saved-1")).thenReturn(Uni.createFrom().item(true));

        useCase.delete(" saved-1 ").await().indefinitely();

        verify(savedRoutePort).deleteById("saved-1");
    }

    @Test
    void deleteRejectsBlankIdBeforePortCall() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> useCase.delete("  "));

        assertEquals("saved route id is required", exception.getMessage());
        verify(savedRoutePort, never()).deleteById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteFailsWhenSavedRouteDoesNotExist() {
        when(savedRoutePort.deleteById("missing")).thenReturn(Uni.createFrom().item(false));

        SavedRouteSuggestionNotFoundException exception = assertThrows(SavedRouteSuggestionNotFoundException.class,
                () -> useCase.delete("missing").await().indefinitely());

        assertEquals("Saved route suggestion not found: missing", exception.getMessage());
        verify(savedRoutePort).deleteById("missing");
    }

    @Test
    void deletePropagatesPortFailure() {
        when(savedRoutePort.deleteById("saved-1")).thenReturn(Uni.createFrom().failure(new RuntimeException("mongo unavailable")));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> useCase.delete("saved-1").await().indefinitely());

        assertEquals("mongo unavailable", exception.getMessage());
    }
}
