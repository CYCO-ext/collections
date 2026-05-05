package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.CollectorDiscoveryPort;
import org.example.application.port.out.AddressPort;
import org.example.domain.entity.Address;
import org.example.domain.entity.Collector;
import org.example.domain.entity.CollectionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class CollectionRequestUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(CollectionRequestUseCase.class);
    private static final double NEARBY_DISTANCE = 50.0; // km

    @Inject
    CollectionRequestPort collectionRequestPort;

    @Inject
    CollectorDiscoveryPort collectorDiscoveryPort;

    @Inject
    AddressPort addressPort;

    public Uni<CollectionRequest> createRequest(String generatorId, String addressId, List<String> materialIds, Double weight) {
        LOG.info("Creating collection request for generator: {}, address: {}", generatorId, addressId);

        CollectionRequest request = new CollectionRequest(generatorId, addressId, materialIds, weight);
        
        return collectionRequestPort.save(request)
                .replaceWith(request)
                .invoke(() -> LOG.info("Collection request created: {}", request.getId()));
    }

    public Uni<List<Collector>> findNearbyCollectors(String requestId) {
        LOG.info("Finding nearby collectors for request: {}", requestId);

        return collectionRequestPort.findById(requestId)
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Request not found: " + requestId))
                .flatMap(request -> addressPort.findById(request.getAddressId())
                        .onItem().ifNull().failWith(() -> new IllegalArgumentException("Address not found: " + request.getAddressId()))
                        .flatMap(requestAddress -> collectorDiscoveryPort.findCollectorsAcceptingMaterials(request.getMaterialIds())
                                .onItem().transform(collectors -> filterByDistance(collectors, requestAddress))
                        )
                );
    }

    private List<Collector> filterByDistance(List<Collector> collectors, Address requestAddress) {
        return collectors.stream()
                .filter(collector -> {
                    double distance = collector.getAddress().distance(requestAddress);
                    return distance <= NEARBY_DISTANCE;
                })
                .sorted((c1, c2) -> {
                    double dist1 = c1.getAddress().distance(requestAddress);
                    double dist2 = c2.getAddress().distance(requestAddress);
                    return Double.compare(dist1, dist2);
                })
                .collect(Collectors.toList());
    }
}

