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
import org.example.application.usecase.ConcurrentCollectionRequestModificationException;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.CompletionCodeState;
import org.example.domain.entity.CompletionEvidence;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Arrays;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;

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
        long expectedRevision = request.getRevision();
        Document doc = toDocument(request);
        doc.put("revision", expectedRevision + 1);
        Document filter = new Document("_id", request.getId());
        if (expectedRevision == 0) {
            filter.append("$or", List.of(
                    new Document("revision", 0),
                    new Document("revision", new Document("$exists", false))));
        } else {
            filter.append("revision", expectedRevision);
        }
        return getCollection()
                .replaceOne(filter, doc)
                .flatMap(result -> {
                    if (result.getMatchedCount() != 1) {
                        return Uni.createFrom().failure(new ConcurrentCollectionRequestModificationException(request.getId()));
                    }
                    request.setRevision(expectedRevision + 1);
                    return Uni.createFrom().voidItem();
                });
    }

    public Uni<Boolean> issueCompletionCodeIfAbsent(String requestId, CompletionCodeState code) {
        Document filter = new Document("_id", requestId)
                .append("status", new Document("$in", List.of("IN_PROGRESS", "ON_THE_WAY")))
                .append("completionCode", new Document("$exists", false));
        Document update = new Document("$set", new Document("completionCode", toDocument(code)))
                .append("$inc", new Document("revision", 1));
        return getCollection().updateOne(filter, update)
                .map(result -> result.getModifiedCount() == 1);
    }

    public Uni<Boolean> incrementCompletionCodeAttempts(
            String requestId,
            int codeVersion,
            LocalDateTime now,
            LocalDateTime blockedUntil,
            int maxAttempts
    ) {
        Date nowDate = toDate(now);
        Date blockedUntilDate = toDate(blockedUntil);
        Document currentAttempts = new Document("$ifNull", List.of("$completionCode.failedAttempts", 0));
        Document attemptsIfRestarted = new Document("$cond", List.of(
                new Document("$and", List.of(
                        new Document("$ne", Arrays.asList(new Document("$ifNull", Arrays.asList("$completionCode.blockedUntil", null)), null)),
                        new Document("$lte", List.of("$completionCode.blockedUntil", nowDate)))),
                1,
                new Document("$add", List.of(currentAttempts, 1))));
        Document nextBlockedUntil = new Document("$cond", Arrays.asList(
                new Document("$gte", List.of(attemptsIfRestarted, maxAttempts)),
                blockedUntilDate,
                null));
        Document filter = new Document("_id", requestId)
                .append("status", new Document("$in", List.of("IN_PROGRESS", "ON_THE_WAY")))
                .append("completionCode.version", codeVersion)
                .append("completionCode.consumedAt", new Document("$exists", false))
                .append("$or", List.of(
                        new Document("completionCode.blockedUntil", new Document("$exists", false)),
                        new Document("completionCode.blockedUntil", null),
                        new Document("completionCode.blockedUntil", new Document("$lte", nowDate))));
        Document set = new Document("completionCode.failedAttempts", attemptsIfRestarted)
                .append("completionCode.blockedUntil", nextBlockedUntil)
                .append("updatedAt", nowDate)
                .append("revision", new Document("$add", List.of(new Document("$ifNull", List.of("$revision", 0)), 1)));
        List<Bson> pipeline = List.of(new Document("$set", set));
        return getCollection().updateOne(filter, pipeline)
                .map(result -> result.getModifiedCount() == 1);
    }

    public Uni<Boolean> completeWithCode(String requestId, int codeVersion, CompletionEvidence evidence) {
        Document filter = new Document("_id", requestId)
                .append("status", new Document("$in", List.of("IN_PROGRESS", "ON_THE_WAY")))
                .append("completionCode.version", codeVersion)
                .append("completionCode.consumedAt", new Document("$exists", false));
        Date now = toDate(evidence.validatedAt());
        Document completion = toDocument(evidence)
                .append("pendingEffects", List.of("ROUTE_PROGRESS", "ROUTE_CLOSE", "COLLECTION_EVENT", "NOTIFICATION"));
        Document update = new Document("$set", new Document("status", "COMPLETED")
                .append("collectorConfirmed", true)
                .append("completionCode.consumedAt", now)
                .append("completion", completion)
                .append("updatedAt", now))
                .append("$inc", new Document("revision", 1));
        return getCollection().updateOne(filter, update)
                .map(result -> result.getModifiedCount() == 1);
    }

    public Uni<List<CollectionRequest>> findPendingCompletionEffects(int limit) {
        if (limit <= 0) return Uni.createFrom().item(List.of());
        Document filter = new Document("status", "COMPLETED")
                .append("completion.pendingEffects", new Document("$exists", true).append("$ne", List.of()));
        return getCollection()
                .find(filter, new FindOptions().limit(limit).sort(Sorts.ascending("completion.validatedAt")))
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    public Uni<Boolean> markCompletionEffectProcessed(String requestId, String completionId, String effect) {
        Document filter = new Document("_id", requestId)
                .append("status", "COMPLETED")
                .append("completion.completionId", completionId)
                .append("completion.pendingEffects", effect);
        Document update = new Document("$pull", new Document("completion.pendingEffects", effect))
                .append("$inc", new Document("revision", 1));
        return getCollection().updateOne(filter, update)
                .map(result -> result.getModifiedCount() == 1);
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
        Document document = new Document()
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
                .append("updatedAt", request.getUpdatedAt())
                .append("revision", 0);
        if (request.getCompletionCode() != null) document.append("completionCode", toDocument(request.getCompletionCode()));
        if (request.getCompletionEvidence() != null) document.append("completion", toDocument(request.getCompletionEvidence()));
        return document;
    }

    private CollectionRequest fromDocument(Document doc) {
        CollectionRequest request = new CollectionRequest();
        request.setId(doc.getString("_id"));
        request.setGeneratorId(doc.getString("generatorId"));
        request.setAddressId(doc.getString("addressId"));
        request.setMaterialIds(doc.getList("materialIds", String.class));
        request.setWeight(MongoDocumentValues.asDouble(doc, "weight"));
        request.setStatus(CollectionRequest.Status.valueOf(doc.getString("status")));
        request.setSelectedCollectorId(doc.getString("selectedCollectorId"));
        request.setGeneratorConfirmed(doc.getBoolean("generatorConfirmed"));
        request.setCollectorConfirmed(doc.getBoolean("collectorConfirmed"));
        Object revision = doc.get("revision");
        request.setRevision(revision instanceof Number number ? number.longValue() : 0L);
        Document code = doc.get("completionCode", Document.class);
        if (code != null) request.setCompletionCode(fromCompletionCode(code));
        Document completion = doc.get("completion", Document.class);
        if (completion != null) request.setCompletionEvidence(fromCompletionEvidence(completion));
        request.setCreatedAt(toLocalDateTime(doc.get("createdAt")));
        request.setUpdatedAt(toLocalDateTime(doc.get("updatedAt")));
        return request;
    }

    private Document toDocument(CompletionCodeState code) {
        Document document = new Document("version", code.version())
                .append("ciphertext", code.ciphertext())
                .append("nonce", code.nonce())
                .append("keyId", code.keyId())
                .append("verifier", code.verifier())
                .append("issuedAt", toDate(code.issuedAt()))
                .append("failedAttempts", code.failedAttempts());
        if (code.blockedUntil() != null) document.append("blockedUntil", toDate(code.blockedUntil()));
        if (code.consumedAt() != null) document.append("consumedAt", toDate(code.consumedAt()));
        return document;
    }

    private CompletionCodeState fromCompletionCode(Document code) {
        return new CompletionCodeState(
                code.getInteger("version"),
                code.getString("ciphertext"),
                code.getString("nonce"),
                code.getString("keyId"),
                code.getString("verifier"),
                toLocalDateTime(code.getDate("issuedAt")),
                code.getInteger("failedAttempts", 0),
                toLocalDateTime(code.getDate("blockedUntil")),
                toLocalDateTime(code.getDate("consumedAt")));
    }

    private Document toDocument(CompletionEvidence evidence) {
        return new Document("completionId", evidence.completionId())
                .append("actorUserId", evidence.actorUserId())
                .append("actorRole", evidence.actorRole())
                .append("actorProfileId", evidence.actorProfileId())
                .append("validatedAt", toDate(evidence.validatedAt()))
                .append("codeVersion", evidence.codeVersion())
                .append("idempotencyKey", evidence.idempotencyKey())
                .append("payloadDigest", evidence.payloadDigest())
                .append("pendingEffects", evidence.pendingEffects());
    }

    private CompletionEvidence fromCompletionEvidence(Document evidence) {
        return new CompletionEvidence(
                evidence.getString("completionId"),
                evidence.getString("actorUserId"),
                evidence.getString("actorRole"),
                evidence.getString("actorProfileId"),
                toLocalDateTime(evidence.getDate("validatedAt")),
                evidence.getInteger("codeVersion"),
                evidence.getString("idempotencyKey"),
                evidence.getString("payloadDigest"),
                evidence.getList("pendingEffects", String.class, List.of()));
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    private LocalDateTime toLocalDateTime(Date value) {
        return value == null ? null : value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Date date) {
            return toLocalDateTime(date);
        }
        if (value instanceof String text) {
            try {
                return Instant.parse(text).atZone(ZoneId.systemDefault()).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                return LocalDateTime.parse(text);
            }
        }
        return null;
    }
}

