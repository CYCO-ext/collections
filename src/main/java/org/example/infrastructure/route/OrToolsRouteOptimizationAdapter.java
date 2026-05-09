package org.example.infrastructure.route;

import com.google.ortools.Loader;
import jakarta.inject.Singleton;
import org.example.application.port.out.RouteOptimizationPort;
import org.example.application.route.RouteModels.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class OrToolsRouteOptimizationAdapter implements RouteOptimizationPort {
    private static final Logger LOG = LoggerFactory.getLogger(OrToolsRouteOptimizationAdapter.class);
    private static final String ENGINE = "OR_TOOLS";
    private static final String FALLBACK_ENGINE = "GREEDY_FALLBACK";
    private static final long DEMAND_SCALE = 1000L;

    @Override
    public RouteOptimizationResult optimize(RouteOptimizationProblem problem) {
        long startedAt = System.currentTimeMillis();
        try {
            ReflectionSolver solver = new ReflectionSolver(problem);
            Object solution = solver.solve();
            long elapsedMs = System.currentTimeMillis() - startedAt;
            if (solution == null) {
                return infeasible(problem, elapsedMs, ENGINE);
            }

            List<UnassignedRouteStop> droppedStops = solver.droppedStops(solution);
            List<RoutePlan> routes = solver.routes(solution);
            SolverStatus status = droppedStops.isEmpty() ? SolverStatus.FEASIBLE : SolverStatus.PARTIAL;
            SolverMetadata metadata = new SolverMetadata(ENGINE, elapsedMs, solver.objectiveValue(solution), droppedStops.size());
            return new RouteOptimizationResult(status, metadata, routes, droppedStops);
        } catch (ReflectiveOperationException | LinkageError ex) {
            Throwable rootCause = rootCause(ex);
            LOG.warn("OR-Tools native solver is unavailable; using greedy fallback route planner: {}", rootCause.toString());
            return greedyFallback(problem, System.currentTimeMillis() - startedAt);
        }
    }

    private RouteOptimizationResult greedyFallback(RouteOptimizationProblem problem, long elapsedMs) {
        List<MutableRoute> routes = new ArrayList<>();
        for (RouteVehicle vehicle : problem.vehicles()) {
            routes.add(new MutableRoute(vehicle.index(), vehicle.capacity()));
        }

        List<UnassignedRouteStop> unassigned = new ArrayList<>();
        for (int stopIndex = 0; stopIndex < problem.stops().size(); stopIndex++) {
            RouteCandidateStop stop = problem.stops().get(stopIndex);
            int node = stopIndex + 1;
            MutableRoute route = bestRoute(problem, routes, stop, node);
            if (route == null) {
                unassigned.add(new UnassignedRouteStop(
                        stop.collectionRequestId(),
                        problem.options().allowDroppingStopsOrDefault() ? UnassignedReason.SOLVER_DROPPED : UnassignedReason.INFEASIBLE
                ));
                continue;
            }
            route.add(problem, stop, node);
        }

        long totalDistance = 0;
        List<RoutePlan> routePlans = new ArrayList<>();
        for (MutableRoute route : routes) {
            route.close(problem.distanceMatrixMeters());
            totalDistance += route.totalDistanceMeters;
            routePlans.add(route.toPlan());
        }

        SolverStatus status = unassigned.isEmpty()
                ? SolverStatus.FEASIBLE
                : problem.options().allowDroppingStopsOrDefault() ? SolverStatus.PARTIAL : SolverStatus.INFEASIBLE;
        return new RouteOptimizationResult(
                status,
                new SolverMetadata(FALLBACK_ENGINE, elapsedMs, totalDistance, unassigned.size()),
                routePlans,
                unassigned
        );
    }

    private MutableRoute bestRoute(RouteOptimizationProblem problem, List<MutableRoute> routes, RouteCandidateStop stop, int node) {
        MutableRoute best = null;
        long bestDistance = Long.MAX_VALUE;
        for (MutableRoute route : routes) {
            if (!route.canFit(stop.demand())) {
                continue;
            }
            long distance = problem.distanceMatrixMeters()[route.currentNode][node];
            if (distance < bestDistance) {
                best = route;
                bestDistance = distance;
            }
        }
        return best;
    }

    private RouteOptimizationResult infeasible(RouteOptimizationProblem problem, long elapsedMs, String engine) {
        List<UnassignedRouteStop> unassigned = problem.stops().stream()
                .map(stop -> new UnassignedRouteStop(stop.collectionRequestId(), UnassignedReason.INFEASIBLE))
                .toList();
        return new RouteOptimizationResult(
                SolverStatus.INFEASIBLE,
                new SolverMetadata(engine, elapsedMs, 0, 0),
                List.of(),
                unassigned
        );
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException invocationTargetException && invocationTargetException.getTargetException() != null) {
            current = invocationTargetException.getTargetException();
        }
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static final class MutableRoute {
        private final int vehicleIndex;
        private final double capacity;
        private final List<RouteStop> stops = new ArrayList<>();
        private int currentNode;
        private double totalLoad;
        private long totalDistanceMeters;

        private MutableRoute(int vehicleIndex, double capacity) {
            this.vehicleIndex = vehicleIndex;
            this.capacity = capacity;
            this.currentNode = 0;
        }

        private boolean canFit(double demand) {
            return totalLoad + demand <= capacity;
        }

        private void add(RouteOptimizationProblem problem, RouteCandidateStop stop, int node) {
            long distanceFromPrevious = problem.distanceMatrixMeters()[currentNode][node];
            totalDistanceMeters += distanceFromPrevious;
            totalLoad += stop.demand();
            currentNode = node;
            stops.add(new RouteStop(
                    stops.size() + 1,
                    stop.collectionRequestId(),
                    stop.addressId(),
                    stop.location().latitude(),
                    stop.location().longitude(),
                    stop.demand(),
                    totalLoad,
                    distanceFromPrevious
            ));
        }

        private void close(long[][] distanceMatrixMeters) {
            if (!stops.isEmpty()) {
                totalDistanceMeters += distanceMatrixMeters[currentNode][0];
                currentNode = 0;
            }
        }

        private RoutePlan toPlan() {
            return new RoutePlan(vehicleIndex, capacity, totalLoad, totalDistanceMeters, List.copyOf(stops));
        }
    }

    private static final class ReflectionSolver {
        private final RouteOptimizationProblem problem;
        private final long[] demands;
        private final Object manager;
        private final Object routing;
        private final Class<?> managerClass;
        private final Class<?> routingClass;

        private ReflectionSolver(RouteOptimizationProblem problem) throws ReflectiveOperationException {
            this.problem = problem;
            this.demands = scaledDemands(problem.stops());

            Loader.loadNativeLibraries();

            managerClass = Class.forName("com.google.ortools.constraintsolver.RoutingIndexManager");
            routingClass = Class.forName("com.google.ortools.constraintsolver.RoutingModel");
            manager = managerClass.getConstructor(int.class, int.class, int.class)
                    .newInstance(problem.distanceMatrixMeters().length, problem.vehicles().size(), 0);
            routing = routingClass.getConstructor(managerClass).newInstance(manager);
            configureRoutingModel();
        }

        private Object solve() throws ReflectiveOperationException {
            Object searchParameters = searchParameters();
            return findMethod(routingClass, "solveWithParameters", 1).invoke(routing, searchParameters);
        }

        private long objectiveValue(Object solution) throws ReflectiveOperationException {
            return ((Number) solution.getClass().getMethod("objectiveValue").invoke(solution)).longValue();
        }

        private void configureRoutingModel() throws ReflectiveOperationException {
            int transitCallbackIndex = registerTransitCallback();
            routingClass.getMethod("setArcCostEvaluatorOfAllVehicles", int.class).invoke(routing, transitCallbackIndex);

            int demandCallbackIndex = registerDemandCallback();
            Method addCapacity = findMethod(routingClass, "addDimensionWithVehicleCapacity", 5);
            Object slack = addCapacity.getParameterTypes()[1].equals(int.class) ? 0 : 0L;
            addCapacity.invoke(routing, demandCallbackIndex, slack, scaledCapacities(problem), true, "Capacity");

            if (problem.options().allowDroppingStopsOrDefault()) {
                Method addDisjunction = routingClass.getMethod("addDisjunction", long[].class, long.class);
                for (int node = 1; node < problem.distanceMatrixMeters().length; node++) {
                    addDisjunction.invoke(routing, new long[]{nodeToIndex(node)}, problem.dropPenalty());
                }
            }
        }

        private int registerTransitCallback() throws ReflectiveOperationException {
            Class<?> callbackClass = Class.forName("com.google.ortools.constraintsolver.LongLongToLong");
            Object callback = proxy(callbackClass, args -> {
                int fromNode = indexToNode(((Number) args[0]).longValue());
                int toNode = indexToNode(((Number) args[1]).longValue());
                return problem.distanceMatrixMeters()[fromNode][toNode];
            });
            return ((Number) routingClass.getMethod("registerTransitCallback", callbackClass).invoke(routing, callback)).intValue();
        }

        private int registerDemandCallback() throws ReflectiveOperationException {
            Class<?> callbackClass = Class.forName("com.google.ortools.constraintsolver.LongToLong");
            Object callback = proxy(callbackClass, args -> demands[indexToNode(((Number) args[0]).longValue())]);
            return ((Number) routingClass.getMethod("registerUnaryTransitCallback", callbackClass).invoke(routing, callback)).intValue();
        }

        private Object searchParameters() throws ReflectiveOperationException {
            Object defaults = Class.forName("com.google.ortools.constraintsolver.main")
                    .getMethod("defaultRoutingSearchParameters")
                    .invoke(null);
            Object builder = defaults.getClass().getMethod("toBuilder").invoke(defaults);

            Object firstSolution = enumValue("com.google.ortools.constraintsolver.FirstSolutionStrategy$Value", "PATH_CHEAPEST_ARC");
            Object localSearch = enumValue("com.google.ortools.constraintsolver.LocalSearchMetaheuristic$Value", "GUIDED_LOCAL_SEARCH");
            Object durationBuilder = Class.forName("com.google.protobuf.Duration")
                    .getMethod("newBuilder")
                    .invoke(null);
            durationBuilder.getClass().getMethod("setSeconds", long.class).invoke(durationBuilder, (long) problem.timeLimitSeconds());
            Object duration = durationBuilder.getClass().getMethod("build").invoke(durationBuilder);

            builder.getClass().getMethod("setFirstSolutionStrategy", firstSolution.getClass()).invoke(builder, firstSolution);
            builder.getClass().getMethod("setLocalSearchMetaheuristic", localSearch.getClass()).invoke(builder, localSearch);
            builder.getClass().getMethod("setTimeLimit", duration.getClass()).invoke(builder, duration);
            return builder.getClass().getMethod("build").invoke(builder);
        }

        private List<UnassignedRouteStop> droppedStops(Object solution) throws ReflectiveOperationException {
            List<UnassignedRouteStop> dropped = new ArrayList<>();
            Method nextVar = routingClass.getMethod("nextVar", long.class);
            for (int node = 1; node < problem.stops().size() + 1; node++) {
                long index = nodeToIndex(node);
                Object next = nextVar.invoke(routing, index);
                if (value(solution, next) == index) {
                    dropped.add(new UnassignedRouteStop(problem.stops().get(node - 1).collectionRequestId(), UnassignedReason.SOLVER_DROPPED));
                }
            }
            return dropped;
        }

        private List<RoutePlan> routes(Object solution) throws ReflectiveOperationException {
            List<RoutePlan> routes = new ArrayList<>();
            Method start = routingClass.getMethod("start", int.class);
            Method isEnd = routingClass.getMethod("isEnd", long.class);
            Method nextVar = routingClass.getMethod("nextVar", long.class);
            Method arcCost = findMethod(routingClass, "getArcCostForVehicle", 3);

            for (int vehicleIndex = 0; vehicleIndex < problem.vehicles().size(); vehicleIndex++) {
                long index = ((Number) start.invoke(routing, vehicleIndex)).longValue();
                long routeDistance = 0;
                double routeLoad = 0.0;
                int sequence = 1;
                List<RouteStop> stops = new ArrayList<>();

                while (!((Boolean) isEnd.invoke(routing, index))) {
                    long previousIndex = index;
                    index = value(solution, nextVar.invoke(routing, index));
                    Object vehicleArgument = arcCost.getParameterTypes()[2].equals(int.class) ? vehicleIndex : (long) vehicleIndex;
                    long distanceFromPrevious = ((Number) arcCost.invoke(routing, previousIndex, index, vehicleArgument)).longValue();
                    routeDistance += distanceFromPrevious;

                    if (!((Boolean) isEnd.invoke(routing, index))) {
                        int node = indexToNode(index);
                        RouteCandidateStop candidate = problem.stops().get(node - 1);
                        routeLoad += demands[node] / (double) DEMAND_SCALE;
                        stops.add(new RouteStop(
                                sequence++,
                                candidate.collectionRequestId(),
                                candidate.addressId(),
                                candidate.location().latitude(),
                                candidate.location().longitude(),
                                candidate.demand(),
                                routeLoad,
                                distanceFromPrevious
                        ));
                    }
                }

                routes.add(new RoutePlan(vehicleIndex, problem.vehicles().get(vehicleIndex).capacity(), routeLoad, routeDistance, stops));
            }
            return routes;
        }

        private long value(Object solution, Object variable) throws ReflectiveOperationException {
            Method method = findMethod(solution.getClass(), "value", 1);
            return ((Number) method.invoke(solution, variable)).longValue();
        }

        private long nodeToIndex(int node) throws ReflectiveOperationException {
            return ((Number) managerClass.getMethod("nodeToIndex", int.class).invoke(manager, node)).longValue();
        }

        private int indexToNode(long index) throws ReflectiveOperationException {
            return ((Number) managerClass.getMethod("indexToNode", long.class).invoke(manager, index)).intValue();
        }

        private static long[] scaledDemands(List<RouteCandidateStop> stops) {
            long[] demands = new long[stops.size() + 1];
            demands[0] = 0;
            for (int index = 0; index < stops.size(); index++) {
                demands[index + 1] = Math.max(1L, Math.round(stops.get(index).demand() * DEMAND_SCALE));
            }
            return demands;
        }

        private static long[] scaledCapacities(RouteOptimizationProblem problem) {
            long[] capacities = new long[problem.vehicles().size()];
            for (int index = 0; index < problem.vehicles().size(); index++) {
                capacities[index] = Math.max(1L, Math.round(problem.vehicles().get(index).capacity() * DEMAND_SCALE));
            }
            return capacities;
        }

        private static Object proxy(Class<?> callbackClass, Callback callback) {
            InvocationHandler handler = (Object proxy, Method method, Object[] args) -> {
                if (method.getDeclaringClass().equals(Object.class)) {
                    return switch (method.getName()) {
                        case "toString" -> callbackClass.getSimpleName() + "Proxy";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> null;
                    };
                }
                return callback.invoke(args == null ? new Object[0] : args);
            };
            return Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass}, handler);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Object enumValue(String className, String name) throws ReflectiveOperationException {
            return Enum.valueOf((Class<? extends Enum>) Class.forName(className), name);
        }

        private static Method findMethod(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    return method;
                }
            }
            throw new NoSuchMethodException(type.getName() + "." + name);
        }

        private interface Callback {
            Object invoke(Object[] args) throws ReflectiveOperationException;
        }
    }
}
