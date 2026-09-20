package org.example.application.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.deliveryman.DeliverymanModels.DeliverymanProfile;
import org.example.application.port.out.DeliverymanPort;
import org.example.infrastructure.repository.DeliverymanRepository;

import java.util.List;

@Singleton
public class DeliverymanAdapter implements DeliverymanPort {
    @Inject
    DeliverymanRepository repository;

    @Override
    public Uni<Void> saveOrUpdate(DeliverymanProfile deliveryman) {
        return repository.saveOrUpdate(deliveryman);
    }

    @Override
    public Uni<DeliverymanProfile> findById(String deliverymanId) {
        return repository.findById(deliverymanId);
    }

    @Override
    public Uni<List<DeliverymanProfile>> findByCollectorId(String collectorId) {
        return repository.findByCollectorId(collectorId);
    }
}
