package org.example.application.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.CollectorDiscoveryPort;
import org.example.domain.entity.Collector;
import org.example.infrastructure.repository.CollectorRepository;

import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class CollectorDiscoveryAdapter implements CollectorDiscoveryPort {

    @Inject
    CollectorRepository repository;

    @Override
    public Uni<List<Collector>> findCollectorsAcceptingMaterials(List<String> materialIds) {
        return repository.findAll()
                .onItem().transform(collectors -> collectors.stream()
                        .filter(c -> c.acceptsAllMaterials(materialIds))
                        .collect(Collectors.toList())
                );
    }

    @Override
    public Uni<Collector> findCollectorById(String collectorId) {
        return repository.findById(collectorId);
    }
}

