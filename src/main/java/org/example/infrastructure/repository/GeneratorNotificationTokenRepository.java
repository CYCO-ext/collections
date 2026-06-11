package org.example.infrastructure.repository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.example.domain.entity.GeneratorNotificationToken;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Singleton
public class GeneratorNotificationTokenRepository {

    private static final String DB_NAME = "collections";
    private static final String COLLECTION_NAME = "generator_notification_tokens";

    @Inject
    ReactiveMongoClient mongoClient;

    private ReactiveMongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB_NAME).getCollection(COLLECTION_NAME, Document.class);
    }

    public Uni<Void> saveOrUpdate(GeneratorNotificationToken token) {
        Document update = new Document("$set", new Document()
                .append("generatorId", token.getGeneratorId())
                .append("token", token.getToken())
                .append("platform", token.getPlatform())
                .append("enabled", token.isEnabled())
                .append("updatedAt", toDate(token.getUpdatedAt())))
                .append("$setOnInsert", new Document()
                        .append("_id", token.getId())
                        .append("createdAt", toDate(token.getCreatedAt())));

        return getCollection()
                .updateOne(Filters.eq("token", token.getToken()), update, new UpdateOptions().upsert(true))
                .replaceWithVoid();
    }

    public Uni<List<GeneratorNotificationToken>> findEnabledByGeneratorId(String generatorId) {
        return getCollection()
                .find(Filters.and(Filters.eq("generatorId", generatorId), Filters.eq("enabled", true)))
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    private GeneratorNotificationToken fromDocument(Document doc) {
        GeneratorNotificationToken token = new GeneratorNotificationToken();
        token.setId(doc.getString("_id"));
        token.setGeneratorId(doc.getString("generatorId"));
        token.setToken(doc.getString("token"));
        token.setPlatform(doc.getString("platform"));
        token.setEnabled(Boolean.TRUE.equals(doc.getBoolean("enabled")));
        token.setCreatedAt(toLocalDateTime(doc.getDate("createdAt")));
        token.setUpdatedAt(toLocalDateTime(doc.getDate("updatedAt")));
        return token;
    }

    private Date toDate(LocalDateTime value) {
        return value == null ? null : Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    private LocalDateTime toLocalDateTime(Date value) {
        return value == null ? null : value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
