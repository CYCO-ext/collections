package org.example.application.port.out;

import io.smallrye.mutiny.Uni;
import org.example.domain.entity.GeneratorNotificationToken;

import java.util.List;

public interface GeneratorNotificationTokenPort {
    Uni<Void> saveOrUpdate(GeneratorNotificationToken token);

    Uni<List<GeneratorNotificationToken>> findEnabledByGeneratorId(String generatorId);
}
