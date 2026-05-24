# Design: move-request

Overview
--------

Add a manual route-edit operation for saved route suggestions. The operation moves one assigned collection request from its current vehicle route to another vehicle route inside the same saved route, calculates the best insertion position in the target vehicle, recalculates the affected route plans, and persists the updated saved route snapshot.

This design keeps the change separate from route suggestion generation. It does not call OR-Tools and does not mutate collection requests. It edits the saved suggestion snapshot that already exists in `saved_routes`.

Primary Flow
------------

1. Client calls `POST /collectors/routes/saved/{savedRouteId}/move-request`.
2. REST maps request body to `MoveRouteRequestCommand`.
3. `MoveRouteRequestUseCase` validates required fields.
4. Use case loads saved route by id through `SavedRoutePort.findById(savedRouteId)`.
5. If the saved route does not exist, the use case throws a not-found exception.
6. If the saved route status is `CLOSED`, the use case rejects the move.
7. Use case scans the saved route suggestion to find the assigned stop with the given `collectionRequestId`.
8. Use case validates that the stop exists exactly once.
9. If `sourceVehicleIndex` was provided, it must match the vehicle currently containing the stop.
10. Use case validates that `targetVehicleIndex` exists.
11. Use case rejects no-op moves where the request is already assigned to the target vehicle.
12. Use case validates that target vehicle capacity can accept the moved stop.
13. Use case removes the stop from the source vehicle candidate list.
14. Use case evaluates every valid insertion position in the target vehicle and chooses the position with the smallest additional route distance.
15. Use case inserts the moved stop into the target vehicle at the calculated best-fit position.
16. Use case recalculates source and target route plans.
17. Use case builds a refreshed fingerprint from the updated ordered route stop layout.
18. Use case checks for duplicate saved route fingerprint, ignoring the current saved route id.
19. Use case persists the updated saved route snapshot, fingerprint, assigned request ids, and `updatedAt`.
20. Endpoint returns HTTP 200 with the updated saved route result.

Proposed API
------------

Endpoint:

```text
POST /collectors/routes/saved/{savedRouteId}/move-request
```

Runtime URL:

```text
POST /api/collectors/routes/saved/{savedRouteId}/move-request
```

Request shape:

```json
{
  "collectionRequestId": "request-123",
  "sourceVehicleIndex": 0,
  "targetVehicleIndex": 1
}
```

Field rules:

- `collectionRequestId`: required.
- `sourceVehicleIndex`: optional; when present, must match the current vehicle containing the stop.
- `targetVehicleIndex`: required.
- No target sequence is accepted. The service calculates the insertion position automatically.

Success response:

```json
{
  "id": "saved-route-123",
  "collectorId": "collector-123",
  "status": "OPEN",
  "fingerprint": "sha256...",
  "assignedCollectionRequestIds": ["request-1", "request-123", "request-2"],
  "suggestion": {
    "status": "FEASIBLE",
    "solver": {
      "engine": "OR_TOOLS",
      "elapsedMs": 42,
      "objectiveDistanceMeters": 18450,
      "droppedStops": 0
    },
    "routes": [],
    "unassigned": []
  },
  "createdAt": "2026-05-12T10:00:00",
  "updatedAt": "2026-05-15T14:30:00",
  "closedAt": null
}
```

Error mapping:

- Missing saved route: HTTP 404.
- Invalid request body, closed route, request not in route, duplicate assigned stop, invalid target vehicle, no-op move, capacity violation: HTTP 400.
- Duplicate resulting route fingerprint: HTTP 409.
- Unexpected persistence/runtime failures: HTTP 500.

Architecture
------------

Application models:

- `MoveRouteRequestCommand`
  - `savedRouteId`, `collectionRequestId`, `sourceVehicleIndex`, `targetVehicleIndex`.
- `MoveRouteRequestResult`
  - Can reuse `SavedRouteResult` unless a dedicated response wrapper becomes necessary.
- `RouteMoveValidationException`
  - Validation failure mapped to HTTP 400.
- `SavedRouteSuggestionNotFoundException`
  - Existing exception can be reused for HTTP 404.
- `DuplicateSavedRouteException`
  - Existing exception can be reused for HTTP 409.

Application services:

- `MoveRouteRequestUseCase`
  - Orchestrates validation, automatic best-fit insertion, stop transfer, recalculation, fingerprint refresh, duplicate check, and persistence.
- `SavedRouteFingerprintService` or extracted helper
  - Recommended extraction from current save logic so save and move use identical fingerprint rules.
- `RoutePlanRecalculator`
  - Rebuilds `RoutePlan` stop sequences, accumulated loads, and distances after a manual edit.
