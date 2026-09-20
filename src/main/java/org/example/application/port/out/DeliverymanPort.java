package org.example.application.port.out;

import io.smallrye.mutiny.Uni;
import org.example.application.deliveryman.DeliverymanModels.DeliverymanProfile;

import java.util.List;

public interface DeliverymanPort {
    Uni<Void> saveOrUpdate(DeliverymanProfile deliveryman);

    Uni<DeliverymanProfile> findById(String deliverymanId);

    Uni<List<DeliverymanProfile>> findByCollectorId(String collectorId);
}
