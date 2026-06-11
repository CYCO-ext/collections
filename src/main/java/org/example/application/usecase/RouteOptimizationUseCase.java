package org.example.application.usecase;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.port.out.*;
import org.example.application.route.RouteModels.*;
import org.example.domain.entity.Address;
import org.example.domain.entity.CollectionRequest;
import org.example.domain.entity.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Singleton
public class RouteOptimizationUseCase {
    private static final Logger LOG = LoggerFactory.getLogger(RouteOptimizationUseCase.class);
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
        if (command.vehicles() == null || command.vehicles().isEmpty()) {
            throw new IllegalArgumentException("vehicles are required");
        }
        for (RouteVehicle vehicle : command.vehicles()) {
            if (vehicle == null) {
                throw new IllegalArgumentException("vehicles cannot contain null entries");
            }
            if (vehicle.capacity() <= 0) {
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
            LOG.info("Route optimization requested explicit candidates collectorId={} requestedIds={}", command.collectorId(), explicitIds);
            return collectionRequestPort.findByIds(explicitIds)
                    .onItem().invoke(found -> LOG.info(
                            "Route optimization loaded explicit candidates collectorId={} requestedCount={} foundCount={} foundIds={}",
                            command.collectorId(), explicitIds.size(), found == null ? 0 : found.size(), collectionIds(found)));
        }
        return collectionRequestPort.findInProgress(maxCandidates)
                .onItem().invoke(found -> LOG.info(
                        "Route optimization loaded in-progress candidates collectorId={} foundCount={} foundIds={}",
                        command.collectorId(), found == null ? 0 : found.size(), collectionIds(found)));
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
        List<UnassignedRouteStop> missingExplicitCandidates = missingExplicitCandidates(command, distinctCandidates);
        if (!missingExplicitCandidates.isEmpty()) {
            LOG.info("Route optimization missing explicit candidates collectorId={} missingIds={}",
                    command.collectorId(), unassignedIds(missingExplicitCandidates));
        }
        List<Uni<Address>> addressLookups = distinctCandidates.stream()
                .map(request -> addressPort.findById(request.getAddressId()))
                .toList();

        return Uni.combine().all().unis(addressLookups)
                .with(addresses -> buildCandidates(command, collector, depot, distinctCandidates, addresses)
                        .withAdditionalUnassigned(missingExplicitCandidates))
                .onItem().invoke(buildResult -> LOG.info(
                        "Route optimization candidate build collectorId={} routableIds={} unassigned={}",
                        command.collectorId(), routableIds(buildResult.routableStops()), unassignedSummary(buildResult.unassigned())))
                .flatMap(buildResult -> {
                    if (buildResult.routableStops().isEmpty()) {
                        return Uni.createFrom().item(emptyResult(buildResult.unassigned()));
                    }
                    return solveOnWorker(command, depot, buildResult);
                });
    }

