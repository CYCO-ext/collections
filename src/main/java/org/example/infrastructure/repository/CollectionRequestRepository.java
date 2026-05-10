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
import org.bson.conversions.Bson;
import org.example.application.usecase.SearchCollectionsUseCase.CollectionSearchQuery;
import org.example.domain.entity.CollectionRequest;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class CollectionRequestRepository {

    @Inject
    ReactiveMongoClient mongoClient;

    private static final String DB_NAME = "collections";
    private static final String COLLECTION_NAME = "collection_requests";

    private ReactiveMongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB_NAME).getCollection(COLLECTION_NAME, Document.class);
    }

    public Uni<Void> save(CollectionRequest request) {
        Document doc = toDocument(request);
        return getCollection()
                .insertOne(doc)
                .replaceWithVoid();
    }

    public Uni<Void> update(CollectionRequest request) {
        Document doc = toDocument(request);
        return getCollection()
                .replaceOne(Filters.eq("_id", request.getId()), doc)
                .replaceWithVoid();
    }

    public Uni<CollectionRequest> findById(String id) {
        return getCollection()
                .find(Filters.eq("_id", id))
                .collect().first()
                .onItem().ifNotNull()
                .transform(this::fromDocument);
    }

    public Uni<List<CollectionRequest>> findByGeneratorId(String generatorId) {
        return getCollection()
                .find(Filters.eq("generatorId", generatorId))
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    public Uni<List<CollectionRequest>> findByStatus(String status) {
        return getCollection()
                .find(Filters.eq("status", status))
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    public Uni<List<CollectionRequest>> search(CollectionSearchQuery query) {
        FindOptions options = new FindOptions().sort(Sorts.descending("createdAt"));
        Bson filter = toSearchFilter(query);
        return getCollection()
                .find(filter, options)
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    private Bson toSearchFilter(CollectionSearchQuery query) {
        if (query == null) {
            return new Document();
        }
        List<Bson> filters = new ArrayList<>();
        if (query.status() != null) {
            filters.add(Filters.eq("status", query.status().toString()));
        }
        if (query.collectorId() != null) {
            filters.add(Filters.eq("selectedCollectorId", query.collectorId()));
        }
        if (query.generatorId() != null) {
            filters.add(Filters.eq("generatorId", query.generatorId()));
        }
        if (filters.isEmpty()) {
            return new Document();
        }
        return Filters.and(filters);
    }

    public Uni<List<CollectionRequest>> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }
        return getCollection()
                .find(Filters.in("_id", ids))
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    public Uni<List<CollectionRequest>> findInProgress(int limit) {
        return getCollection()
                .find(Filters.eq("status", CollectionRequest.Status.IN_PROGRESS.toString()))
                .collect().asList()
                .onItem().transform(docs -> docs.stream()
                        .limit(Math.max(limit, 0))
                        .map(this::fromDocument)
                        .toList());
    }

    public Uni<List<CollectionRequest>> findBySelectedCollectorId(String collectorId) {
        return getCollection()
                .find(Filters.eq("selectedCollectorId", collectorId))
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    private Document toDocument(CollectionRequest request) {
        return new Document()
                .append("_id", request.getId())
                .append("generatorId", request.getGeneratorId())
                .append("addressId", request.getAddressId())
                .append("materialIds", request.getMaterialIds())
                .append("weight", request.getWeight())
                .append("status", request.getStatus().toString())
                .append("selectedCollectorId", request.getSelectedCollectorId())
                .append("generatorConfirmed", request.getGeneratorConfirmed())
                .append("collectorConfirmed", request.getCollectorConfirmed())
                .append("createdAt", request.getCreatedAt())
                .append("updatedAt", request.getUpdatedAt());
    }

    private CollectionRequest fromDocument(Document doc) {
        CollectionRequest request = new CollectionRequest();
        request.setId(doc.getString("_id"));
        request.setGeneratorId(doc.getString("generatorId"));
        request.setAddressId(doc.getString("addressId"));
        request.setMaterialIds(doc.getList("materialIds", String.class));
        request.setWeight(doc.getDouble("weight"));
        request.setStatus(CollectionRequest.Status.valueOf(doc.getString("status")));
        request.setSelectedCollectorId(doc.getString("selectedCollectorId"));
        request.setGeneratorConfirmed(doc.getBoolean("generatorConfirmed"));
        request.setCollectorConfirmed(doc.getBoolean("collectorConfirmed"));
        request.setCreatedAt(doc.getDate("createdAt").toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        request.setUpdatedAt(doc.getDate("updatedAt").toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        return request;
    }
}

