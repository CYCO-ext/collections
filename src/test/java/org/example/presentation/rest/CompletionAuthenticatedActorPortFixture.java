package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.example.application.port.out.AuthenticatedActorPort;
import org.example.infrastructure.identity.RegisterAuthenticatedActorAdapter;

@Alternative
@Priority(1)
@ApplicationScoped
public class CompletionAuthenticatedActorPortFixture implements AuthenticatedActorPort {
    @Override
    public Uni<AuthenticatedActor> resolve(String bearerToken) {
        if (bearerToken == null || "Bearer invalid".equals(bearerToken)) {
            return Uni.createFrom().failure(new RegisterAuthenticatedActorAdapter.ActorUnauthenticatedException());
        }
        if ("Bearer unavailable".equals(bearerToken)) {
            return Uni.createFrom().failure(new IllegalStateException("identity service unavailable"));
        }
        return switch (bearerToken) {
            case "Bearer collector" -> Uni.createFrom().item(new AuthenticatedActor(
                    "user-collector", "WASTE_COLLECTOR", null, "collector-1", null, null));
            case "Bearer third" -> Uni.createFrom().item(new AuthenticatedActor(
                    "user-third", "WASTE_COLLECTOR", null, "collector-2", null, null));
            case "Bearer generator" -> Uni.createFrom().item(new AuthenticatedActor(
                    "user-generator", "GENERATOR", "generator-1", null, null, null));
            case "Bearer deliveryman" -> Uni.createFrom().item(new AuthenticatedActor(
                    "user-deliveryman", "DELIVERYMAN", null, null, "deliveryman-1", "collector-1"));
            case "Bearer other-deliveryman" -> Uni.createFrom().item(new AuthenticatedActor(
                    "user-other-deliveryman", "DELIVERYMAN", null, null, "deliveryman-2", "collector-1"));
            default -> Uni.createFrom().failure(new RegisterAuthenticatedActorAdapter.ActorUnauthenticatedException());
        };
    }
}
