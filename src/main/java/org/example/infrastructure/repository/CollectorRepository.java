package org.example.infrastructure.repository;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.example.domain.entity.Collector;
import com.mongodb.client.model.Filters;

import java.util.List;

@Singleton
public class CollectorRepository {

    @Inject
    ReactiveMongoClient mongoClient;

    private static final String DB_NAME = "collections";
    private static final String COLLECTION_NAME = "collectors";

    private ReactiveMongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB_NAME).getCollection(COLLECTION_NAME, Document.class);
    }

    public Uni<Void> save(Collector collector) {
        Document doc = toDocument(collector);
        return getCollection()
                .insertOne(doc)
                .replaceWithVoid();
    }

    public Uni<Void> update(Collector collector) {
        Document doc = toDocument(collector);
        return getCollection()
                .replaceOne(Filters.eq("_id", collector.getId()), doc)
                .replaceWithVoid();
    }

    public Uni<Collector> findById(String id) {
        return getCollection()
                .find(Filters.eq("_id", id))
                .collect().first()
                .onItem().ifNotNull()
                .transform(this::fromDocument);
    }

    public Uni<List<Collector>> findAll() {
        return getCollection()
                .find()
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    public Uni<List<Collector>> findByAcceptedMaterial(String materialId) {
        return getCollection()
                .find(Filters.in("acceptedMaterialIds", materialId))
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    public Uni<Void> upsert(Collector collector) {
        Document doc = toDocument(collector);
        return getCollection()
                .replaceOne(Filters.eq("_id", collector.getId()), doc,
                        new com.mongodb.client.model.ReplaceOptions().upsert(true))
                .replaceWithVoid();
    }

    private Document toDocument(Collector collector) {
        return new Document()
                .append("_id", collector.getId())
                .append("userId", collector.getUserId())
                .append("name", collector.getName())
                .append("address", collectorAddressToDoc(collector.getAddress()))
                .append("acceptedMaterialIds", collector.getAcceptedMaterialIds())
                .append("acceptanceRate", collector.getAcceptanceRate());
    }

    private Document collectorAddressToDoc(org.example.domain.entity.Address address) {
        return new Document()
                .append("id", address.getId())
                .append("street", address.getStreet())
                .append("city", address.getCity())
                .append("zipCode", address.getZipCode())
                .append("number", address.getNumber())
                .append("state", address.getState())
                .append("latitude", address.getLatitude())
                .append("longitude", address.getLongitude())
                .append("enrichmentStatus", address.getEnrichmentStatus())
                .append("enrichmentSource", address.getEnrichmentSource());
    }

    private Collector fromDocument(Document doc) {
        Document addrDoc = doc.get("address", Document.class);
        org.example.domain.entity.Address address = new org.example.domain.entity.Address(
                addrDoc.getString("id"),
                addrDoc.getString("street"),
                addrDoc.getString("city"),
                addrDoc.getString("zipCode"),
                addrDoc.getString("number"),
                addrDoc.getString("state"),
                addrDoc.getDouble("latitude"),
                addrDoc.getDouble("longitude"),
                addrDoc.getString("enrichmentStatus"),
                addrDoc.getString("enrichmentSource")
        );

        Collector collector = new Collector();
        collector.setId(doc.getString("_id"));
        collector.setUserId(doc.getString("userId"));
        collector.setName(doc.getString("name"));
        collector.setAddress(address);
        collector.setAcceptedMaterialIds(doc.getList("acceptedMaterialIds", String.class));
        collector.setAcceptanceRate(doc.getDouble("acceptanceRate"));
        return collector;
    }
}

