package org.example.application.port.out;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.mutiny.Uni;
import org.example.application.route.RouteMapModels.RouteCoordinate;

import java.util.List;

public interface OpenRouteServiceDirectionsPort {
    Uni<JsonNode> fetchDrivingCarGeoJson(List<RouteCoordinate> coordinates);
}
