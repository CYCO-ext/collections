# Design: vehicle-name

Overview
--------

Add a user-provided vehicle nickname to the route creation contract and carry it through route optimization results, saved route snapshots, and route mutation workflows.

The design keeps `vehicleIndex` as the technical identifier and treats the nickname as immutable display metadata for the route snapshot. Solver behavior, duplicate detection, and route movement continue to use indexes and collection request ordering.

Primary Route Creation Flow
---------------------------

1. Client calls `POST /collectors/routes/suggest` with `vehicles[]` entries containing `name` and `capacity`.
2. `CollectorRouteResource.toVehicles()` validates each vehicle name and capacity.
3. The resource trims each name and creates `RouteVehicle(index, name, capacity)` in request order.
4. `RouteOptimizationUseCase` receives the command and builds the optimization problem without changing filtering or candidate selection behavior.
5. `RouteOptimizationPort.optimize()` produces route plans.
6. The OR-Tools adapter and greedy fallback copy `RouteVehicle.name()` into each returned `RoutePlan.vehicleName`.
7. The endpoint returns the optimization result with `vehicleIndex`, `vehicleName`, capacity, load, distance, and stops.

Saved Route Flow
----------------

1. Client saves a route suggestion that already contains `vehicleName` on each route plan.
2. `SaveRouteSuggestionUseCase` validates assigned stops and builds the existing duplicate fingerprint.
3. Fingerprinting continues to use collector id, `vehicleIndex`, sequence, and collection request id; it does not include vehicle name.
4. `SavedRouteRepository` stores `vehicleName` in each persisted route plan document.
5. `SavedRouteRepository` tolerates old documents where `vehicleName` is missing by mapping it as `null`.
6. List and map use cases return saved route snapshots with the stored vehicle names.

Move Route Request Flow
-----------------------

1. Client calls `POST /collectors/routes/saved/{savedRouteId}/move-request` using `sourceVehicleIndex` and `targetVehicleIndex`.
2. `MoveRouteRequestUseCase` loads the saved route and updates route stop placement.
3. Rebuilt `RoutePlan` values preserve each original plan's `vehicleName` with its `vehicleIndex`.
4. Updated saved route snapshots are persisted with unchanged vehicle names.
5. The recalculated fingerprint remains based on ordered stop tuples, not display names.

Proposed API
------------

Route suggestion request:

```json
{
  "collectorId": "collector-123",
  "vehicles": [
    {
      "name": "Truck A",
      "capacity": 100.0
    },
    {
      "name": "Small van",
      "capacity": 60.0
    }
  ],
  "start": {
    "type": "COLLECTOR_ADDRESS"
  },
  "endAtStart": true,
  "candidateRequestIds": ["request-1", "request-2"],
  "filters": {
    "onlyInProgress": true
  },
  "options": {
    "allowDroppingStops": true
  }
}
```

Route suggestion response excerpt:

```json
{
  "status": "FEASIBLE",
  "routes": [
    {
      "vehicleIndex": 0,
      "vehicleName": "Truck A",
      "capacity": 100.0,
      "totalLoad": 75.0,
      "totalDistanceMeters": 9400,
      "stops": []
    }
  ],
  "unassigned": []
}
```

Validation responses:

```text
HTTP 400
vehicle name is required
```

```text
HTTP 400
vehicle name must be at most 80 characters
```

Architecture
------------

Application route models:

- `RouteVehicle`
  - Add `String name` between `index` and `capacity` or after `capacity`, following the least disruptive constructor update across call sites.
  - Keep `index` and `capacity` unchanged semantically.
- `RoutePlan`
  - Add `String vehicleName` next to `vehicleIndex`.
  - Use `vehicleName` instead of `name` in result payloads to avoid colliding with route-level concepts.

Presentation layer:

- `CollectorRouteResource.RouteVehicleDTO`
  - Add `name` field with getter/setter.
- `CollectorRouteResource.toVehicles()`
  - Validate capacity as today.
  - Validate `name != null`, `!name.trim().isEmpty()`, and `trimmed.length() <= 80`.
  - Use the trimmed name when constructing `RouteVehicle`.

Optimization layer:

- `OrToolsRouteOptimizationAdapter.MutableRoute`
  - Store `vehicleName` with `vehicleIndex` and `capacity`.
  - Initialize from `RouteVehicle.name()`.
  - Include `vehicleName` when building `RoutePlan`.
- `ReflectionSolver.routes()`
  - When converting solver vehicle routes to `RoutePlan`, look up the matching `RouteVehicle` by vehicle index and pass its name.
- `greedyFallback()`
  - Build `MutableRoute(vehicle.index(), vehicle.name(), vehicle.capacity())`.

Saved route persistence:

- `SavedRouteRepository.RoutePlanDocument`
  - Add nullable `vehicleName` field.
- Document mapping methods
  - Write `RoutePlan.vehicleName()` into MongoDB.
  - Read missing `vehicleName` as `null` for backward compatibility.
- No new index is required because vehicle nickname is not queried.

Move and recalculation logic:

- `RoutePlanRecalculator`
  - Preserve `vehicleName` when rebuilding route plans.
- `MoveRouteRequestUseCase`
  - No contract change; still uses indexes.
  - Existing validations remain index-based.

Fingerprinting:

- `SavedRouteFingerprintService`
  - No functional change expected.
  - Add or update a regression test proving nickname changes do not alter fingerprints when indexes and stops are equal.

Data Compatibility
------------------

Existing saved route documents do not contain `vehicleName`. Repository reads must tolerate the missing field. New saved route documents should include `vehicleName` when present in the route suggestion.

No bulk migration is required for the first implementation. Existing saved route responses may expose `vehicleName: null` for older documents until routes are recreated.

Testing Strategy
----------------

Unit tests:

- `CollectorRouteResourceTest`
  - Reject missing, blank, and too-long vehicle names.
  - Accept valid names and return route plans containing `vehicleName`.
- `RouteOptimizationUseCaseTest`
  - Verify command/model flow preserves names when optimization port returns named plans if applicable.
- `OrToolsRouteOptimizationAdapterTest`
  - Verify greedy fallback route plans include the input vehicle names.
  - Verify OR-Tools path includes names where the native solver is available or through existing adapter seams.
- `SavedRouteRepository` mapping coverage, if repository tests exist or can be added without a Mongo fixture.
  - Verify route plan document writes and reads `vehicleName`.
  - Verify missing field maps without failure.
- `MoveRouteRequestUseCaseTest`
  - Verify moved route plans retain their original names.
- `SavedRouteFingerprintServiceTest`
  - Verify changing `vehicleName` only does not change the fingerprint.

Documentation checks:

- Update README route suggestion request/response examples.
- Update saved route examples if they include route plan payloads.

Risks
-----

- Java record constructor changes will require all `RouteVehicle` and `RoutePlan` call sites to be updated together.
- JSON compatibility for old clients depends on whether they can tolerate the additional `vehicleName` response field.
- Old clients that omit `vehicles[].name` will start receiving HTTP 400; this is intentional per the feature requirement, but it is a breaking request contract change.
