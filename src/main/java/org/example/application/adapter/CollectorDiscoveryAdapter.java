package org.example.application.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.CollectorDiscoveryPort;
import org.example.domain.entity.Collector;
import org.example.domain.entity.Material;
import org.example.infrastructure.repository.CollectorRepository;
import org.example.infrastructure.repository.MaterialRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class CollectorDiscoveryAdapter implements CollectorDiscoveryPort {

    @Inject
    CollectorRepository repository;

    @Inject
    MaterialRepository materialRepository;

    @Override
    public Uni<List<Collector>> findCollectorsAcceptingMaterials(List<String> materialIds) {
        return materialRepository.findAll()
                .flatMap(materials -> repository.findAll()
                        .map(collectors -> {
                            List<String> resolvedMaterialIds = resolveMaterialIds(materialIds, materials);
                            return collectors.stream()
                                    .filter(collector -> collector.acceptsAllMaterials(resolvedMaterialIds))
                                    .collect(Collectors.toList());
                        }));
    }

    private List<String> resolveMaterialIds(List<String> requestedMaterialIds, List<Material> materials) {
        if (requestedMaterialIds == null || requestedMaterialIds.isEmpty()) {
            return requestedMaterialIds;
        }

        Map<String, String> idByIdOrName = new HashMap<>();
        for (Material material : materials) {
            if (material.getId() != null) {
                idByIdOrName.put(material.getId(), material.getId());
            }
            if (material.getName() != null && material.getId() != null) {
                idByIdOrName.put(material.getName(), material.getId());
            }
        }
        return requestedMaterialIds.stream()
                .map(idOrName -> idByIdOrName.getOrDefault(idOrName, idOrName))
                .toList();
    }

    @Override
    public Uni<Collector> findCollectorById(String collectorId) {
        return repository.findById(collectorId);
    }
}

