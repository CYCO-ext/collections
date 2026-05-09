# Design: or-tools-routes

Overview
--------
Add a collector route optimization flow that accepts a planning request, loads eligible collection requests, builds a capacitated vehicle routing problem, solves it through OR-Tools, and returns a read-only route suggestion.

The design keeps OR-Tools behind an application port. REST resources and use cases speak in domain/application models, not solver-specific classes.

Primary Flow
------------

1. Collector calls `POST /collectors/routes/suggest`.
2. REST DTO is validated and mapped to `RouteOptimizationCommand`.
3. `RouteOptimizationUseCase` loads the collector, candidate collection requests, and pickup addresses.
4. The use case filters candidates that cannot be routed and records unassigned reasons.
5. The use case asks `DistanceMatrixPort` for a matrix covering depot plus candidate stops.
6. The use case calls `RouteOptimizationPort.optimize(problem)`.
7. `OrToolsRouteOptimizationAdapter` solves the CVRP and returns route assignments.
8. The use case maps solver output into route response DTOs and adds metadata/unassigned candidates.
9. No collection request or collector state is changed.

Proposed API
------------

Endpoint:

```text
POST /collectors/routes/suggest
```

Request shape:

```json
{
  "collectorId": "collector-123",
  "vehicleCount": 2,
  "vehicleCapacity": 100.0,
  "vehicleCapacities": [100.0, 80.0],
  "start": {
    "type": "COORDINATES",
    "addressId": null,
    "latitude": -23.5505,
    "longitude": -46.6333
  },
  "endAtStart": true,
  "candidateRequestIds": ["request-1", "request-2"],
  "filters": {
    "materialIds": ["paper", "plastic"],
    "maxDistanceKmFromStart": 50.0,
    "onlyInProgress": true
  },
  "options": {
    "timeLimitSeconds": 5,
    "allowDroppingStops": true,
    "dropPenalty": 100000,
    "distanceUnit": "METERS"
  }
}
```

Validation rules:

- `collectorId` is required.
- `vehicleCount` must be greater than zero.
- Either `vehicleCapacity` or `vehicleCapacities` is required.
- If `vehicleCapacities` is present, its size must equal `vehicleCount`.
- Every capacity must be greater than zero.
- `start` must resolve to valid coordinates.
- `timeLimitSeconds` defaults to a small bounded value and cannot exceed the configured maximum.
- `dropPenalty` is used only when `allowDroppingStops` is true.

Response shape:

```json
{
  "status": "FEASIBLE",
  "solver": {
    "engine": "OR_TOOLS",
    "elapsedMs": 42,
    "objectiveDistanceMeters": 18450,
    "droppedStops": 1
  },
  "routes": [
    {
      "vehicleIndex": 0,
      "capacity": 100.0,
      "totalLoad": 75.0,
      "totalDistanceMeters": 9400,
      "stops": [
        {
          "sequence": 1,
          "collectionRequestId": "request-1",
          "addressId": "address-1",
          "latitude": -23.56,
          "longitude": -46.64,
          "demand": 25.0,
          "accumulatedLoad": 25.0,
          "distanceFromPreviousMeters": 1800
        }
      ]
    }
  ],
  "unassigned": [
    {
      "collectionRequestId": "request-9",
      "reason": "SOLVER_DROPPED"
    }
  ]
}
```

Architecture
------------

Application layer:

- `RouteOptimizationUseCase`
  - Coordinates validation, candidate loading, filtering, distance matrix creation, solver invocation, and response mapping.
- `RouteOptimizationPort`
  - Boundary for the optimization engine.
- `DistanceMatrixPort`
  - Boundary for distance calculation.
- `RouteOptimizationCommand`, `RouteOptimizationProblem`, `RouteOptimizationResult`, `RoutePlan`, `RouteStop`, `UnassignedRouteStop`
  - Application/domain models independent from REST and OR-Tools.

Infrastructure layer:

- `OrToolsRouteOptimizationAdapter`
  - Loads OR-Tools native library.
  - Creates `RoutingIndexManager` and `RoutingModel`.
  - Registers distance and demand callbacks.
  - Adds capacity dimension using vehicle capacities.
  - Optionally adds disjunction penalties so stops can be dropped when requested.
  - Sets search parameters with a bounded time limit.
  - Maps the `Assignment` into route plans and dropped stops.
- `HaversineDistanceMatrixAdapter`
  - Computes a deterministic matrix from coordinates in meters.
  - Provides a local, dependency-free MVP distance provider.

Presentation layer:

