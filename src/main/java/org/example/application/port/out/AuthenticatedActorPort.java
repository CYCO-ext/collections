package org.example.application.port.out;

import io.smallrye.mutiny.Uni;

public interface AuthenticatedActorPort {
    Uni<AuthenticatedActor> resolve(String bearerToken);

    record AuthenticatedActor(
            String userId,
            String role,
            String generatorId,
            String collectorId,
            String deliverymanId,
            String ownerCollectorId
    ) {
    }
}
