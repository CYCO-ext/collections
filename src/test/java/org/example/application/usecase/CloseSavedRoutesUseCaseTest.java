package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.RouteModels.RouteOptimizationResult;
import org.example.application.route.RouteModels.SolverMetadata;
import org.example.application.route.RouteModels.SolverStatus;
import org.example.application.route.SavedRouteModels.SavedRouteStatus;
import org.example.application.route.SavedRouteModels.SavedRouteSuggestion;
import org.example.domain.entity.CollectionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloseSavedRoutesUseCaseTest {
    private CloseSavedRoutesUseCase useCase;

    @Mock
    private SavedRoutePort savedRoutePort;

    @Mock
    private CollectionRequestPort collectionRequestPort;

    @BeforeEach
    void setUp() {
        useCase = new CloseSavedRoutesUseCase();
        useCase.savedRoutePort = savedRoutePort;
        useCase.collectionRequestPort = collectionRequestPort;
    }

    @Test
    void closesRouteWhenAllAssignedRequestsAreCompleted() {
        SavedRouteSuggestion route = route(List.of("request-1", "request-2"));
        when(savedRoutePort.findOpenContainingCollectionRequest("request-1")).thenReturn(Uni.createFrom().item(List.of(route)));
        when(collectionRequestPort.findByIds(route.assignedCollectionRequestIds())).thenReturn(Uni.createFrom().item(List.of(
                request("request-1", CollectionRequest.Status.COMPLETED),
                request("request-2", CollectionRequest.Status.COMPLETED)
        )));
        when(savedRoutePort.close(eq("saved-1"), any(LocalDateTime.class))).thenReturn(Uni.createFrom().voidItem());

        useCase.closeRoutesContaining("request-1").await().indefinitely();

        verify(savedRoutePort).close(eq("saved-1"), any(LocalDateTime.class));
    }

    @Test
    void keepsRouteOpenWhenAnyAssignedRequestIsNotCompleted() {
        SavedRouteSuggestion route = route(List.of("request-1", "request-2"));
        when(savedRoutePort.findOpenContainingCollectionRequest("request-1")).thenReturn(Uni.createFrom().item(List.of(route)));
        when(collectionRequestPort.findByIds(route.assignedCollectionRequestIds())).thenReturn(Uni.createFrom().item(List.of(
                request("request-1", CollectionRequest.Status.COMPLETED),
                request("request-2", CollectionRequest.Status.IN_PROGRESS)
        )));

        useCase.closeRoutesContaining("request-1").await().indefinitely();

        verify(savedRoutePort, never()).close(any(), any());
    }

    private SavedRouteSuggestion route(List<String> assignedIds) {
        return new SavedRouteSuggestion(
                "saved-1",
                "collector-1",
                SavedRouteStatus.OPEN,
                "fingerprint",
                assignedIds,
                new RouteOptimizationResult(SolverStatus.FEASIBLE, new SolverMetadata("TEST", 1, 1, 0), List.of(), List.of()),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null
        );
    }

    private CollectionRequest request(String id, CollectionRequest.Status status) {
        CollectionRequest request = new CollectionRequest("generator-1", "address-1", List.of("paper"), 10.0);
        request.setId(id);
        request.setStatus(status);
        return request;
    }
}
