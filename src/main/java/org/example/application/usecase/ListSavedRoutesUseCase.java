package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.SavedRouteModels.SavedRouteResult;

import java.util.List;

@Singleton
public class ListSavedRoutesUseCase {

    @Inject
    SavedRoutePort savedRoutePort;

    public Uni<List<SavedRouteResult>> list() {
        return savedRoutePort.findAllOrderByCreatedAtDesc()
                .onItem().transform(routes -> routes.stream()
                        .map(SavedRouteResult::from)
                        .toList());
    }
}