- Add route DTOs and endpoint under `CollectorResource`, or create `CollectorRouteResource` if the class becomes too large.
- Keep REST-specific validation messages close to the DTO/resource.

Domain/Data Model
-----------------

No persistent route entity is required for MVP.

New in-memory/application models:

- `RouteLocation`
  - `id`, `latitude`, `longitude`, optional `addressId`.
- `RouteVehicle`
  - `index`, `capacity`.
- `RouteCandidateStop`
  - `collectionRequestId`, `addressId`, `materialIds`, `demand`, `location`, optional priority.
- `RouteOptimizationProblem`
  - depot/start location, vehicles, candidate stops, distance matrix, options.
- `RouteOptimizationResult`
  - status, route list, unassigned list, solver metadata.

Candidate Selection
-------------------

Candidate input can be explicit or filter-based:

- If `candidateRequestIds` is present, load only those requests.
- If omitted, load `IN_PROGRESS` requests using repository support added for this feature.
- Filter by required collection request status first: only `IN_PROGRESS` requests are routable.
- Filter by collector material compatibility using `Collector.acceptsAllMaterials(...)`.
- Filter by coordinates: missing pickup coordinates become unassigned, not fatal.
- Filter by optional `maxDistanceKmFromStart` before solving to keep solver inputs bounded.

Distance Strategy
-----------------

MVP uses Haversine distance in meters:

- Predictable and testable.
- No external network dependency.
- Good enough for route suggestion ordering when precise road distance is not available.

Future road-network distance providers can implement the same `DistanceMatrixPort`.

OR-Tools Strategy
-----------------

The solver problem is a capacitated VRP:

- Node 0 is the depot/start location.
- Nodes 1..N are candidate pickup stops.
- Arc cost uses the distance matrix.
- Demand callback returns `0` for depot and request weight for pickup stops.
- Vehicle capacity dimension uses the per-vehicle capacity array.
- `PATH_CHEAPEST_ARC` can be the first solution strategy.
- `GUIDED_LOCAL_SEARCH` can improve solutions within the time limit.
- `allowDroppingStops=true` adds disjunction penalties for candidate nodes.
- If no solution is found, return `INFEASIBLE` with unassigned reasons instead of throwing a generic error.

Status Model
------------

Route optimization status values:

- `OPTIMAL`: solver proves best solution within the configured search.
- `FEASIBLE`: solver returns a usable solution but not proven optimal.
- `PARTIAL`: solution exists and at least one optional stop was dropped.
- `INFEASIBLE`: no valid route assignment exists.
- `INVALID_INPUT`: request failed validation.

Implementation may map OR-Tools' exact status values into this app-level status enum.

Configuration
-------------

Add application properties:

```properties
routes.optimization.max-time-limit-seconds=30
routes.optimization.default-time-limit-seconds=5
routes.optimization.default-drop-penalty=100000
routes.optimization.max-candidates=100
routes.optimization.distance-provider=haversine
```

Repository Needs
----------------

`CollectionRequestPort`/repository will need query support for route candidates:

- Find by list of IDs.
- Find `IN_PROGRESS` requests, optionally capped by max candidates.

Existing `AddressPort.findById(...)` can be reused for pickup coordinates if it is already implemented for request addresses.

Failure Handling
----------------

- Validation errors return HTTP 400 with field-level or clear text details.
- Missing collector returns HTTP 404 or mapped bad request according to existing REST style.
- Missing request addresses are represented as unassigned candidates when possible.
- OR-Tools initialization failures return a controlled service error and are logged with the adapter name.
- Solver timeout returns the best available solution if OR-Tools provides one, otherwise `INFEASIBLE` or `PARTIAL` depending on dropped candidates.

Testing Strategy
----------------

Unit tests:

- Haversine matrix returns symmetric distances and zero diagonal.
- Use case filters missing coordinates and incompatible material requests.
- Use case does not mutate collection request status.
- OR-Tools adapter respects vehicle capacity with one vehicle.
- OR-Tools adapter splits stops across multiple vehicles when required.
- OR-Tools adapter reports dropped stops when total demand exceeds capacity and dropping is enabled.

Resource tests:

- Valid request returns route response shape.
- Invalid vehicle count/capacity returns HTTP 400.
- Missing coordinates are included in `unassigned`.

Open Questions
--------------

- Should route suggestions consider only `IN_PROGRESS` requests already assigned to the collector, or all `IN_PROGRESS` requests that match the collector's materials?
- Should capacity be weight-only, volume-only, or support multiple dimensions later?
- Should the route start from the collector registered address by default when no explicit start is provided?
- Should suggested routes be persisted for audit/history, or remain transient?