- `BestRouteInsertionCalculator`
  - Evaluates candidate insertion positions for the moved stop in the target vehicle and selects the lowest additional-distance option.

Ports:

- Extend `SavedRoutePort` with:
  - `Uni<SavedRouteSuggestion> findById(String savedRouteId)`.
  - `Uni<SavedRouteSuggestion> findByFingerprintExcludingId(String fingerprint, String excludedSavedRouteId)` or equivalent duplicate check.
  - `Uni<Void> update(SavedRouteSuggestion route)`.

Infrastructure:

- Extend `SavedRouteRepository` with:
  - Find document by `_id`.
  - Replace/update full saved route document after move.
  - Duplicate lookup by fingerprint excluding current id.
- Extend `SavedRouteAdapter` to implement the new port methods.

Presentation:

- Extend `CollectorRouteResource` with:
  - `POST /saved/{savedRouteId}/move-request`.
- Add request DTO with `collectionRequestId`, optional `sourceVehicleIndex`, and `targetVehicleIndex`.
- Map success to HTTP 200 with updated saved route result.

Best-Fit Insertion Rules
------------------------

The client does not provide the target sequence. The service calculates it.

For the selected target vehicle:

1. Build candidate target stop lists by inserting the moved stop at every possible position, from before the first stop through after the last stop.
2. For each candidate list, calculate the resulting route distance with the available distance model.
3. Select the position with the lowest total route distance or lowest additional distance compared with the current target route.
4. If multiple positions tie, choose the earliest sequence to keep behavior deterministic.
5. Insert the moved stop at the selected position and then recalculate route fields.

Capacity is validated before insertion. If the target vehicle's current load plus the moved stop demand exceeds capacity, the move is rejected without evaluating insertion positions.

Recalculation Rules
-------------------

For every changed vehicle route:

1. Reassign stop `sequence` values in list order, starting at 1.
2. Recalculate each stop's `accumulatedLoad` as the sum of prior stop demand plus current demand.
3. Recalculate stop-to-stop `distanceFromPreviousMeters` using Haversine distance between previous stop coordinates and current stop coordinates.
4. Preserve the existing first stop's depot-to-first `distanceFromPreviousMeters` if the saved route does not contain depot coordinates.
5. Recalculate `totalLoad` from stop demand totals.
6. Recalculate `totalDistanceMeters` from recalculated leg distances plus any preserved final return/depot distance that already exists in the saved snapshot.

Known Design Constraint
-----------------------

The current saved route model stores the route suggestion snapshot but does not store the depot/start location independently. Because of that, an exact depot-to-first and last-to-depot recalculation is not possible after moving the first or last stop unless the depot is added to the saved snapshot.

MVP behavior should either:

- preserve existing depot leg distances when the depot cannot be reconstructed; or
- extend saved route persistence first to store depot/start location, then perform exact Haversine recalculation for depot legs.

Preferred implementation path: preserve existing depot legs for MVP and record a follow-up improvement to persist depot location if exact manual-edit distance totals become required.

Data Persistence
----------------

No new MongoDB collection is required. The operation updates existing documents in `saved_routes`.

Fields updated on successful move:

- `suggestion.routes`
- `suggestion.status` if recalculation changes partial/feasible classification, though normally it should remain unchanged
- `fingerprint`
- `assignedCollectionRequestIds`
- `updatedAt`

Fields preserved:

- `_id`
- `collectorId`
- `status`, unless future lifecycle logic requires otherwise
- `createdAt`
- `closedAt`
- `suggestion.solver`, unless a future design introduces manual-edit solver metadata
- `suggestion.unassigned`

Testing Strategy
----------------

Unit tests:

- `MoveRouteRequestUseCaseTest`
  - successful move calculates best-fit insertion;
  - tie between insertion positions chooses earliest sequence;
  - closed route rejected;
  - missing route rejected;
  - request not found rejected;
  - duplicate assigned stop rejected;
  - wrong source vehicle rejected;
  - invalid target vehicle rejected;
  - request already in target vehicle rejected;
  - capacity violation rejected;
  - duplicate resulting fingerprint rejected;
  - collection requests are not mutated.

Repository/adapter coverage:

- Verify new `SavedRoutePort` methods compile and map documents consistently.
- Add repository unit coverage if the project test style supports Mongo mapping without an integration fixture.

REST tests:

- Success returns HTTP 200 and updated route.
- Missing route maps to HTTP 404.
- Validation failures map to HTTP 400.
- Duplicate fingerprint maps to HTTP 409.

Documentation:

- Update README route section with endpoint, request body, automatic best-fit insertion behavior, response behavior, and limitations around depot-leg recalculation.
