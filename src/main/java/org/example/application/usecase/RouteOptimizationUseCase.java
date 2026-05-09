package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.port.out.*;
import org.example.application.route.RouteModels.*;
import org.example.domain.entity.Address;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.Collector;

import java.util.*;

@Singleton
public class RouteOptimizationUseCase {
    private static final String SOLVER_ENGINE = "OR_TOOLS";

    @Inject
    CollectionRequestPort collectionRequestPort;

    @Inject
    CollectorDiscoveryPort collectorDiscoveryPort;

    @Inject
    AddressPort addressPort;

    @Inject
    DistanceMatrixPort distanceMatrixPort;

    @Inject
    RouteOptimizationPort routeOptimizationPort;

    @ConfigProperty(name = "routes.optimization.default-time-limit-seconds", defaultValue = "5")
    int defaultTimeLimitSeconds;

    @ConfigProperty(name = "routes.optimization.max-time-limit-seconds", defaultValue = "30")
    int maxTimeLimitSeconds;

    @ConfigProperty(name = "routes.optimization.default-drop-penalty", defaultValue = "100000")
    long defaultDropPenalty;

    @ConfigProperty(name = "routes.optimization.max-candidates", defaultValue = "100")
    int maxCandidates;

    public Uni<RouteOptimizationResult> suggestRoutes(RouteOptimizationCommand command) {
        validate(command);

        return collectorDiscoveryPort.findCollectorById(command.collectorId())
                .onItem().ifNull().failWith(() -> new IllegalArgumentException("Collector not found: " + command.collectorId()))
                .flatMap(collector -> resolveStart(command.start(), collector)
                        .flatMap(depot -> loadCandidates(command)
                                .flatMap(candidates -> buildAndSolve(command, collector, depot, candidates))));
    }

