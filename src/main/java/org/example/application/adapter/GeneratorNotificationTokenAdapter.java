package org.example.application.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.GeneratorNotificationTokenPort;
import org.example.domain.entity.GeneratorNotificationToken;
import org.example.infrastructure.repository.GeneratorNotificationTokenRepository;

import java.util.List;

@Singleton
public class GeneratorNotificationTokenAdapter implements GeneratorNotificationTokenPort {

    @Inject
    GeneratorNotificationTokenRepository repository;

    @Override
    public Uni<Void> saveOrUpdate(GeneratorNotificationToken token) {
        return repository.saveOrUpdate(token);
    }

    @Override
    public Uni<List<GeneratorNotificationToken>> findEnabledByGeneratorId(String generatorId) {
        return repository.findEnabledByGeneratorId(generatorId);
    }
}
