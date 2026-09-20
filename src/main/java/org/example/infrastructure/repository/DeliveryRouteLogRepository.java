package org.example.infrastructure.repository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import io.quarkus.mongodb.FindOptions;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.example.application.deliveryman.DeliverymanModels.DeliveryRouteLog;
import org.example.application.deliveryman.DeliverymanModels.RouteLogEventType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Singleton
public class DeliveryRouteLogRepository {
    private static final String DB_NAME = "collections";
    private static final String COLLECTION_NAME = "delivery_route_logs";

    @Inject
    ReactiveMongoClient mongoClient;

    private ReactiveMongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB_NAME).getCollection(COLLECTION_NAME, Document.class);
    }

    public Uni<Void> append(DeliveryRouteLog log) {
        return getCollection().insertOne(toDocument(log)).replaceWithVoid();
    }

    public Uni<List<DeliveryRouteLog>> findBySavedRouteIdAndVehicleIndex(String savedRouteId, int vehicleIndex) {
        return getCollection()
                .find(Filters.and(
                        Filters.eq("savedRouteId", savedRouteId),
                        Filters.eq("vehicleIndex", vehicleIndex)
                ), new FindOptions().sort(Sorts.ascending("createdAt")))
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    private Document toDocument(DeliveryRouteLog log) {
        return new Document()
                .append("_id", log.id())
                .append("savedRouteId", log.savedRouteId())
                .append("vehicleIndex", log.vehicleIndex())
                .append("collectorId", log.collectorId())
                .append("deliverymanId", log.deliverymanId())
                .append("collectionRequestId", log.collectionRequestId())
                .append("eventType", log.eventType().toString())
                .append("note", log.note())
                .append("failureReason", log.failureReason())
                .append("createdAt", toDate(log.createdAt()));
    }

    private DeliveryRouteLog fromDocument(Document doc) {
        return new DeliveryRouteLog(
                doc.getString("_id"),
                doc.getString("savedRouteId"),
                doc.getInteger("vehicleIndex", 0),
                doc.getString("collectorId"),
                doc.getString("deliverymanId"),
                doc.getString("collectionRequestId"),
                RouteLogEventType.valueOf(doc.getString("eventType")),
                doc.getString("note"),
                doc.getString("failureReason"),
                toLocalDateTime(doc.getDate("createdAt"))
        );
    }

    private Date toDate(LocalDateTime value) {
        return value == null ? null : Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    private LocalDateTime toLocalDateTime(Date value) {
        return value == null ? null : LocalDateTime.ofInstant(value.toInstant(), ZoneId.systemDefault());
    }
}
