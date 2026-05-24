package org.example.application.usecase;

import jakarta.inject.Singleton;
import org.example.application.route.RouteModels.RouteOptimizationResult;
import org.example.application.route.RouteModels.RoutePlan;
import org.example.application.route.RouteModels.RouteStop;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class SavedRouteFingerprintService {

    public List<RouteStopRef> assignedStopRefs(RouteOptimizationResult suggestion) {
        List<RouteStopRef> refs = new ArrayList<>();
        if (suggestion == null || suggestion.routes() == null) {
            return refs;
        }
        for (RoutePlan route : suggestion.routes()) {
            if (route.stops() == null) {
                continue;
            }
            for (RouteStop stop : route.stops()) {
                if (stop.collectionRequestId() == null || stop.collectionRequestId().isBlank()) {
                    throw new IllegalArgumentException("route stop collectionRequestId is required");
                }
                refs.add(new RouteStopRef(route.vehicleIndex(), stop.sequence(), stop.collectionRequestId().trim()));
            }
        }
        refs.sort(Comparator.comparingInt(RouteStopRef::vehicleIndex).thenComparingInt(RouteStopRef::sequence));
        return refs;
    }

    public List<String> distinctAssignedIds(RouteOptimizationResult suggestion) {
        return distinctAssignedIds(assignedStopRefs(suggestion));
    }

    public List<String> distinctAssignedIds(List<RouteStopRef> stopRefs) {
        Set<String> ids = new LinkedHashSet<>();
        for (RouteStopRef stopRef : stopRefs) {
            ids.add(stopRef.collectionRequestId());
        }
        return List.copyOf(ids);
    }

    public String fingerprint(String collectorId, RouteOptimizationResult suggestion) {
        return fingerprint(collectorId, assignedStopRefs(suggestion));
    }

    public String fingerprint(String collectorId, List<RouteStopRef> stopRefs) {
        String input = collectorId + "|" + stopRefs.stream()
                .map(ref -> ref.vehicleIndex() + ":" + ref.sequence() + ":" + ref.collectionRequestId())
                .collect(Collectors.joining("|"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record RouteStopRef(int vehicleIndex, int sequence, String collectionRequestId) {
    }
}
