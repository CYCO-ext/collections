package org.example.infrastructure.repository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import io.quarkus.mongodb.FindOptions;
import io.quarkus.mongodb.reactive.ReactiveMongoClient;
import io.quarkus.mongodb.reactive.ReactiveMongoCollection;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.example.application.route.RouteModels.RouteOptimizationResult;
import org.example.application.route.RouteModels.RoutePlan;
import org.example.application.route.RouteModels.RouteStop;
import org.example.application.route.RouteModels.SolverMetadata;
import org.example.application.route.RouteModels.SolverStatus;
import org.example.application.route.RouteModels.UnassignedReason;
import org.example.application.route.RouteModels.UnassignedRouteStop;
import org.example.application.route.SavedRouteModels.SavedRouteStatus;
import org.example.application.route.SavedRouteModels.SavedRouteSuggestion;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Singleton
public class SavedRouteRepository {

    private static final String DB_NAME = "collections";
    private static final String COLLECTION_NAME = "saved_routes";

    @Inject
    ReactiveMongoClient mongoClient;

    private ReactiveMongoCollection<Document> getCollection() {
        return mongoClient.getDatabase(DB_NAME).getCollection(COLLECTION_NAME, Document.class);
    }

    public Uni<Void> save(SavedRouteSuggestion route) {
        return getCollection()
                .insertOne(toDocument(route))
                .replaceWithVoid();
    }

    public Uni<SavedRouteSuggestion> findById(String savedRouteId) {
        return getCollection()
                .find(Filters.eq("_id", savedRouteId))
                .collect().first()
                .onItem().ifNotNull().transform(this::fromDocument);
    }

    public Uni<SavedRouteSuggestion> findByFingerprint(String fingerprint) {
        return getCollection()
                .find(Filters.eq("fingerprint", fingerprint))
                .collect().first()
                .onItem().ifNotNull().transform(this::fromDocument);
    }

    public Uni<SavedRouteSuggestion> findByFingerprintExcludingId(String fingerprint, String excludedSavedRouteId) {
        return getCollection()
                .find(Filters.and(
                        Filters.eq("fingerprint", fingerprint),
                        Filters.ne("_id", excludedSavedRouteId)
                ))
                .collect().first()
                .onItem().ifNotNull().transform(this::fromDocument);
    }