    private Uni<RouteOptimizationResult> solveOnWorker(
            RouteOptimizationCommand command,
            RouteLocation depot,
            CandidateBuildResult buildResult
    ) {
        return Uni.createFrom().item(() -> {
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
                            effectiveDropPenalty(options, matrix)
                    );

                    LOG.info("Route optimization solver input collectorId={} routableIds={} vehicleIndexes={} dropPenalty={} timeLimitSeconds={}",
                            command.collectorId(), routableIds(buildResult.routableStops()), vehicleIndexes(vehicles), problem.dropPenalty(), problem.timeLimitSeconds());
                    RouteOptimizationResult solved = routeOptimizationPort.optimize(problem);
                    LOG.info("Route optimization solver output collectorId={} status={} assignedIds={} solverUnassigned={} droppedStops={}",
                            command.collectorId(), solved.status(), assignedIds(solved.routes()), unassignedSummary(solved.unassigned()),
                            solved.solver() == null ? null : solved.solver().droppedStops());
                    RouteOptimizationResult reconciled = reconcileSolverOutput(solved, buildResult.routableStops());
                    RouteOptimizationResult finalResult = reconciled.withAdditionalUnassigned(buildResult.unassigned());
                    LOG.info("Route optimization final output collectorId={} status={} assignedIds={} unassigned={}",
                            command.collectorId(), finalResult.status(), assignedIds(finalResult.routes()), unassignedSummary(finalResult.unassigned()));
                    return finalResult;
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
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
        double maxCapacity = command.vehicles().stream().map(RouteVehicle::capacity).max(Comparator.naturalOrder()).orElse(0.0);

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

    private List<UnassignedRouteStop> missingExplicitCandidates(RouteOptimizationCommand command, List<CollectionRequest> candidates) {
        List<String> explicitIds = command.candidateRequestIdsOrEmpty();
        if (explicitIds.isEmpty()) {
            return List.of();
        }
        Set<String> foundIds = new HashSet<>();
        for (CollectionRequest candidate : candidates) {
            foundIds.add(candidate.getId());
        }
        Set<String> missingIds = new LinkedHashSet<>(explicitIds);
        missingIds.removeAll(foundIds);
        return missingIds.stream()
                .map(id -> new UnassignedRouteStop(id, UnassignedReason.NOT_FOUND))
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

    private RouteOptimizationResult reconcileSolverOutput(RouteOptimizationResult solved, List<RouteCandidateStop> routableStops) {
        Set<String> missingIds = new LinkedHashSet<>();
        for (RouteCandidateStop stop : routableStops) {
            missingIds.add(stop.collectionRequestId());
        }
        if (solved.routes() != null) {
            for (RoutePlan route : solved.routes()) {
                if (route.stops() == null) {
                    continue;
                }
                for (RouteStop stop : route.stops()) {
                    missingIds.remove(stop.collectionRequestId());
                }
            }
        }
        if (solved.unassigned() != null) {
            for (UnassignedRouteStop stop : solved.unassigned()) {
                missingIds.remove(stop.collectionRequestId());
            }
        }
        if (missingIds.isEmpty()) {
            return solved;
        }
        List<UnassignedRouteStop> missing = missingIds.stream()
                .map(id -> new UnassignedRouteStop(id, UnassignedReason.SOLVER_DROPPED))
                .toList();
        return solved.withAdditionalUnassigned(missing);
    }

    private List<RouteVehicle> vehicles(RouteOptimizationCommand command) {
        return command.vehicles();
    }

    private int boundedTimeLimit(RouteOptions options) {
        int requested = options.timeLimitSeconds() == null ? defaultTimeLimitSeconds : options.timeLimitSeconds();
        return Math.max(1, Math.min(requested, maxTimeLimitSeconds));
    }

    private long effectiveDropPenalty(RouteOptions options, long[][] matrix) {
        long requested = options.dropPenalty() == null ? defaultDropPenalty : options.dropPenalty();
        long upperBound = sequentialRouteUpperBound(matrix);
        if (upperBound == Long.MAX_VALUE) {
            return requested;
        }
        return Math.max(requested, upperBound + 1);
    }

    private long sequentialRouteUpperBound(long[][] matrix) {
        if (matrix == null || matrix.length <= 1) {
            return 0;
        }
        long total = 0;
        for (int index = 0; index < matrix.length - 1; index++) {
            total = safeAdd(total, matrix[index][index + 1]);
        }
        return safeAdd(total, matrix[matrix.length - 1][0]);
    }

    private long safeAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
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

    private List<String> collectionIds(List<CollectionRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(CollectionRequest::getId)
                .toList();
    }

    private List<String> routableIds(List<RouteCandidateStop> stops) {
        if (stops == null) {
            return List.of();
        }
        return stops.stream()
                .map(RouteCandidateStop::collectionRequestId)
                .toList();
    }

    private List<String> assignedIds(List<RoutePlan> routes) {
        if (routes == null) {
            return List.of();
        }
        return routes.stream()
                .filter(route -> route.stops() != null)
                .flatMap(route -> route.stops().stream())
                .map(RouteStop::collectionRequestId)
                .toList();
    }

    private List<Integer> vehicleIndexes(List<RouteVehicle> vehicles) {
        if (vehicles == null) {
            return List.of();
        }
        return vehicles.stream()
                .map(RouteVehicle::index)
                .toList();
    }

    private List<String> unassignedIds(List<UnassignedRouteStop> stops) {
        if (stops == null) {
            return List.of();
        }
        return stops.stream()
                .map(UnassignedRouteStop::collectionRequestId)
                .toList();
    }

    private List<String> unassignedSummary(List<UnassignedRouteStop> stops) {
        if (stops == null) {
            return List.of();
        }
        return stops.stream()
                .map(stop -> stop.collectionRequestId() + ":" + stop.reason())
                .toList();
    }

    private record CandidateBuildResult(List<RouteCandidateStop> routableStops, List<UnassignedRouteStop> unassigned) {
        private CandidateBuildResult withAdditionalUnassigned(List<UnassignedRouteStop> additional) {
            if (additional == null || additional.isEmpty()) {
                return this;
            }
            List<UnassignedRouteStop> merged = new ArrayList<>(unassigned);
            merged.addAll(additional);
            return new CandidateBuildResult(routableStops, merged);
        }
    }
}
