package org.example.infrastructure.repository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.example.domain.entity.Address;

import java.util.Date;

@Singleton
public class AddressCacheRepository {

    private static final String DB_NAME = "collections";
    private static final String COLLECTION_NAME = "address_cache";

    @Inject
    ReactiveMongoClient mongoClient;

    private ReactiveMongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB_NAME).getCollection(COLLECTION_NAME, Document.class);
    }

    public Uni<Address> findByKey(String key) {
        return getCollection()
                .find(Filters.eq("_id", key))
                .collect().first()
                .onItem().ifNotNull()
                .transform(this::fromDocument);
    }

    public Uni<Void> upsert(String key, Address address, String source) {
        Document doc = new Document()
                .append("_id", key)
                .append("street", normalize(address.getStreet()))
                .append("city", normalize(address.getCity()))
                .append("zipCode", normalize(address.getZipCode()))
                .append("number", normalize(address.getNumber()))
                .append("state", normalize(address.getState()))
                .append("latitude", address.getLatitude())
                .append("longitude", address.getLongitude())
                .append("source", source)
                .append("updatedAt", new Date())
                .append("createdAt", new Date());

        return getCollection()
                .replaceOne(Filters.eq("_id", key), doc, new ReplaceOptions().upsert(true))
                .replaceWithVoid();
    }

    private Address fromDocument(Document doc) {
        return new Address(
                doc.getString("_id"),
                doc.getString("street"),
                doc.getString("city"),
                doc.getString("zipCode"),
                doc.getString("number"),
                doc.getString("state"),
                doc.getDouble("latitude"),
                doc.getDouble("longitude"),
                null,
                doc.getString("source")
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
