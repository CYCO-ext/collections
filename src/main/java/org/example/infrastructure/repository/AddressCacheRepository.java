package org.example.infrastructure.repository;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.example.domain.entity.Address;
import com.mongodb.client.model.Filters;

import java.time.Instant;

@Singleton
public class AddressCacheRepository {

    @Inject
    ReactiveMongoClient mongoClient;

    private static final String DB_NAME = "collections";
    private static final String COLLECTION_NAME = "address_cache";

    private ReactiveMongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB_NAME).getCollection(COLLECTION_NAME, Document.class);
    }

    public Uni<Address> findByKey(String key) {
        return getCollection()
                .find(Filters.eq("_id", key))
                .collect().first()
                .onItem().ifNotNull().transform(this::fromDocument);
    }

    public Uni<Void> upsert(String key, Address address, String source) {
        Document doc = new Document()
                .append("_id", key)
                .append("street", address.getStreet())
                .append("city", address.getCity())
                .append("zipCode", address.getZipCode())
                .append("latitude", address.getLatitude())
                .append("longitude", address.getLongitude())
                .append("source", source)
                .append("createdAt", Instant.now().toEpochMilli());

        return getCollection()
                .replaceOne(Filters.eq("_id", key), doc, new com.mongodb.client.model.ReplaceOptions().upsert(true))
                .replaceWithVoid();
    }

    private Address fromDocument(Document doc) {
        return new Address(
                doc.getString("_id"),
                doc.getString("street"),
                doc.getString("city"),
                doc.getString("zipCode"),
                doc.getDouble("latitude"),
                doc.getDouble("longitude")
        );
    }
}
