package org.example.infrastructure.repository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.example.domain.entity.Address;

@Singleton
public class AddressRepository {

    private static final String DB_NAME = "collections";
    private static final String COLLECTION_NAME = "addresses";

    @Inject
    ReactiveMongoClient mongoClient;

    private ReactiveMongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB_NAME).getCollection(COLLECTION_NAME, Document.class);
    }

    public Uni<Void> save(Address address) {
        return getCollection()
                .insertOne(toDocument(address))
                .replaceWithVoid();
    }

    public Uni<Address> findById(String id) {
        return getCollection()
                .find(Filters.eq("_id", id))
                .collect().first()
                .onItem().ifNotNull()
                .transform(this::fromDocument);
    }

    public Uni<Address> findDuplicate(Address address) {
        Bson filter = Filters.and(
                Filters.eq("zipCode", normalize(address.getZipCode())),
                Filters.eq("street", normalize(address.getStreet())),
                Filters.eq("number", normalize(address.getNumber())),
                Filters.eq("city", normalize(address.getCity())),
                Filters.eq("state", normalize(address.getState()))
        );

        return getCollection()
                .find(filter)
                .collect().first()
                .onItem().ifNotNull()
                .transform(this::fromDocument);
    }

    public Uni<Void> upsert(Address address) {
        return getCollection()
                .replaceOne(
                        Filters.eq("_id", address.getId()),
                        toDocument(address),
                        new ReplaceOptions().upsert(true)
                )
                .replaceWithVoid();
    }

    private Document toDocument(Address address) {
        return new Document()
                .append("_id", address.getId())
                .append("street", normalize(address.getStreet()))
                .append("city", normalize(address.getCity()))
                .append("zipCode", normalize(address.getZipCode()))
                .append("number", normalize(address.getNumber()))
                .append("state", normalize(address.getState()))
                .append("latitude", address.getLatitude())
                .append("longitude", address.getLongitude())
                .append("enrichmentStatus", address.getEnrichmentStatus())
                .append("enrichmentSource", address.getEnrichmentSource());
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
                doc.getString("enrichmentStatus"),
                doc.getString("enrichmentSource")
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
