package org.example.infrastructure.repository;

import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.example.domain.entity.Material;
import com.mongodb.client.model.Filters;

import java.util.List;

@Singleton
public class MaterialRepository {

    @Inject
    ReactiveMongoClient mongoClient;

    private static final String DB_NAME = "collections";
    private static final String COLLECTION_NAME = "materials";

    private ReactiveMongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB_NAME).getCollection(COLLECTION_NAME, Document.class);
    }

    public Uni<Void> save(Material material) {
        Document doc = toDocument(material);
        return getCollection()
                .insertOne(doc)
                .replaceWithVoid();
    }

    public Uni<Material> findById(String id) {
        return getCollection()
                .find(Filters.eq("_id", id))
                .collect().first()
                .onItem().ifNotNull()
                .transform(this::fromDocument);
    }

    public Uni<List<Material>> findAll() {
        return getCollection()
                .find()
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    public Uni<Void> upsert(Material material) {
        Document doc = toDocument(material);
        return getCollection()
                .replaceOne(Filters.eq("_id", material.getId()), doc,
                    new com.mongodb.client.model.ReplaceOptions().upsert(true))
                .replaceWithVoid();
    }

    private Document toDocument(Material material) {
        return new Document()
                .append("_id", material.getId())
                .append("name", material.getName())
                .append("category", material.getCategory());
    }

    private Material fromDocument(Document doc) {
        return new Material(
                doc.getString("_id"),
                doc.getString("name"),
                doc.getString("category")
        );
    }
}

