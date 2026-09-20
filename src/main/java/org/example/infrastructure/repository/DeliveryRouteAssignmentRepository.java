package org.example.infrastructure.repository;

import com.mongodb.client.model.Filters;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteAssignment;
import org.example.application.deliveryman.DeliverymanModels.RouteAssignmentStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Singleton
public class DeliveryRouteAssignmentRepository {
    private static final String DB_NAME = "collections";
    private static final String COLLECTION_NAME = "delivery_route_assignments";

    @Inject
    ReactiveMongoClient mongoClient;

    private ReactiveMongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB_NAME).getCollection(COLLECTION_NAME, Document.class);
    }

    public Uni<Void> save(DeliveryRouteAssignment assignment) {
        return getCollection().insertOne(toDocument(assignment)).replaceWithVoid();
    }

    public Uni<Void> update(DeliveryRouteAssignment assignment) {
        return getCollection()
                .replaceOne(Filters.eq("_id", assignment.id()), toDocument(assignment))
                .replaceWithVoid();
    }

    public Uni<DeliveryRouteAssignment> findById(String assignmentId) {
        return getCollection()
                .find(Filters.eq("_id", assignmentId))
                .collect().first()
                .onItem().ifNotNull().transform(this::fromDocument);
    }

    public Uni<DeliveryRouteAssignment> findOpenBySavedRouteIdAndVehicleIndex(String savedRouteId, int vehicleIndex) {
        return getCollection()
                .find(Filters.and(
                        Filters.eq("savedRouteId", savedRouteId),
                        Filters.eq("vehicleIndex", vehicleIndex),
                        Filters.in("status", List.of(RouteAssignmentStatus.ASSIGNED.toString(), RouteAssignmentStatus.IN_PROGRESS.toString()))
                ))
                .collect().first()
                .onItem().ifNotNull().transform(this::fromDocument);
    }

    public Uni<List<DeliveryRouteAssignment>> findByDeliverymanIdAndStatuses(String deliverymanId, List<RouteAssignmentStatus> statuses) {
        Bson filter = statuses == null || statuses.isEmpty()
                ? Filters.eq("deliverymanId", deliverymanId)
                : Filters.and(
                        Filters.eq("deliverymanId", deliverymanId),
                        Filters.in("status", statuses.stream().map(Enum::toString).toList())
                );
        return getCollection()
                .find(filter)
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    private Document toDocument(DeliveryRouteAssignment assignment) {
        return new Document()
                .append("_id", assignment.id())
                .append("collectorId", assignment.collectorId())
                .append("deliverymanId", assignment.deliverymanId())
                .append("savedRouteId", assignment.savedRouteId())
                .append("vehicleIndex", assignment.vehicleIndex())
                .append("status", assignment.status().toString())
                .append("assignedCollectionRequestIds", assignment.assignedCollectionRequestIds())
                .append("completedCollectionRequestIds", assignment.completedCollectionRequestIds())
                .append("assignedAt", toDate(assignment.assignedAt()))
                .append("startedAt", toDate(assignment.startedAt()))
                .append("completedAt", toDate(assignment.completedAt()))
                .append("updatedAt", toDate(assignment.updatedAt()));
    }

    private DeliveryRouteAssignment fromDocument(Document doc) {
        return new DeliveryRouteAssignment(
                doc.getString("_id"),
                doc.getString("collectorId"),
                doc.getString("deliverymanId"),
                doc.getString("savedRouteId"),
                doc.getInteger("vehicleIndex", 0),
                RouteAssignmentStatus.valueOf(doc.getString("status")),
                doc.getList("assignedCollectionRequestIds", String.class),
                doc.getList("completedCollectionRequestIds", String.class),
                toLocalDateTime(doc.getDate("assignedAt")),
                toLocalDateTime(doc.getDate("startedAt")),
                toLocalDateTime(doc.getDate("completedAt")),
                toLocalDateTime(doc.getDate("updatedAt"))
        );
    }

    private Date toDate(LocalDateTime value) {
        return value == null ? null : Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    private LocalDateTime toLocalDateTime(Date value) {
        return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
    }
}
