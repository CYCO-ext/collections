package org.example.application.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteLog;
import org.example.application.port.out.DeliveryRouteLogPort;
import org.example.infrastructure.repository.DeliveryRouteLogRepository;

import java.util.List;

@Singleton
public class DeliveryRouteLogAdapter implements DeliveryRouteLogPort {
    @Inject
    DeliveryRouteLogRepository repository;

    @Override
    public Uni<Void> append(DeliveryRouteLog log) {
        return repository.append(log);
    }

    @Override
    public Uni<List<DeliveryRouteLog>> findBySavedRouteIdAndVehicleIndex(String savedRouteId, int vehicleIndex) {
        return repository.findBySavedRouteIdAndVehicleIndex(savedRouteId, vehicleIndex);
    }
}
