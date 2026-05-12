# Design: save-route

Overview
--------
Add durable persistence for route suggestions. A collector can save a route suggestion, retrieve saved routes, and rely on the service to close saved route suggestions once every assigned collection request in the saved route is completed.

The design keeps persistence behind a saved-route port/repository and keeps route saving separate from route optimization. The save endpoint accepts a route suggestion result and persists a snapshot; it does not re-run OR-Tools and does not mutate collection requests.

Primary Save Flow
-----------------

1. Client calls `POST /collectors/routes/save` with `collectorId` and route suggestion payload.
2. REST DTO is validated and mapped to `SaveRouteSuggestionCommand`.
3. `SaveRouteSuggestionUseCase` validates collector id, route plans, assigned stops, and collection request ids.
4. The use case loads assigned collection requests using `CollectionRequestPort.findByIds(ids)`.
5. The use case verifies assigned collection requests exist and are associated with the collector where applicable.
6. The use case builds a duplicate fingerprint from collector id and normalized route stop ordering.
7. The use case asks `SavedRoutePort` if an open or existing route with that fingerprint already exists.
8. If duplicate exists, the use case fails with a conflict error.
9. The use case determines saved route status:
   - `CLOSED` if every assigned collection request is already `COMPLETED`.
   - `OPEN` otherwise.
10. The use case persists `SavedRouteSuggestion` in MongoDB collection `saved_routes`.
11. Endpoint returns HTTP 201 with the saved route document/result.

Primary List Flow
-----------------

1. Client calls `GET /collectors/routes/saved`.
2. REST delegates to `ListSavedRoutesUseCase`.
3. Use case calls `SavedRoutePort.findAllOrderByCreatedAtDesc()`.
4. Endpoint returns HTTP 200 with saved routes ordered newest first.

Close After Completion Flow
---------------------------

1. Existing `CompletionUseCase` marks a `CollectionRequest` as `COMPLETED`.
2. After the completion update succeeds, it calls a saved-route closure use case/port with the completed request id.
3. The saved-route closure logic loads open saved routes containing the completed request id.
4. For each open saved route, it loads all assigned collection requests.
5. If every assigned request status is `COMPLETED`, it updates the saved route status to `CLOSED` and sets `closedAt`/`updatedAt`.
6. Already closed routes are ignored.

Proposed API
------------

Save endpoint:

```text
POST /collectors/routes/save
```

Runtime URL:

```text
POST /api/collectors/routes/save
```

Save request shape:

```json
{
  "collectorId": "collector-123",
  "source": "ROUTE_SUGGESTION",
  "suggestion": {
    "status": "FEASIBLE",
    "solver": {
      "engine": "OR_TOOLS",
      "elapsedMs": 42,
      "objectiveDistanceMeters": 18450,
      "droppedStops": 0
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
    "unassigned": []
  }
}
```

Save response shape:

```json
{
  "id": "saved-route-123",
  "collectorId": "collector-123",
  "status": "OPEN",
  "fingerprint": "sha256...",
  "assignedCollectionRequestIds": ["request-1"],
  "suggestion": { "status": "FEASIBLE", "routes": [], "unassigned": [] },
  "createdAt": "2026-05-12T10:00:00",
  "updatedAt": "2026-05-12T10:00:00",
  "closedAt": null
}
```

Duplicate response:

```text
HTTP 409
Route suggestion already saved
```

List endpoint:

```text
GET /collectors/routes/saved
```

Runtime URL:

```text
GET /api/collectors/routes/saved
```

List response shape:

```json
[
  {
    "id": "saved-route-123",
    "collectorId": "collector-123",
    "status": "OPEN",
    "assignedCollectionRequestIds": ["request-1"],
    "createdAt": "2026-05-12T10:00:00",
    "updatedAt": "2026-05-12T10:00:00",
    "closedAt": null,
    "suggestion": {}
  }
]
```

Architecture
------------

Domain/application models:

- `SavedRouteStatus`
  - `OPEN`, `CLOSED`.
- `SavedRouteSuggestion`
  - `id`, `collectorId`, `status`, `fingerprint`, `assignedCollectionRequestIds`, `suggestion`, `createdAt`, `updatedAt`, `closedAt`.
- `SaveRouteSuggestionCommand`
  - `collectorId`, `source`, `RouteOptimizationResult suggestion`.
- `SavedRouteResult`
  - API-ready saved route representation.

Application layer:

