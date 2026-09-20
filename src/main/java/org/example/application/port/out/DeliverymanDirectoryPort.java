package org.example.application.port.out;

import io.smallrye.mutiny.Uni;

public interface DeliverymanDirectoryPort {
    Uni<DeliverymanRef> findById(String deliverymanId);

    record DeliverymanRef(
            String id,
            String collectorId,
            String name,
            boolean active
    ) {
    }
}
