package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.RouteModels.RouteOptimizationResult;
import org.example.application.route.RouteModels.SolverMetadata;
import org.example.application.route.RouteModels.SolverStatus;
import org.example.application.route.SavedRouteModels.SavedRouteStatus;
import org.example.application.route.SavedRouteModels.SavedRouteSuggestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListSavedRoutesUseCaseTest {
    private ListSavedRoutesUseCase useCase;

    @Mock
    private SavedRoutePort savedRoutePort;

    @BeforeEach
    void setUp() {
        useCase = new ListSavedRoutesUseCase();
        useCase.savedRoutePort = savedRoutePort;
    }

    @Test
    void listDelegatesToPort() {
        SavedRouteSuggestion route = new SavedRouteSuggestion(
                "saved-1",
                "collector-1",
                SavedRouteStatus.OPEN,
                "fingerprint",
                List.of("request-1"),
                new RouteOptimizationResult(SolverStatus.FEASIBLE, new SolverMetadata("TEST", 1, 1, 0), List.of(), List.of()),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null
        );
        when(savedRoutePort.findAllOrderByCreatedAtDesc()).thenReturn(Uni.createFrom().item(List.of(route)));

        var result = useCase.list().await().indefinitely();

        assertEquals(1, result.size());
        assertEquals("saved-1", result.getFirst().id());
        verify(savedRoutePort).findAllOrderByCreatedAtDesc();
    }
}
