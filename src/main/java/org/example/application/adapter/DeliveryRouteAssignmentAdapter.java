package org.example.application.adapter;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteAssignment;
import org.example.application.deliveryman.DeliverymanModels.RouteAssignmentStatus;
import org.example.application.port.out.DeliveryRouteAssignmentPort;
import org.example.infrastructure.repository.DeliveryRouteAssignmentRepository;

import java.util.List;
import java.time.LocalDateTime;

@Singleton
public class DeliveryRouteAssignmentAdapter implements DeliveryRouteAssignmentPort {
    @Inject
    DeliveryRouteAssignmentRepository repository;

    @Override
    public Uni<Void> save(DeliveryRouteAssignment assignment) {
        return repository.save(assignment);
    }

    @Override
    public Uni<Void> update(DeliveryRouteAssignment assignment) {
        return repository.update(assignment);
    }

    @Override
    public Uni<DeliveryRouteAssignment> findById(String assignmentId) {
        return repository.findById(assignmentId);
    }

    @Override
    public Uni<DeliveryRouteAssignment> findOpenBySavedRouteIdAndVehicleIndex(String savedRouteId, int vehicleIndex) {
        return repository.findOpenBySavedRouteIdAndVehicleIndex(savedRouteId, vehicleIndex);
    }

    @Override
    public Uni<List<DeliveryRouteAssignment>> findByDeliverymanIdAndStatuses(String deliverymanId, List<RouteAssignmentStatus> statuses) {
        return repository.findByDeliverymanIdAndStatuses(deliverymanId, statuses);
    }

    @Override
    public Uni<List<DeliveryRouteAssignment>> findByCollectionRequestId(String collectionRequestId) {
        return repository.findByCollectionRequestId(collectionRequestId);
    }

    @Override
    public Uni<Boolean> markStopCompleted(String assignmentId, String collectionRequestId, LocalDateTime completedAt) {
        return repository.markStopCompleted(assignmentId, collectionRequestId, completedAt);
    }
}
