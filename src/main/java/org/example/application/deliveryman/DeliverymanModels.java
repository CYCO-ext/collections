package org.example.application.deliveryman;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class DeliverymanModels {
    private DeliverymanModels() {
    }

    public enum RouteAssignmentStatus {
        ASSIGNED,
        IN_PROGRESS,
        COMPLETED,
        PARTIALLY_COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum RouteLogEventType {
        ROUTE_STARTED,
        STOP_COMPLETED,
        STOP_FAILED,
        ROUTE_COMPLETED,
        ROUTE_FAILED
    }

    public record DeliveryRouteLog(
            String id,
            String savedRouteId,
            int vehicleIndex,
            String collectorId,
            String deliverymanId,
            String collectionRequestId,
            RouteLogEventType eventType,
            String note,
            String failureReason,
            LocalDateTime createdAt
    ) {
        public static DeliveryRouteLog create(
                DeliveryRouteAssignment assignment,
                String collectionRequestId,
                RouteLogEventType eventType,
                String note,
                String failureReason,
                LocalDateTime now
        ) {
            return new DeliveryRouteLog(
                    UUID.randomUUID().toString(),
                    assignment.savedRouteId(),
                    assignment.vehicleIndex(),
                    assignment.collectorId(),
                    assignment.deliverymanId(),
                    collectionRequestId,
                    eventType,
                    note,
                    failureReason,
                    now
            );
        }
    }

    public record DeliverymanProfile(
            String id,
            String collectorId,
            String name,
            String email,
            String phone,
            String licenseNumber,
            String vehicleId,
            String vehiclePlate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static DeliverymanProfile create(
                String id,
                String collectorId,
                String name,
                String email,
                String phone,
                String licenseNumber,
                String vehicleId,
                String vehiclePlate,
                LocalDateTime now
        ) {
            return new DeliverymanProfile(id, collectorId, name, email, phone, licenseNumber, vehicleId, vehiclePlate, now, now);
        }
    }

    public record DeliveryRouteAssignment(
            String id,
            String collectorId,
            String deliverymanId,
            String savedRouteId,
            int vehicleIndex,
            RouteAssignmentStatus status,
            List<String> assignedCollectionRequestIds,
            List<String> completedCollectionRequestIds,
            LocalDateTime assignedAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LocalDateTime updatedAt
    ) {
        public static DeliveryRouteAssignment create(
                String collectorId,
                String deliverymanId,
                String savedRouteId,
                int vehicleIndex,
                List<String> assignedCollectionRequestIds,
                LocalDateTime now
        ) {
            return new DeliveryRouteAssignment(
                    UUID.randomUUID().toString(),
                    collectorId,
                    deliverymanId,
                    savedRouteId,
                    vehicleIndex,
                    RouteAssignmentStatus.ASSIGNED,
                    assignedCollectionRequestIds,
                    List.of(),
                    now,
                    null,
                    null,
                    now
            );
        }

        public DeliveryRouteAssignment start(LocalDateTime now) {
            if (status != RouteAssignmentStatus.ASSIGNED) {
                return this;
            }
            return new DeliveryRouteAssignment(id, collectorId, deliverymanId, savedRouteId, vehicleIndex,
                    RouteAssignmentStatus.IN_PROGRESS, assignedCollectionRequestIds, completedCollectionRequestIds,
                    assignedAt, now, completedAt, now);
        }

        public DeliveryRouteAssignment completeStop(String collectionRequestId, LocalDateTime now) {
            List<String> completed = completedCollectionRequestIds == null ? List.of() : completedCollectionRequestIds;
            if (completed.contains(collectionRequestId)) {
                return this;
            }
            List<String> nextCompleted = new java.util.ArrayList<>(completed);
            nextCompleted.add(collectionRequestId);
            RouteAssignmentStatus nextStatus = nextCompleted.containsAll(assignedCollectionRequestIds)
                    ? RouteAssignmentStatus.COMPLETED
                    : RouteAssignmentStatus.IN_PROGRESS;
            return new DeliveryRouteAssignment(id, collectorId, deliverymanId, savedRouteId, vehicleIndex,
                    nextStatus, assignedCollectionRequestIds, nextCompleted, assignedAt, startedAt,
                    nextStatus == RouteAssignmentStatus.COMPLETED ? now : completedAt, now);
        }

        public DeliveryRouteAssignment finish(RouteAssignmentStatus outcome, LocalDateTime now) {
            if (outcome != RouteAssignmentStatus.COMPLETED
                    && outcome != RouteAssignmentStatus.PARTIALLY_COMPLETED
                    && outcome != RouteAssignmentStatus.FAILED) {
                throw new IllegalArgumentException("finish outcome must be terminal");
            }
            return new DeliveryRouteAssignment(id, collectorId, deliverymanId, savedRouteId, vehicleIndex,
                    outcome, assignedCollectionRequestIds, completedCollectionRequestIds,
                    assignedAt, startedAt, now, now);
        }

        public DeliveryRouteAssignment reassign(String nextDeliverymanId, LocalDateTime now) {
            if (status != RouteAssignmentStatus.ASSIGNED) {
                throw new IllegalStateException("Assignment already started");
            }
            return new DeliveryRouteAssignment(id, collectorId, nextDeliverymanId, savedRouteId, vehicleIndex,
                    status, assignedCollectionRequestIds, completedCollectionRequestIds,
                    assignedAt, startedAt, completedAt, now);
        }
    }

    public record AssignDeliveryRouteCommand(String collectorId, String deliverymanId, String savedRouteId, Integer vehicleIndex) {
    }

    public record FailStopCommand(String failureReason, String note) {
    }

    public record SyncDeliverymanCommand(
            String id,
            String collectorId,
            String name,
            String email,
            String phone,
            String licenseNumber,
            String vehicleId,
            String vehiclePlate
    ) {
    }
}
