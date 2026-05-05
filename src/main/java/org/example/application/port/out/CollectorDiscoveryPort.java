package org.example.application.port.out;

import io.smallrye.mutiny.Uni;
import org.example.domain.entity.Collector;

import java.util.List;

public interface CollectorDiscoveryPort {
    Uni<List<Collector>> findCollectorsAcceptingMaterials(List<String> materialIds);
    Uni<Collector> findCollectorById(String collectorId);
}