    public Uni<List<SavedRouteSuggestion>> findAllOrderByCreatedAtDesc() {
        return getCollection()
                .find(new Document(), new FindOptions().sort(Sorts.descending("createdAt")))
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    public Uni<List<SavedRouteSuggestion>> findOpenContainingCollectionRequest(String collectionRequestId) {
        return getCollection()
                .find(Filters.and(
                        Filters.eq("status", SavedRouteStatus.OPEN.toString()),
                        Filters.eq("assignedCollectionRequestIds", collectionRequestId)
                ))
                .collect().asList()
                .onItem().transform(docs -> docs.stream().map(this::fromDocument).toList());
    }

    public Uni<Void> update(SavedRouteSuggestion route) {
        return getCollection()
                .replaceOne(Filters.eq("_id", route.id()), toDocument(route))
                .replaceWithVoid();
    }

    public Uni<Void> close(String savedRouteId, LocalDateTime closedAt) {
        Date closedDate = toDate(closedAt);
        return getCollection()
                .updateOne(
                        Filters.eq("_id", savedRouteId),
                        Updates.combine(
                                Updates.set("status", SavedRouteStatus.CLOSED.toString()),
                                Updates.set("updatedAt", closedDate),
                                Updates.set("closedAt", closedDate)
                        )
                )
                .replaceWithVoid();
    }

    public Uni<Boolean> deleteById(String savedRouteId) {
        return getCollection()
                .deleteOne(Filters.eq("_id", savedRouteId))
                .onItem().transform(result -> result.getDeletedCount() > 0);
    }

    private Document toDocument(SavedRouteSuggestion route) {
        return new Document()
                .append("_id", route.id())
                .append("collectorId", route.collectorId())
                .append("status", route.status().toString())
                .append("fingerprint", route.fingerprint())
                .append("assignedCollectionRequestIds", route.assignedCollectionRequestIds())
                .append("suggestion", toSuggestionDocument(route.suggestion()))
                .append("createdAt", toDate(route.createdAt()))
                .append("updatedAt", toDate(route.updatedAt()))
                .append("closedAt", route.closedAt() == null ? null : toDate(route.closedAt()));
    }

    private SavedRouteSuggestion fromDocument(Document doc) {
        return new SavedRouteSuggestion(
                doc.getString("_id"),
                doc.getString("collectorId"),
                SavedRouteStatus.valueOf(doc.getString("status")),
                doc.getString("fingerprint"),
                doc.getList("assignedCollectionRequestIds", String.class),
                fromSuggestionDocument(doc.get("suggestion", Document.class)),
                toLocalDateTime(doc.getDate("createdAt")),
                toLocalDateTime(doc.getDate("updatedAt")),
                toLocalDateTime(doc.getDate("closedAt"))
        );
    }

    private Document toSuggestionDocument(RouteOptimizationResult suggestion) {
        return new Document()
                .append("status", suggestion.status().toString())
                .append("solver", toSolverDocument(suggestion.solver()))
                .append("routes", suggestion.routes() == null ? List.of() : suggestion.routes().stream().map(this::toRouteDocument).toList())
                .append("unassigned", suggestion.unassigned() == null ? List.of() : suggestion.unassigned().stream().map(this::toUnassignedDocument).toList());
    }

    private RouteOptimizationResult fromSuggestionDocument(Document doc) {
        if (doc == null) {
            return null;
        }
        return new RouteOptimizationResult(
                SolverStatus.valueOf(doc.getString("status")),
                fromSolverDocument(doc.get("solver", Document.class)),
                doc.getList("routes", Document.class, List.of()).stream().map(this::fromRouteDocument).toList(),
                doc.getList("unassigned", Document.class, List.of()).stream().map(this::fromUnassignedDocument).toList()
        );
    }

    private Document toSolverDocument(SolverMetadata solver) {
        if (solver == null) {
            return null;
        }
        return new Document()
                .append("engine", solver.engine())
                .append("elapsedMs", solver.elapsedMs())
                .append("objectiveDistanceMeters", solver.objectiveDistanceMeters())
                .append("droppedStops", solver.droppedStops());
    }

    private SolverMetadata fromSolverDocument(Document doc) {
        if (doc == null) {
            return null;
        }
        return new SolverMetadata(
                doc.getString("engine"),
                number(doc.get("elapsedMs")).longValue(),
                number(doc.get("objectiveDistanceMeters")).longValue(),
                number(doc.get("droppedStops")).intValue()
        );
    }

    private Document toRouteDocument(RoutePlan route) {
        return new Document()
                .append("vehicleIndex", route.vehicleIndex())
                .append("capacity", route.capacity())
                .append("totalLoad", route.totalLoad())
                .append("totalDistanceMeters", route.totalDistanceMeters())
                .append("stops", route.stops() == null ? List.of() : route.stops().stream().map(this::toStopDocument).toList());
    }

    private RoutePlan fromRouteDocument(Document doc) {
        return new RoutePlan(
                number(doc.get("vehicleIndex")).intValue(),
                number(doc.get("capacity")).doubleValue(),
                number(doc.get("totalLoad")).doubleValue(),
                number(doc.get("totalDistanceMeters")).longValue(),
                doc.getList("stops", Document.class, List.of()).stream().map(this::fromStopDocument).toList()
        );
    }

    private Document toStopDocument(RouteStop stop) {
        return new Document()
                .append("sequence", stop.sequence())
                .append("collectionRequestId", stop.collectionRequestId())
                .append("addressId", stop.addressId())
                .append("latitude", stop.latitude())
                .append("longitude", stop.longitude())
                .append("demand", stop.demand())
                .append("accumulatedLoad", stop.accumulatedLoad())
                .append("distanceFromPreviousMeters", stop.distanceFromPreviousMeters());
    }

    private RouteStop fromStopDocument(Document doc) {
        return new RouteStop(
                number(doc.get("sequence")).intValue(),
                doc.getString("collectionRequestId"),
                doc.getString("addressId"),
                number(doc.get("latitude")).doubleValue(),
                number(doc.get("longitude")).doubleValue(),
                number(doc.get("demand")).doubleValue(),
                number(doc.get("accumulatedLoad")).doubleValue(),
                number(doc.get("distanceFromPreviousMeters")).longValue()
        );
    }

    private Document toUnassignedDocument(UnassignedRouteStop stop) {
        return new Document()
                .append("collectionRequestId", stop.collectionRequestId())
                .append("reason", stop.reason().toString());
    }

    private UnassignedRouteStop fromUnassignedDocument(Document doc) {
        return new UnassignedRouteStop(
                doc.getString("collectionRequestId"),
                UnassignedReason.valueOf(doc.getString("reason"))
        );
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
