package org.example.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.example.application.route.RouteMapModels.RouteMap;
import org.example.application.route.RouteMapModels.RouteMapProvider;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Singleton
public class RouteMapRepository {

    private static final String DB_NAME = "collections";
    private static final String COLLECTION_NAME = "route_maps";
    private static final String PROFILE = "driving-car";
    private static final RouteMapProvider PROVIDER = RouteMapProvider.OPEN_ROUTE_SERVICE;

    @Inject
    ReactiveMongoClient mongoClient;

    @Inject
    ObjectMapper objectMapper;

    private ReactiveMongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB_NAME).getCollection(COLLECTION_NAME, Document.class);
    }

    public Uni<RouteMap> findBySavedRouteIdAndVehicleIndex(String savedRouteId, int vehicleIndex) {
        return getCollection()
                .find(Filters.and(
                        Filters.eq("savedRouteId", savedRouteId),
                        Filters.eq("vehicleIndex", vehicleIndex),
                        Filters.eq("provider", PROVIDER.toString()),
                        Filters.eq("profile", PROFILE)
                ))
                .collect().first()
                .onItem().ifNotNull().transform(this::fromDocument);
    }

    public Uni<List<RouteMap>> findBySavedRouteId(String savedRouteId) {
        return getCollection()
                .find(Filters.and(
                        Filters.eq("savedRouteId", savedRouteId),
                        Filters.eq("provider", PROVIDER.toString()),
                        Filters.eq("profile", PROFILE)
                ))
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    public Uni<Void> upsert(RouteMap map) {
        return getCollection()
                .updateOne(
                        Filters.and(
                                Filters.eq("savedRouteId", map.savedRouteId()),
                                Filters.eq("vehicleIndex", map.vehicleIndex()),
                                Filters.eq("provider", map.provider().toString()),
                                Filters.eq("profile", map.profile())
                        ),
                        Updates.combine(
                                Updates.setOnInsert("_id", map.id()),
                                Updates.setOnInsert("createdAt", toDate(map.createdAt())),
                                Updates.set("savedRouteId", map.savedRouteId()),
                                Updates.set("vehicleIndex", map.vehicleIndex()),
                                Updates.set("provider", map.provider().toString()),
                                Updates.set("profile", map.profile()),
                                Updates.set("fingerprint", map.fingerprint()),
                                Updates.set("geoJson", toDocument(map.geoJson())),
                                Updates.set("updatedAt", toDate(map.updatedAt()))
                        ),
                        new UpdateOptions().upsert(true)
                )
                .replaceWithVoid();
    }

    private RouteMap fromDocument(Document doc) {
        return new RouteMap(
                doc.getString("_id"),
                doc.getString("savedRouteId"),
                number(doc.get("vehicleIndex")).intValue(),
                RouteMapProvider.valueOf(doc.getString("provider")),
                doc.getString("profile"),
                doc.getString("fingerprint"),
                toJsonNode(doc.get("geoJson", Document.class)),
                toLocalDateTime(doc.getDate("createdAt")),
                toLocalDateTime(doc.getDate("updatedAt"))
        );
    }

    private Document toDocument(JsonNode jsonNode) {
        try {
            return Document.parse(objectMapper.writeValueAsString(jsonNode));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid route map GeoJSON", ex);
        }
    }

    private JsonNode toJsonNode(Document document) {
        if (document == null) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(document.toJson());
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid stored route map GeoJSON", ex);
        }
    }

    private Date toDate(LocalDateTime value) {
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }

    private LocalDateTime toLocalDateTime(Date value) {
        return value == null ? null : value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private Number number(Object value) {
        return value instanceof Number number ? number : 0;
    }
}
