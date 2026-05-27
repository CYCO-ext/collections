package org.example.application.usecase;

import jakarta.inject.Singleton;
import org.example.application.route.RouteMapModels.RouteCoordinate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Singleton
public class RouteMapFingerprintService {

    public String fingerprint(String savedRouteId, int vehicleIndex, List<RouteCoordinate> coordinates) {
        StringBuilder normalized = new StringBuilder();
        normalized.append(trim(savedRouteId)).append('|').append(vehicleIndex);
        coordinates.stream()
                .sorted(Comparator.comparingInt(coordinates::indexOf))
                .forEach(coordinate -> normalized
                        .append('|')
                        .append(trim(coordinate.collectionRequestId()))
                        .append(':')
                        .append(format(coordinate.longitude()))
                        .append(',')
                        .append(format(coordinate.latitude())));
        return "sha256:" + sha256(normalized.toString());
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