    private void validate(RouteOptimizationCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Route optimization request is required");
        }
        if (isBlank(command.collectorId())) {
            throw new IllegalArgumentException("collectorId is required");
        }
        if (command.vehicleCount() <= 0) {
            throw new IllegalArgumentException("vehicleCount must be greater than zero");
        }
        if ((command.vehicleCapacities() == null || command.vehicleCapacities().isEmpty()) && command.vehicleCapacity() == null) {
            throw new IllegalArgumentException("vehicleCapacity or vehicleCapacities is required");
        }
        if (command.vehicleCapacities() != null && !command.vehicleCapacities().isEmpty()
                && command.vehicleCapacities().size() != command.vehicleCount()) {
            throw new IllegalArgumentException("vehicleCapacities size must match vehicleCount");
        }
        for (Double capacity : vehicleCapacities(command)) {
            if (capacity == null || capacity <= 0) {
                throw new IllegalArgumentException("vehicle capacities must be greater than zero");
            }
        }
    }

    private Uni<RouteLocation> resolveStart(StartLocation start, Collector collector) {
        StartLocation effectiveStart = start == null
                ? new StartLocation(StartLocationType.COLLECTOR_ADDRESS, null, null, null)
                : start;

        StartLocationType type = effectiveStart.type() == null ? StartLocationType.COLLECTOR_ADDRESS : effectiveStart.type();
        return switch (type) {
            case COORDINATES -> {
                validateCoordinates(effectiveStart.latitude(), effectiveStart.longitude(), "start");
                yield Uni.createFrom().item(new RouteLocation("start", null, effectiveStart.latitude(), effectiveStart.longitude()));
            }
            case ADDRESS -> {
                if (isBlank(effectiveStart.addressId())) {
                    throw new IllegalArgumentException("start.addressId is required for ADDRESS start type");
                }
                yield addressPort.findById(effectiveStart.addressId())
                        .onItem().ifNull().failWith(() -> new IllegalArgumentException("Start address not found: " + effectiveStart.addressId()))
                        .onItem().transform(address -> toRouteLocation("start", address));
            }
            case COLLECTOR_ADDRESS -> {
                Address address = collector.getAddress();
                if (address == null && !isBlank(effectiveStart.addressId())) {
                    yield addressPort.findById(effectiveStart.addressId())
                            .onItem().ifNull().failWith(() -> new IllegalArgumentException("Start address not found: " + effectiveStart.addressId()))
                            .onItem().transform(found -> toRouteLocation("start", found));
                }
                if (address == null) {
                    throw new IllegalArgumentException("Collector has no start address");
                }
                yield Uni.createFrom().item(toRouteLocation("start", address));
            }
        };
    }

    private Uni<List<CollectionRequest>> loadCandidates(RouteOptimizationCommand command) {
        List<String> explicitIds = command.candidateRequestIdsOrEmpty();
        if (!explicitIds.isEmpty()) {
            return collectionRequestPort.findByIds(explicitIds);
        }
        return collectionRequestPort.findInProgress(maxCandidates);
    }

    private Uni<RouteOptimizationResult> buildAndSolve(
            RouteOptimizationCommand command,
            Collector collector,
            RouteLocation depot,
            List<CollectionRequest> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("At least one candidate collection request is required");
        }

        List<CollectionRequest> distinctCandidates = distinctById(candidates);
        List<Uni<Address>> addressLookups = distinctCandidates.stream()
                .map(request -> addressPort.findById(request.getAddressId()))
                .toList();

        return Uni.combine().all().unis(addressLookups).with(addresses -> {
            CandidateBuildResult buildResult = buildCandidates(command, collector, depot, distinctCandidates, addresses);
            if (buildResult.routableStops().isEmpty()) {
                return emptyResult(buildResult.unassigned());
            }

            List<RouteVehicle> vehicles = vehicles(command);
            long[][] matrix = distanceMatrixPort.buildMatrixMeters(depot, buildResult.routableStops());
            RouteOptions options = command.optionsOrDefault();
            RouteOptimizationProblem problem = new RouteOptimizationProblem(
                    depot,
                    vehicles,
                    buildResult.routableStops(),
                    matrix,
                    options,
                    boundedTimeLimit(options),
                    options.dropPenalty() == null ? defaultDropPenalty : options.dropPenalty()
            );

            RouteOptimizationResult solved = routeOptimizationPort.optimize(problem);
            return solved.withAdditionalUnassigned(buildResult.unassigned());
        });
    }

    private CandidateBuildResult buildCandidates(
            RouteOptimizationCommand command,
            Collector collector,
            RouteLocation depot,
            List<CollectionRequest> requests,
            List<?> addresses
    ) {
        List<RouteCandidateStop> routable = new ArrayList<>();
        List<UnassignedRouteStop> unassigned = new ArrayList<>();
        double maxCapacity = vehicleCapacities(command).stream().filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(0.0);

        for (int index = 0; index < requests.size(); index++) {
            CollectionRequest request = requests.get(index);
            Address address = (Address) addresses.get(index);
            UnassignedReason reason = ineligibilityReason(command, collector, request, address, maxCapacity);
            if (reason != null) {
                unassigned.add(new UnassignedRouteStop(request.getId(), reason));
                continue;
            }

            RouteLocation location = toRouteLocation(request.getId(), address);
            Double maxDistanceKm = command.filtersOrDefault().maxDistanceKmFromStart();
            if (maxDistanceKm != null && distanceKm(depot, location) > maxDistanceKm) {
                unassigned.add(new UnassignedRouteStop(request.getId(), UnassignedReason.INFEASIBLE));
                continue;
            }

            routable.add(new RouteCandidateStop(
                    request.getId(),
                    request.getAddressId(),
                    request.getMaterialIds(),
                    request.getWeight(),
                    location
            ));
        }
        return new CandidateBuildResult(routable, unassigned);
    }

    private UnassignedReason ineligibilityReason(
            RouteOptimizationCommand command,
            Collector collector,
            CollectionRequest request,
            Address address,
            double maxCapacity
    ) {
        if (!CollectionRequest.Status.IN_PROGRESS.equals(request.getStatus())) {
            return UnassignedReason.NOT_IN_PROGRESS;
        }
        if (!collector.acceptsAllMaterials(request.getMaterialIds())) {
            return UnassignedReason.MATERIAL_NOT_ACCEPTED;
        }
        List<String> filterMaterials = command.filtersOrDefault().materialIdsOrEmpty();
        if (!filterMaterials.isEmpty() && request.getMaterialIds().stream().noneMatch(filterMaterials::contains)) {
            return UnassignedReason.MATERIAL_NOT_ACCEPTED;
        }
        if (request.getWeight() == null || request.getWeight() <= 0) {
            return UnassignedReason.INVALID_DEMAND;
        }
        if (request.getWeight() > maxCapacity) {
            return UnassignedReason.OVER_CAPACITY;
        }
        if (address == null) {
            return UnassignedReason.MISSING_ADDRESS;
        }
        if (!hasCoordinates(address)) {
            return UnassignedReason.MISSING_COORDINATES;
        }
        return null;
    }

    private List<CollectionRequest> distinctById(List<CollectionRequest> candidates) {
        Set<String> seen = new HashSet<>();
        return candidates.stream()
                .filter(request -> request != null && request.getId() != null && seen.add(request.getId()))
                .toList();
    }

    private RouteOptimizationResult emptyResult(List<UnassignedRouteStop> unassigned) {
        return new RouteOptimizationResult(
                SolverStatus.INFEASIBLE,
                new SolverMetadata(SOLVER_ENGINE, 0, 0, 0),
                List.of(),
                unassigned
        );
    }

    private List<RouteVehicle> vehicles(RouteOptimizationCommand command) {
        List<Double> capacities = vehicleCapacities(command);
        List<RouteVehicle> vehicles = new ArrayList<>();
        for (int index = 0; index < command.vehicleCount(); index++) {
            vehicles.add(new RouteVehicle(index, capacities.get(index)));
        }
        return vehicles;
    }

    private List<Double> vehicleCapacities(RouteOptimizationCommand command) {
        if (command.vehicleCapacities() != null && !command.vehicleCapacities().isEmpty()) {
            return command.vehicleCapacities();
        }
        List<Double> capacities = new ArrayList<>();
        for (int index = 0; index < command.vehicleCount(); index++) {
            capacities.add(command.vehicleCapacity());
        }
        return capacities;
    }

    private int boundedTimeLimit(RouteOptions options) {
        int requested = options.timeLimitSeconds() == null ? defaultTimeLimitSeconds : options.timeLimitSeconds();
        return Math.max(1, Math.min(requested, maxTimeLimitSeconds));
    }

    private RouteLocation toRouteLocation(String id, Address address) {
        validateCoordinates(address.getLatitude(), address.getLongitude(), id);
        return new RouteLocation(id, address.getId(), address.getLatitude(), address.getLongitude());
    }

    private boolean hasCoordinates(Address address) {
        return address.getLatitude() != null && address.getLongitude() != null
                && validLatitude(address.getLatitude()) && validLongitude(address.getLongitude());
    }

    private void validateCoordinates(Double latitude, Double longitude, String label) {
        if (latitude == null || longitude == null || !validLatitude(latitude) || !validLongitude(longitude)) {
            throw new IllegalArgumentException(label + " coordinates are invalid");
        }
    }

    private boolean validLatitude(Double latitude) {
        return latitude >= -90.0 && latitude <= 90.0;
    }

    private boolean validLongitude(Double longitude) {
        return longitude >= -180.0 && longitude <= 180.0;
    }

    private double distanceKm(RouteLocation from, RouteLocation to) {
        double fromLat = Math.toRadians(from.latitude());
        double toLat = Math.toRadians(to.latitude());
        double deltaLat = toLat - fromLat;
        double deltaLon = Math.toRadians(to.longitude() - from.longitude());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(fromLat) * Math.cos(toLat) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371.0 * c;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record CandidateBuildResult(List<RouteCandidateStop> routableStops, List<UnassignedRouteStop> unassigned) {
    }
}
