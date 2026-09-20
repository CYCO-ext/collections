package org.example.infrastructure.repository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.example.application.deliveryman.DeliverymanModels.DeliverymanProfile;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Singleton
public class DeliverymanRepository {
    private static final String DB_NAME = "collections";
    private static final String COLLECTION_NAME = "deliverymen";

    @Inject
    ReactiveMongoClient mongoClient;

    private ReactiveMongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB_NAME).getCollection(COLLECTION_NAME, Document.class);
    }

    public Uni<Void> saveOrUpdate(DeliverymanProfile deliveryman) {
        return getCollection()
                .replaceOne(Filters.eq("_id", deliveryman.id()), toDocument(deliveryman), new ReplaceOptions().upsert(true))
                .replaceWithVoid();
    }

    public Uni<DeliverymanProfile> findById(String deliverymanId) {
        return getCollection()
                .find(Filters.eq("_id", deliverymanId))
                .collect().first()
                .onItem().ifNotNull().transform(this::fromDocument);
    }

    public Uni<List<DeliverymanProfile>> findByCollectorId(String collectorId) {
        return getCollection()
                .find(Filters.eq("collectorId", collectorId))
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    private Document toDocument(DeliverymanProfile deliveryman) {
        return new Document()
                .append("_id", deliveryman.id())
                .append("collectorId", deliveryman.collectorId())
                .append("name", deliveryman.name())
                .append("email", deliveryman.email())
                .append("phone", deliveryman.phone())
                .append("licenseNumber", deliveryman.licenseNumber())
                .append("vehicleId", deliveryman.vehicleId())
                .append("vehiclePlate", deliveryman.vehiclePlate())
                .append("createdAt", toDate(deliveryman.createdAt()))
                .append("updatedAt", toDate(deliveryman.updatedAt()));
    }

    private DeliverymanProfile fromDocument(Document doc) {
        return new DeliverymanProfile(
                doc.getString("_id"),
                doc.getString("collectorId"),
                doc.getString("name"),
                doc.getString("email"),
                doc.getString("phone"),
                doc.getString("licenseNumber"),
                doc.getString("vehicleId"),
                doc.getString("vehiclePlate"),
                toLocalDateTime(doc.getDate("createdAt")),
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
