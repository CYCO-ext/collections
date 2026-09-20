package org.example.application.port.out;

import io.smallrye.mutiny.Uni;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteLog;

import java.util.List;

public interface DeliveryRouteLogPort {
    Uni<Void> append(DeliveryRouteLog log);

    Uni<List<DeliveryRouteLog>> findBySavedRouteIdAndVehicleIndex(String savedRouteId, int vehicleIndex);
}
