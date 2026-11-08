package org.example.application.port.out;

import io.smallrye.mutiny.Uni;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteAssignment;
import org.example.application.deliveryman.DeliverymanModels.RouteAssignmentStatus;

import java.util.List;
import java.time.LocalDateTime;

public interface DeliveryRouteAssignmentPort {
    Uni<Void> save(DeliveryRouteAssignment assignment);

    Uni<Void> update(DeliveryRouteAssignment assignment);

    Uni<DeliveryRouteAssignment> findById(String assignmentId);

    Uni<DeliveryRouteAssignment> findOpenBySavedRouteIdAndVehicleIndex(String savedRouteId, int vehicleIndex);

    Uni<List<DeliveryRouteAssignment>> findByDeliverymanIdAndStatuses(String deliverymanId, List<RouteAssignmentStatus> statuses);

    Uni<List<DeliveryRouteAssignment>> findByCollectionRequestId(String collectionRequestId);

    Uni<Boolean> markStopCompleted(String assignmentId, String collectionRequestId, LocalDateTime completedAt);
}
