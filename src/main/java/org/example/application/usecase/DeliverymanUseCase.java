package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.deliveryman.DeliverymanModels.AssignDeliveryRouteCommand;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteAssignment;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteLog;
import org.example.application.deliveryman.DeliverymanModels.FailStopCommand;
import org.example.application.deliveryman.DeliverymanModels.DeliverymanProfile;
import org.example.application.deliveryman.DeliverymanModels.RouteLogEventType;
import org.example.application.deliveryman.DeliverymanModels.RouteAssignmentStatus;
import org.example.application.deliveryman.DeliverymanModels.SyncDeliverymanCommand;
import org.example.application.port.out.DeliveryRouteAssignmentPort;
import org.example.application.port.out.DeliveryRouteLogPort;
import org.example.application.port.out.CollectionRequestPort;
import org.example.application.port.out.AuthenticatedActorPort;
import org.example.application.port.out.EventPort;
import org.example.application.port.out.DeliverymanDirectoryPort;
import org.example.application.port.out.DeliverymanPort;
import org.example.application.port.out.SavedRoutePort;
import org.example.application.route.RouteModels.RoutePlan;
import org.example.application.route.RouteModels.RouteStop;
import org.example.application.route.SavedRouteModels.SavedRouteStatus;
import org.example.infrastructure.event.CollectionEvent;
import org.example.domain.entity.CollectionRequest;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Singleton
public class DeliverymanUseCase {
    private static final LocalDateTime DEFAULT_COMPLETION_CODE_CUTOVER = LocalDateTime.of(2026, 11, 7, 0, 0);

    @ConfigProperty(name = "completion-code.historical-cutover", defaultValue = "2026-11-07T00:00:00")
    LocalDateTime completionCodeHistoricalCutover = DEFAULT_COMPLETION_CODE_CUTOVER;

    @Inject
    DeliverymanPort deliverymanPort;

    @Inject
    DeliverymanDirectoryPort deliverymanDirectoryPort;

    @Inject
    DeliveryRouteAssignmentPort assignmentPort;

    @Inject
    DeliveryRouteLogPort routeLogPort;

    @Inject
    SavedRoutePort savedRoutePort;

    @Inject
    CompletionUseCase completionUseCase;

    @Inject
    AuthenticatedActorPort authenticatedActorPort;

    @Inject
    CollectionRequestPort collectionRequestPort;

    @Inject
    EventPort eventPort;