- `SaveRouteSuggestionUseCase`
  - Validates save command.
  - Extracts assigned request ids from route stops.
  - Loads collection requests for validation/status evaluation.
  - Builds duplicate fingerprint.
  - Saves new route or throws duplicate conflict.
- `ListSavedRoutesUseCase`
  - Returns saved routes ordered by newest first.
- `CloseSavedRoutesUseCase`
  - Evaluates open saved routes after collection completion and closes any fully completed route.
- `SavedRoutePort`
  - Persistence boundary for saved route operations.

Infrastructure layer:

- `SavedRouteRepository`
  - MongoDB collection: `saved_routes`.
  - Insert saved route.
  - Find all sorted by `createdAt` descending.
  - Find by fingerprint.
  - Find open routes containing assigned collection request id.
  - Update route status to closed.
- `SavedRouteAdapter`
  - Implements `SavedRoutePort` and delegates to repository.

Presentation layer:

- Extend `CollectorRouteResource` with:
  - `POST /save` under `/collectors/routes`.
  - `GET /saved` under `/collectors/routes`.
- Map duplicate conflict to HTTP 409.
- Map validation failures to HTTP 400.
- Map unexpected failures to HTTP 500.

MongoDB Document Shape
----------------------

Collection: `saved_routes`

```json
{
  "_id": "saved-route-123",
  "collectorId": "collector-123",
  "status": "OPEN",
  "fingerprint": "sha256...",
  "assignedCollectionRequestIds": ["request-1", "request-2"],
  "suggestion": {
    "status": "FEASIBLE",
    "solver": {},
    "routes": [],
    "unassigned": []
  },
  "createdAt": "2026-05-12T10:00:00",
  "updatedAt": "2026-05-12T10:00:00",
  "closedAt": null
}
```

Suggested indexes:

```javascript
db.saved_routes.createIndex({ fingerprint: 1 }, { unique: true });
db.saved_routes.createIndex({ createdAt: -1 });
db.saved_routes.createIndex({ status: 1, assignedCollectionRequestIds: 1 });
db.saved_routes.createIndex({ collectorId: 1, createdAt: -1 });
```

Duplicate Strategy
------------------

MVP fingerprint input:

```text
collectorId + "|" + vehicleIndex/sequence/collectionRequestId tuples ordered by vehicleIndex then sequence
```

Rules:

- Only assigned route stops participate in the fingerprint.
- Unassigned stops and solver elapsed time do not participate.
- This blocks saving the same route ordering for the same collector more than once.
- Different route ordering for the same collection ids is allowed because it is operationally different.

Close Status Strategy
---------------------

Saved route status values:

- `OPEN`: At least one assigned collection request is not `COMPLETED`.
- `CLOSED`: Every assigned collection request is `COMPLETED`.

Close points:

- On save: evaluate current statuses and save as `CLOSED` if all assigned requests are already complete.
- After completion: `CompletionUseCase.tryMarkCompleted` should call `CloseSavedRoutesUseCase.closeRoutesContaining(requestId)` after the collection request status has been persisted as `COMPLETED`.

Validation
----------

Save request validation:

- `collectorId` is required.
- `suggestion` is required.
- `routes` must contain at least one route.
- Assigned stops must contain at least one collection request id.
- Stop sequence and collection request ids must be valid enough to fingerprint.
- All assigned collection request ids must exist.
- Assigned collection requests should belong to the collector through `selectedCollectorId` when that field is populated.

Testing Strategy
----------------

Unit tests:

- Save route succeeds and stores `OPEN` when at least one assigned request is not complete.
- Save route stores `CLOSED` when all assigned requests are complete.
- Duplicate fingerprint throws conflict.
- Empty assigned stops are rejected.
- List saved routes delegates to port and preserves sort contract.
- Close use case closes open routes only when all assigned requests are complete.
- Completion use case invokes close use case after marking request completed.

Repository tests:

- Mongo document mapping preserves route plans/stops/metadata.
- `findAll` sorts by `createdAt` descending.
- Duplicate fingerprint/index behavior is enforced or handled.

REST tests:

- `POST /collectors/routes/save` returns HTTP 201.
- Duplicate save maps to HTTP 409.
- Invalid save maps to HTTP 400.
- `GET /collectors/routes/saved` returns HTTP 200 and list shape.

Implementation Notes
--------------------

- Do not re-run route optimization during save.
- Do not mutate `CollectionRequest` records while saving.
- Keep saved route persistence independent from OR-Tools classes.
- Prefer storing the application route suggestion snapshot as structured Mongo document data, not a serialized string blob.
