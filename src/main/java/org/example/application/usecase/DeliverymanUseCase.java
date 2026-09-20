package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.example.application.deliveryman.DeliverymanModels.AssignDeliveryRouteCommand;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteAssignment;
import org.example.application.deliveryman.DeliverymanModels.DeliverymanProfile;
import org.example.application.deliveryman.DeliverymanModels.RouteAssignmentStatus;
import org.example.application.deliveryman.DeliverymanModels.SyncDeliverymanCommand;
import org.example.application.port.out.DeliveryRouteAssignmentPort;
import org.example.application.port.out.DeliverymanPort;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.RouteModels.RoutePlan;
import org.example.application.route.RouteModels.RouteStop;
import org.example.application.route.SavedRouteModels.SavedRouteStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Singleton
public class DeliverymanUseCase {
    @Inject
    DeliverymanPort deliverymanPort;

    @Inject
    DeliveryRouteAssignmentPort assignmentPort;

    @Inject
    SavedRoutePort savedRoutePort;

    @Inject
    CompletionUseCase completionUseCase;

    public Uni<Void> syncDeliveryman(SyncDeliverymanCommand command) {
        validateRequired(command == null ? null : command.id(), "deliverymanId");
        validateRequired(command.collectorId(), "collectorId");
        LocalDateTime now = LocalDateTime.now();
        DeliverymanProfile profile = DeliverymanProfile.create(
                command.id(),
                command.collectorId(),
                command.name(),
                command.email(),
                command.phone(),
                command.licenseNumber(),
                command.vehicleId(),
                command.vehiclePlate(),
                now
        );
        return deliverymanPort.saveOrUpdate(profile);
    }

    public Uni<List<DeliverymanProfile>> listDeliverymen(String collectorId) {
        validateRequired(collectorId, "collectorId");
        return deliverymanPort.findByCollectorId(collectorId);
    }

    public Uni<DeliveryRouteAssignment> assignRoute(AssignDeliveryRouteCommand command) {
        validateRequired(command == null ? null : command.collectorId(), "collectorId");
        validateRequired(command.deliverymanId(), "deliverymanId");
        validateRequired(command.savedRouteId(), "savedRouteId");
        int vehicleIndex = command.vehicleIndex() == null ? 0 : command.vehicleIndex();
        if (vehicleIndex < 0) {
            return Uni.createFrom().failure(new IllegalArgumentException("vehicleIndex must be non-negative"));
        }

        return savedRoutePort.findById(command.savedRouteId())
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Saved route not found: " + command.savedRouteId()))
                .flatMap(savedRoute -> {
                    if (!Objects.equals(savedRoute.collectorId(), command.collectorId())) {
                        return Uni.createFrom().failure(new IllegalStateException("Saved route does not belong to collector"));
                    }
                    if (savedRoute.status() != SavedRouteStatus.OPEN) {
                        return Uni.createFrom().failure(new IllegalStateException("Saved route is not OPEN"));
                    }
                    RoutePlan plan = savedRoute.suggestion().routes().stream()
                            .filter(route -> route.vehicleIndex() == vehicleIndex)
                            .findFirst()
                            .orElse(null);
                    if (plan == null) {
                        return Uni.createFrom().failure(new IllegalArgumentException("Saved route vehicle index not found"));
                    }
                    List<String> stopIds = plan.stops().stream().map(RouteStop::collectionRequestId).toList();
                    if (stopIds.isEmpty()) {
                        return Uni.createFrom().failure(new IllegalStateException("Saved route vehicle has no stops"));
                    }
                    return deliverymanPort.findById(command.deliverymanId())
                            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Deliveryman not found: " + command.deliverymanId()))
                            .flatMap(deliveryman -> {
                                if (!Objects.equals(deliveryman.collectorId(), command.collectorId())) {
                                    return Uni.createFrom().failure(new IllegalStateException("Deliveryman does not belong to collector"));
                                }
                                return assignmentPort.findOpenBySavedRouteIdAndVehicleIndex(command.savedRouteId(), vehicleIndex)
                                        .flatMap(existing -> {
                                            if (existing != null) {
                                                return Uni.createFrom().failure(new IllegalStateException("Saved route vehicle already assigned"));
                                            }
                                            DeliveryRouteAssignment assignment = DeliveryRouteAssignment.create(
                                                    command.collectorId(),
                                                    command.deliverymanId(),
                                                    command.savedRouteId(),
                                                    vehicleIndex,
                                                    stopIds,
                                                    LocalDateTime.now()
                                            );
                                            return assignmentPort.save(assignment).replaceWith(assignment);
                                        });
                            });
                });
    }

    public Uni<List<DeliveryRouteAssignment>> listActiveAssignments(String deliverymanId) {
        validateRequired(deliverymanId, "deliverymanId");
        return assignmentPort.findByDeliverymanIdAndStatuses(
                deliverymanId,
                List.of(RouteAssignmentStatus.ASSIGNED, RouteAssignmentStatus.IN_PROGRESS)
        );
    }

    public Uni<DeliveryRouteAssignment> startAssignment(String deliverymanId, String assignmentId) {
        return loadAssignmentForDeliveryman(deliverymanId, assignmentId)
                .flatMap(assignment -> {
                    DeliveryRouteAssignment started = assignment.start(LocalDateTime.now());
                    return assignmentPort.update(started).replaceWith(started);
                });
    }

    public Uni<DeliveryRouteAssignment> completeAssignedStop(String deliverymanId, String assignmentId, String collectionRequestId) {
        validateRequired(collectionRequestId, "collectionRequestId");
        return loadAssignmentForDeliveryman(deliverymanId, assignmentId)
                .flatMap(assignment -> {
                    if (!assignment.assignedCollectionRequestIds().contains(collectionRequestId)) {
                        return Uni.createFrom().failure(new IllegalStateException("Collection request is not assigned to deliveryman"));
                    }
                    DeliveryRouteAssignment current = assignment.status() == RouteAssignmentStatus.ASSIGNED
                            ? assignment.start(LocalDateTime.now())
                            : assignment;
                    DeliveryRouteAssignment updated = current.completeStop(collectionRequestId, LocalDateTime.now());
                    return assignmentPort.update(updated)
                            .flatMap(it -> completionUseCase.confirmCollectorCompletion(collectionRequestId))
                            .replaceWith(updated);
                });
    }

    private Uni<DeliveryRouteAssignment> loadAssignmentForDeliveryman(String deliverymanId, String assignmentId) {
        validateRequired(deliverymanId, "deliverymanId");
        validateRequired(assignmentId, "assignmentId");
        return assignmentPort.findById(assignmentId)
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Assignment not found: " + assignmentId))
                .flatMap(assignment -> {
                    if (!Objects.equals(assignment.deliverymanId(), deliverymanId)) {
                        return Uni.createFrom().failure(new IllegalStateException("Assignment does not belong to deliveryman"));
                    }
                    if (assignment.status() == RouteAssignmentStatus.COMPLETED || assignment.status() == RouteAssignmentStatus.CANCELLED) {
                        return Uni.createFrom().failure(new IllegalStateException("Assignment is not active"));
                    }
                    return Uni.createFrom().item(assignment);
                });
    }

    private void validateRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
