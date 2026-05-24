package org.example.presentation.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.example.application.route.RouteModels.*;
import org.example.application.route.SavedRouteModels.MoveRouteRequestCommand;
import org.example.application.route.SavedRouteModels.SaveRouteSuggestionCommand;
import org.example.application.usecase.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Path("/collectors/routes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CollectorRouteResource {
    private static final Logger LOG = LoggerFactory.getLogger(CollectorRouteResource.class);

    @Inject
    RouteOptimizationUseCase routeOptimizationUseCase;

    @Inject
    SaveRouteSuggestionUseCase saveRouteSuggestionUseCase;

    @Inject
    ListSavedRoutesUseCase listSavedRoutesUseCase;

    @Inject
    DeleteSavedRouteSuggestionUseCase deleteSavedRouteSuggestionUseCase;

    @Inject
    MoveRouteRequestUseCase moveRouteRequestUseCase;

    @POST
    @Path("/suggest")
    public Uni<Response> suggestRoutes(RouteOptimizationRequestDTO dto) {
        LOG.info("POST /collectors/routes/suggest");
        RouteOptimizationCommand command;
        try {
            command = toCommand(dto);
        } catch (RuntimeException ex) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build());
        }
        return routeOptimizationUseCase.suggestRoutes(command)
                .onItem().transform(result -> Response.ok(result).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error suggesting routes", ex);
                    return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
                });
    }

    @POST
    @Path("/save")
    public Uni<Response> saveRoute(SaveRouteRequestDTO dto) {
        LOG.info("POST /collectors/routes/save");
        SaveRouteSuggestionCommand command;
        try {
            command = toSaveCommand(dto);
        } catch (RuntimeException ex) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build());
        }
        return saveRouteSuggestionUseCase.save(command)
                .onItem().transform(result -> Response.status(Response.Status.CREATED).entity(result).build())
                .onFailure(DuplicateSavedRouteException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.CONFLICT).entity(ex.getMessage()).build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error saving route", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    @GET
    @Path("/saved")
    public Uni<Response> listSavedRoutes() {
        LOG.info("GET /collectors/routes/saved");
        return listSavedRoutesUseCase.list()
                .onItem().transform(results -> Response.ok(results).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error listing saved routes", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    @POST
    @Path("/saved/{savedRouteId}/move-request")
    public Uni<Response> moveRouteRequest(@PathParam("savedRouteId") String savedRouteId, MoveRouteRequestDTO dto) {
        LOG.info("POST /collectors/routes/saved/{}/move-request", savedRouteId);
        MoveRouteRequestCommand command;
        try {
            command = toMoveCommand(savedRouteId, dto);
        } catch (RuntimeException ex) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build());
        }
        return moveRouteRequestUseCase.move(command)
                .onItem().transform(result -> Response.ok(result).build())
                .onFailure(SavedRouteSuggestionNotFoundException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.NOT_FOUND).entity(ex.getMessage()).build())
                .onFailure(RouteMoveValidationException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure(DuplicateSavedRouteException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.CONFLICT).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error moving route request", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    @DELETE
    @Path("/saved/{savedRouteId}")
    public Uni<Response> deleteSavedRoute(@PathParam("savedRouteId") String savedRouteId) {
        LOG.info("DELETE /collectors/routes/saved/{}", savedRouteId);
        return deleteSavedRouteSuggestionUseCase.delete(savedRouteId)
                .onItem().transform(it -> Response.noContent().build())
                .onFailure(SavedRouteSuggestionNotFoundException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.NOT_FOUND).entity(ex.getMessage()).build())
                .onFailure(IllegalArgumentException.class).recoverWithItem(ex ->
                        Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build())
                .onFailure().recoverWithItem(ex -> {
                    LOG.error("Error deleting saved route", ex);
                    return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
                });
    }

    private SaveRouteSuggestionCommand toSaveCommand(SaveRouteRequestDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("save route request is required");
        }
        return new SaveRouteSuggestionCommand(dto.getCollectorId(), dto.getSource(), dto.getSuggestion());
    }

    private MoveRouteRequestCommand toMoveCommand(String savedRouteId, MoveRouteRequestDTO dto) {
        if (dto == null) {
            throw new RouteMoveValidationException("move route request is required");
        }
        return new MoveRouteRequestCommand(
                savedRouteId,
                dto.getCollectionRequestId(),
                dto.getSourceVehicleIndex(),
                dto.getTargetVehicleIndex()
        );
    }

    private RouteOptimizationCommand toCommand(RouteOptimizationRequestDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Route optimization request is required");
        }
        return new RouteOptimizationCommand(
                dto.getCollectorId(),
                toVehicles(dto.getVehicles()),
                toStartLocation(dto.getStart()),
                dto.getEndAtStart() == null || dto.getEndAtStart(),
                dto.getCandidateRequestIds(),
                dto.getFilters() == null ? null : new RouteFilters(
                        dto.getFilters().getMaterialIds(),
                        dto.getFilters().getMaxDistanceKmFromStart(),
                        dto.getFilters().getOnlyInProgress()
                ),
                dto.getOptions() == null ? null : new RouteOptions(
                        dto.getOptions().getTimeLimitSeconds(),
                        dto.getOptions().getAllowDroppingStops(),
                        dto.getOptions().getDropPenalty()
                )
        );
    }

    private List<RouteVehicle> toVehicles(List<RouteVehicleDTO> vehicles) {
        if (vehicles == null) {
            return null;
        }
        return java.util.stream.IntStream.range(0, vehicles.size())
                .mapToObj(index -> {
                    RouteVehicleDTO vehicle = vehicles.get(index);
                    if (vehicle == null || vehicle.getCapacity() == null) {
                        throw new IllegalArgumentException("vehicle capacity is required");
                    }
                    return new RouteVehicle(index, vehicle.getCapacity());
                })
                .toList();
    }

    private StartLocation toStartLocation(StartLocationDTO dto) {
        if (dto == null) {
            return null;
        }
        StartLocationType type = dto.getType() == null ? null : StartLocationType.valueOf(dto.getType());
        return new StartLocation(type, dto.getAddressId(), dto.getLatitude(), dto.getLongitude());
    }

    public static class SaveRouteRequestDTO {
        private String collectorId;
        private String source;
        private RouteOptimizationResult suggestion;

        public String getCollectorId() {
            return collectorId;
        }

        public void setCollectorId(String collectorId) {
            this.collectorId = collectorId;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public RouteOptimizationResult getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(RouteOptimizationResult suggestion) {
            this.suggestion = suggestion;
        }
    }

    public static class MoveRouteRequestDTO {
        private String collectionRequestId;
        private Integer sourceVehicleIndex;
        private Integer targetVehicleIndex;

        public String getCollectionRequestId() {
            return collectionRequestId;
        }

        public void setCollectionRequestId(String collectionRequestId) {
            this.collectionRequestId = collectionRequestId;
        }

        public Integer getSourceVehicleIndex() {
            return sourceVehicleIndex;
        }

        public void setSourceVehicleIndex(Integer sourceVehicleIndex) {
            this.sourceVehicleIndex = sourceVehicleIndex;
        }

        public Integer getTargetVehicleIndex() {
            return targetVehicleIndex;
        }

        public void setTargetVehicleIndex(Integer targetVehicleIndex) {
            this.targetVehicleIndex = targetVehicleIndex;
        }
    }

    public static class RouteVehicleDTO {
        private Double capacity;

        public Double getCapacity() {
            return capacity;
        }

        public void setCapacity(Double capacity) {
            this.capacity = capacity;
        }
    }

    public static class RouteOptimizationRequestDTO {
        private String collectorId;
        private List<RouteVehicleDTO> vehicles;
        private StartLocationDTO start;
        private Boolean endAtStart;
        private List<String> candidateRequestIds;
        private RouteFiltersDTO filters;
        private RouteOptionsDTO options;

        public String getCollectorId() {
            return collectorId;
        }

        public void setCollectorId(String collectorId) {
            this.collectorId = collectorId;
        }

        public List<RouteVehicleDTO> getVehicles() {
            return vehicles;
        }

        public void setVehicles(List<RouteVehicleDTO> vehicles) {
            this.vehicles = vehicles;
        }

        public StartLocationDTO getStart() {
            return start;
        }

        public void setStart(StartLocationDTO start) {
            this.start = start;
        }

        public Boolean getEndAtStart() {
            return endAtStart;
        }

        public void setEndAtStart(Boolean endAtStart) {
            this.endAtStart = endAtStart;
        }

        public List<String> getCandidateRequestIds() {
            return candidateRequestIds;
        }

        public void setCandidateRequestIds(List<String> candidateRequestIds) {
            this.candidateRequestIds = candidateRequestIds;
        }

        public RouteFiltersDTO getFilters() {
            return filters;
        }

        public void setFilters(RouteFiltersDTO filters) {
            this.filters = filters;
        }

        public RouteOptionsDTO getOptions() {
            return options;
        }

        public void setOptions(RouteOptionsDTO options) {
            this.options = options;
        }
    }

    public static class StartLocationDTO {
        private String type;
        private String addressId;
        private Double latitude;
        private Double longitude;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getAddressId() {
            return addressId;
        }

        public void setAddressId(String addressId) {
            this.addressId = addressId;
        }

        public Double getLatitude() {
            return latitude;
        }

        public void setLatitude(Double latitude) {
            this.latitude = latitude;
        }

        public Double getLongitude() {
            return longitude;
        }

        public void setLongitude(Double longitude) {
            this.longitude = longitude;
        }
    }

    public static class RouteFiltersDTO {
        private List<String> materialIds;
        private Double maxDistanceKmFromStart;
        private Boolean onlyInProgress;

        public List<String> getMaterialIds() {
            return materialIds;
        }

        public void setMaterialIds(List<String> materialIds) {
            this.materialIds = materialIds;
        }

        public Double getMaxDistanceKmFromStart() {
            return maxDistanceKmFromStart;
        }

        public void setMaxDistanceKmFromStart(Double maxDistanceKmFromStart) {
            this.maxDistanceKmFromStart = maxDistanceKmFromStart;
        }

        public Boolean getOnlyInProgress() {
            return onlyInProgress;
        }

        public void setOnlyInProgress(Boolean onlyInProgress) {
            this.onlyInProgress = onlyInProgress;
        }
    }

    public static class RouteOptionsDTO {
        private Integer timeLimitSeconds;
        private Boolean allowDroppingStops;
        private Long dropPenalty;

        public Integer getTimeLimitSeconds() {
            return timeLimitSeconds;
        }

        public void setTimeLimitSeconds(Integer timeLimitSeconds) {
            this.timeLimitSeconds = timeLimitSeconds;
        }

        public Boolean getAllowDroppingStops() {
            return allowDroppingStops;
        }

        public void setAllowDroppingStops(Boolean allowDroppingStops) {
            this.allowDroppingStops = allowDroppingStops;
        }

        public Long getDropPenalty() {
            return dropPenalty;
        }

        public void setDropPenalty(Long dropPenalty) {
            this.dropPenalty = dropPenalty;
        }
    }
}
