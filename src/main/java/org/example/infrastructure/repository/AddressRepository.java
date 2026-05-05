package org.example.infrastructure.repository;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.example.domain.entity.Address;
import com.mongodb.client.model.Filters;

import java.util.List;

@Singleton
public class AddressRepository {

    @Inject
    ReactiveMongoClient mongoClient;

    private static final String DB_NAME = "collections";
    private static final String COLLECTION_NAME = "addresses";

    private ReactiveMongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB_NAME).getCollection(COLLECTION_NAME, Document.class);
    }

    public Uni<Void> save(Address address) {
        Document doc = toDocument(address);
        return getCollection()
                .insertOne(doc)
                .replaceWithVoid();
    }

    public Uni<Address> findById(String id) {
        return getCollection()
                .find(Filters.eq("_id", id))
                .collect().first()
                .onItem().ifNotNull()
                .transform(this::fromDocument);
    }

    public Uni<Void> upsert(Address address) {
        Document doc = toDocument(address);
        return getCollection()
                .replaceOne(Filters.eq("_id", address.getId()), doc,
                    new com.mongodb.client.model.ReplaceOptions().upsert(true))
                .replaceWithVoid();
    }

    private Document toDocument(Address address) {
        return new Document()
                .append("_id", address.getId())
                .append("street", address.getStreet())
                .append("city", address.getCity())
                .append("zipCode", address.getZipCode())
                .append("latitude", address.getLatitude())
                .append("longitude", address.getLongitude());
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