    @Inject
    CollectionStatusNotificationUseCase notificationUseCase;

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
                    return areAllStopsCompleted(stopIds)
                            .flatMap(allCompleted -> {
                                if (allCompleted) {
                                    return Uni.createFrom().failure(new IllegalStateException("All collections for this vehicle are already completed"));
                                }
                                return deliverymanDirectoryPort.findById(command.deliverymanId())
                                        .onItem().ifNull().failWith(() -> new IllegalArgumentException("Deliveryman not found: " + command.deliverymanId()))
                                        .flatMap(deliveryman -> {
                                            if (!Objects.equals(deliveryman.collectorId(), command.collectorId())) {
                                                return Uni.createFrom().failure(new IllegalStateException("Deliveryman does not belong to collector"));
                                            }
                                            if (!deliveryman.active()) {
                                                return Uni.createFrom().failure(new IllegalStateException("Deliveryman is not active"));
                                            }
                                            return ensureStopsNotAssignedElsewhere(stopIds, command.savedRouteId(), vehicleIndex, 0)
                                                    .flatMap(ignored -> assignmentPort.findOpenBySavedRouteIdAndVehicleIndex(command.savedRouteId(), vehicleIndex))
                                                    .flatMap(existing -> {
                                                        boolean savedAssignmentCompleted = savedRoute.assignments() != null
                                                                && savedRoute.assignments().stream()
                                                                .filter(item -> item.vehicleIndex() == vehicleIndex)
                                                                .anyMatch(item -> item.status() == org.example.application.route.SavedRouteModels.RouteAssignmentStatus.COMPLETED
                                                                        || item.completedCollectionRequestIds().containsAll(stopIds));
                                                        if (savedAssignmentCompleted) {
                                                            return Uni.createFrom().failure(new IllegalStateException("All collections for this vehicle are already completed"));
                                                        }
                                                        if (existing != null && existing.completedCollectionRequestIds().containsAll(stopIds)) {
                                                            return Uni.createFrom().failure(new IllegalStateException("All collections for this vehicle are already completed"));
                                                        }
                                                        if (existing != null && isTerminal(existing.status())) {
                                                            return Uni.createFrom().failure(new IllegalStateException("This vehicle route already has a completed assignment"));
                                                        }
                                                        if (existing != null) {
                                                            if (Objects.equals(existing.deliverymanId(), command.deliverymanId())) {
                                                                return Uni.createFrom().item(existing);
                                                            }
                                                            DeliveryRouteAssignment reassigned = existing.reassign(command.deliverymanId(), LocalDateTime.now());
                                                            return assignmentPort.update(reassigned).replaceWith(reassigned);
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
                });
    }

    private Uni<Void> ensureStopsNotAssignedElsewhere(
            List<String> stopIds,
            String savedRouteId,
            int vehicleIndex,
            int stopIndex
    ) {
        if (stopIndex >= stopIds.size()) {
            return Uni.createFrom().voidItem();
        }

        String collectionRequestId = stopIds.get(stopIndex);
        return assignmentPort.findByCollectionRequestId(collectionRequestId)
                .flatMap(assignments -> {
                    boolean assignedToAnotherRoute = assignments != null && assignments.stream()
                            .anyMatch(assignment -> !Objects.equals(assignment.savedRouteId(), savedRouteId)
                                    || assignment.vehicleIndex() != vehicleIndex);
                    if (assignedToAnotherRoute) {
                        return Uni.createFrom().failure(new IllegalStateException(
                                "Collection request is already assigned to another delivery route: " + collectionRequestId));
                    }
                    return ensureStopsNotAssignedElsewhere(stopIds, savedRouteId, vehicleIndex, stopIndex + 1);
                });
    }

    private Uni<Boolean> areAllStopsCompleted(List<String> stopIds) {
        if (collectionRequestPort == null) {
            return Uni.createFrom().item(false);
        }
        return collectionRequestPort.findByIds(stopIds)
                .map(requests -> requests.size() == stopIds.size()
                        && requests.stream().allMatch(request -> request != null && request.getStatus() == CollectionRequest.Status.COMPLETED));
    }

    private boolean isTerminal(RouteAssignmentStatus status) {
        return status == RouteAssignmentStatus.COMPLETED
                || status == RouteAssignmentStatus.PARTIALLY_COMPLETED
                || status == RouteAssignmentStatus.FAILED
                || status == RouteAssignmentStatus.CANCELLED;
    }

    public Uni<List<DeliveryRouteAssignment>> listActiveAssignments(String deliverymanId) {
        validateRequired(deliverymanId, "deliverymanId");
        return assignmentPort.findByDeliverymanIdAndStatuses(
                deliverymanId,
                List.of(RouteAssignmentStatus.ASSIGNED, RouteAssignmentStatus.IN_PROGRESS,
                        RouteAssignmentStatus.COMPLETED, RouteAssignmentStatus.PARTIALLY_COMPLETED,
                        RouteAssignmentStatus.FAILED, RouteAssignmentStatus.CANCELLED)
        );
    }

    public Uni<DeliveryRouteAssignment> startAssignment(String deliverymanId, String assignmentId) {
        return loadAssignmentForDeliveryman(deliverymanId, assignmentId)
                .flatMap(assignment -> {
                    LocalDateTime now = LocalDateTime.now();
                    DeliveryRouteAssignment started = assignment.start(now);
                    DeliveryRouteLog log = DeliveryRouteLog.create(started, null, RouteLogEventType.ROUTE_STARTED, null, null, now);
                    return assignmentPort.update(started)
                            .flatMap(it -> routeLogPort.append(log))
                            .flatMap(it -> markStopsOnTheWay(started))
                            .replaceWith(started);
                });
    }

    private Uni<Void> markStopsOnTheWay(DeliveryRouteAssignment assignment) {
        return collectionRequestPort.findByIds(assignment.assignedCollectionRequestIds())
                .flatMap(requests -> Uni.join().all(requests.stream().map(request -> {
                    if (request == null || request.getStatus() != CollectionRequest.Status.IN_PROGRESS) {
                        return Uni.createFrom().voidItem();
                    }
                    request.setStatus(CollectionRequest.Status.ON_THE_WAY);
                    CollectionEvent event = new CollectionEvent();
                    event.setEventType("COLLECTION_ON_THE_WAY");
                    event.setRequestId(request.getId());
                    event.setGeneratorId(request.getGeneratorId());
                    event.setCollectorId(assignment.collectorId());
                    event.setStatus(request.getStatus().toString());
                    event.setTimestamp(Instant.now().toString());
                    event.setActorType("DELIVERYMAN");
                    event.setActorId(assignment.deliverymanId());
                    return collectionRequestPort.update(request)
                            .flatMap(ignored -> eventPort.publishCollectionEvent(event))
                            .flatMap(ignored -> notificationUseCase.notifyGenerator(request, "COLLECTION_ON_THE_WAY"));
                }).toList()).andFailFast().replaceWithVoid());
    }

    public Uni<DeliveryRouteAssignment> completeAssignedStop(String deliverymanId, String assignmentId, String collectionRequestId) {
        return Uni.createFrom().failure(new CompletionCodeConflictException(
                "COMPLETION_CODE_REQUIRED", "A valid completion code is required to complete a delivery stop"));
    }

    public Uni<CompletedStopResult> completeAssignedStop(
            String deliverymanId,
            String assignmentId,
            String collectionRequestId,
            String bearerToken,
            String code,
            String idempotencyKey
    ) {
        validateRequired(collectionRequestId, "collectionRequestId");
        if (bearerToken == null || bearerToken.isBlank()) {
            return Uni.createFrom().failure(new CompletionCodeUnauthenticatedException());
        }
        return authenticatedActorPort.resolve(bearerToken)
                .onFailure(org.example.infrastructure.identity.RegisterAuthenticatedActorAdapter.ActorUnauthenticatedException.class)
                .transform(ignored -> new CompletionCodeUnauthenticatedException())
                .flatMap(actor -> {
                    if (actor == null || !"DELIVERYMAN".equals(actor.role())
                            || !Objects.equals(actor.deliverymanId(), deliverymanId)) {
                        return Uni.createFrom().failure(new CompletionCodeForbiddenException());
                    }
                    return loadAssignmentForDeliverymanCompletion(deliverymanId, assignmentId)
                            .flatMap(assignment -> {
                                if (!assignment.assignedCollectionRequestIds().contains(collectionRequestId)) {
                                    return Uni.createFrom().failure(new CompletionCodeForbiddenException());
                                }
                                return completionUseCase.confirmDeliverymanWithCode(
                                                collectionRequestId, bearerToken, code, idempotencyKey)
                                        .flatMap(completion -> Uni.combine().all().unis(
                                                        assignmentPort.findById(assignmentId),
                                                        collectionRequestPort.findById(collectionRequestId))
                                                .asTuple()
                                                .map(tuple -> {
                                                    DeliveryRouteAssignment reconciled = tuple.getItem1() == null
                                                            ? assignment : tuple.getItem1();
                                                    CollectionRequest request = tuple.getItem2();
                                                    boolean pending = request != null && request.getCompletionEvidence() != null
                                                            && !request.getCompletionEvidence().pendingEffects().isEmpty();
                                                    return new CompletedStopResult(
                                                            reconciled, collectionRequestId, completion.completionId(), pending);
                                                }));
                            });
                });
    }

    public record CompletedStopResult(
            DeliveryRouteAssignment assignment,
            String requestId,
            String completionId,
            boolean reconciliationPending
    ) {
    }

    private Uni<DeliveryRouteAssignment> loadAssignmentForDeliverymanCompletion(String deliverymanId, String assignmentId) {
        validateRequired(deliverymanId, "deliverymanId");
        validateRequired(assignmentId, "assignmentId");
        return assignmentPort.findById(assignmentId)
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Assignment not found: " + assignmentId))
                .flatMap(assignment -> {
                    if (!Objects.equals(assignment.deliverymanId(), deliverymanId)) {
                        return Uni.createFrom().failure(new CompletionCodeForbiddenException());
                    }
                    return Uni.createFrom().item(assignment);
                });
    }

    public Uni<DeliveryRouteAssignment> failAssignedStop(String deliverymanId, String assignmentId, String collectionRequestId, FailStopCommand command) {
        validateRequired(collectionRequestId, "collectionRequestId");
        validateRequired(command == null ? null : command.failureReason(), "failureReason");
        return loadAssignmentForDeliveryman(deliverymanId, assignmentId)
                .flatMap(assignment -> {
                    if (!assignment.assignedCollectionRequestIds().contains(collectionRequestId)) {
                        return Uni.createFrom().failure(new IllegalStateException("Collection request is not assigned to deliveryman"));
                    }
                    LocalDateTime now = LocalDateTime.now();
                    DeliveryRouteAssignment current = assignment.status() == RouteAssignmentStatus.ASSIGNED
                            ? assignment.start(now)
                            : assignment;
                    DeliveryRouteLog log = DeliveryRouteLog.create(
                            current,
                            collectionRequestId,
                            RouteLogEventType.STOP_FAILED,
                            command.note(),
                            command.failureReason(),
                            now
                    );
                    return assignmentPort.update(current)
                            .flatMap(it -> routeLogPort.append(log))
                            .replaceWith(current);
                });
    }

    public Uni<DeliveryRouteAssignment> finishAssignment(String deliverymanId, String assignmentId) {
        return loadAssignmentForDeliveryman(deliverymanId, assignmentId)
                .flatMap(assignment -> routeLogPort.findBySavedRouteIdAndVehicleIndex(assignment.savedRouteId(), assignment.vehicleIndex())
                        .flatMap(logs -> collectionRequestPort.findByIds(assignment.assignedCollectionRequestIds())
                                .flatMap(requests -> {
                            List<CollectionRequest> safeRequests = requests == null ? List.of() : requests;
                            java.util.Map<String, CollectionRequest> requestById = safeRequests.stream()
                                    .filter(java.util.Objects::nonNull)
                                    .collect(java.util.stream.Collectors.toMap(CollectionRequest::getId, request -> request,
                                            (first, ignored) -> first));
                            List<String> completedIds = assignment.assignedCollectionRequestIds().stream()
                                    .filter(requestId -> isValidatedOrHistoricalCompletion(requestById.get(requestId)))
                                    .distinct()
                                    .toList();
                            long failed = (logs == null ? List.<DeliveryRouteLog>of() : logs).stream()
                                    .filter(log -> log.eventType() == RouteLogEventType.STOP_FAILED)
                                    .map(DeliveryRouteLog::collectionRequestId)
                                    .filter(assignment.assignedCollectionRequestIds()::contains)
                                    .distinct()
                                    .count();
                            int assigned = assignment.assignedCollectionRequestIds().size();
                            DeliveryRouteAssignment withProgress = assignment;
                            LocalDateTime now = LocalDateTime.now();
                            for (String requestId : completedIds) {
                                withProgress = withProgress.completeStop(requestId, now);
                            }
                            RouteAssignmentStatus outcome;
                            RouteLogEventType eventType;
                            if (assigned > 0 && completedIds.size() == assigned) {
                                outcome = RouteAssignmentStatus.COMPLETED;
                                eventType = RouteLogEventType.ROUTE_COMPLETED;
                            } else if (assigned > 0 && failed == assigned) {
                                outcome = RouteAssignmentStatus.FAILED;
                                eventType = RouteLogEventType.ROUTE_FAILED;
                            } else {
                                outcome = RouteAssignmentStatus.PARTIALLY_COMPLETED;
                                eventType = RouteLogEventType.ROUTE_COMPLETED;
                            }
                            DeliveryRouteAssignment finished = withProgress.finish(outcome, now);
                            DeliveryRouteLog log = DeliveryRouteLog.create(finished, null, eventType, null, null, now);
                            return assignmentPort.update(finished)
                                    .replaceWith(new FinishedAssignment(finished, log));
                        })
                        .flatMap(result -> routeLogPort.append(result.log()).replaceWith(result.assignment()))));
    }

    private boolean isValidatedOrHistoricalCompletion(CollectionRequest request) {
        if (request == null || request.getStatus() != CollectionRequest.Status.COMPLETED) {
            return false;
        }
        if (request.getCompletionEvidence() != null) {
            return true;
        }
        LocalDateTime updatedAt = request.getUpdatedAt();
        return request.getCompletionCode() == null && updatedAt != null && updatedAt.isBefore(completionCodeHistoricalCutover);
    }

    private record FinishedAssignment(DeliveryRouteAssignment assignment, DeliveryRouteLog log) { }

    private Uni<DeliveryRouteAssignment> loadAssignmentForDeliveryman(String deliverymanId, String assignmentId) {
        validateRequired(deliverymanId, "deliverymanId");
        validateRequired(assignmentId, "assignmentId");
        return assignmentPort.findById(assignmentId)
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Assignment not found: " + assignmentId))
                .flatMap(assignment -> {
                    if (!Objects.equals(assignment.deliverymanId(), deliverymanId)) {
                        return Uni.createFrom().failure(new IllegalStateException("Assignment does not belong to deliveryman"));
                    }
                    if (assignment.status() == RouteAssignmentStatus.COMPLETED
                            || assignment.status() == RouteAssignmentStatus.PARTIALLY_COMPLETED
                            || assignment.status() == RouteAssignmentStatus.FAILED
                            || assignment.status() == RouteAssignmentStatus.CANCELLED) {
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
