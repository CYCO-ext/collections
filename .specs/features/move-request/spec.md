# Feature: move-request

Problem
-------

Collectors can save route suggestions, but after a route is saved there is no API-level way to manually move one collection request from one vehicle route to another. If a dispatcher or collector needs to rebalance vehicle workload, fix an assignment, or adjust an operational plan, the only current option is to generate/save another suggestion or manipulate persisted data outside the application flow.

The service needs a controlled endpoint that moves a request between vehicles within an existing saved route and recalculates the affected vehicle stops so the saved route remains internally consistent.

Goal
----

Add an endpoint to transfer one assigned collection request from its current vehicle route to another vehicle route inside the same saved route suggestion. The client chooses the target vehicle only; the system calculates where the moved request best fits in that target vehicle route, then recalculates stop sequence, accumulated load, per-leg distance, total load, total distance, assigned request ids, fingerprint, and timestamps for the updated saved route.

Scope
-----

In scope:

- Endpoint to move one collection request between vehicles in a saved route.
- Lookup and validation for an existing saved route by id.
- Validation that the saved route is editable before movement.
- Validation that the collection request exists exactly once in the saved route's assigned stops.
- Validation that the target vehicle exists in the saved route.
- Automatic best-fit insertion inside the target vehicle route.
- Recalculation of source and target vehicle stop sequences after the move.
- Recalculation of accumulated load and vehicle total load after the move.
- Recalculation of distance from previous stop and vehicle total distance using the existing distance strategy.
- Update of saved route fingerprint and assigned collection request ids after the move.
- MongoDB persistence update for the modified saved route.
- REST error mapping for not found, invalid move, capacity violation, and duplicate route fingerprint conflicts.
- Tests for successful move, validation failures, automatic insertion, recalculation, persistence, and REST mapping.

Out of scope for the first implementation:

- Moving requests across different saved routes.
- Moving unassigned stops into a route.
- Creating new vehicles inside a saved route.
- Deleting vehicles from a saved route.
- Client-selected insertion sequence.
- Re-running full OR-Tools optimization after the manual move.
- Turn-by-turn navigation or road-network distance recalculation.
- Authorization scoping beyond the project's existing security approach.
- Updating `CollectionRequest.status`, selected collector, or completion state.

Constraints & Assumptions
-------------------------

- Runtime routes include the global `/api` prefix from `quarkus.rest.path=/api`.
- This feature operates on persisted saved route suggestions in MongoDB collection `saved_routes`.
- Suggested endpoint at resource level: `POST /collectors/routes/saved/{savedRouteId}/move-request`.
- Runtime URL: `POST /api/collectors/routes/saved/{savedRouteId}/move-request`.
- A saved route with status `CLOSED` is not editable.
- A move preserves the original `RouteStop` collection request id, address id, latitude, longitude, and demand.
- `sourceVehicleIndex` is optional because the service can locate the current vehicle from the saved route, but when provided it must match the current vehicle.
- `targetVehicleIndex` is required and must exist in the saved route.
- The request body must not include a target sequence; insertion order is calculated by the service.
- The best-fit insertion should choose the target vehicle position with the smallest additional route distance after inserting the moved stop.
- Vehicle capacity must still be respected after the move.
- Distances should be recalculated with the same existing Haversine distance model used by route suggestions.
- The route depot/start location is not currently persisted separately in saved route models. For MVP recalculation, use the first leg's existing distance where the depot cannot be reconstructed, and recalculate stop-to-stop distances for reordered stops. If exact depot recalculation is required, extend saved route persistence to store the depot before implementing movement.
- Duplicate fingerprint handling should remain deterministic. If the move produces a fingerprint already saved for the same collector, reject the operation with HTTP 409.

Acceptance Criteria
-------------------

AC-1: A client can call `POST /api/collectors/routes/saved/{savedRouteId}/move-request` with a collection request id and target vehicle index to move that request within the saved route.

AC-2: The endpoint returns HTTP 404 when the saved route id does not exist.

AC-3: The endpoint rejects moves against `CLOSED` saved routes with HTTP 400.

AC-4: The endpoint rejects a request id that is not assigned to exactly one stop in the saved route with HTTP 400.

AC-5: The endpoint rejects a target vehicle index that does not exist in the saved route with HTTP 400.

AC-6: The endpoint rejects a no-op move when the request is already assigned to the target vehicle with HTTP 400.

AC-7: The endpoint rejects a move that would exceed the target vehicle capacity with HTTP 400.

AC-8: When the move succeeds, the source vehicle no longer contains the moved stop and the target vehicle contains it at the best-fit position calculated by the service.

AC-9: The best-fit position is the insertion point with the lowest additional Haversine route distance among valid insertion points in the target vehicle.

AC-10: After a successful move, all affected vehicle stops have contiguous `sequence` values starting at 1.

AC-11: After a successful move, affected vehicle stops have recalculated `accumulatedLoad`, `distanceFromPreviousMeters`, `totalLoad`, and `totalDistanceMeters`.

AC-12: After a successful move, the saved route's `updatedAt`, `fingerprint`, `assignedCollectionRequestIds`, and suggestion payload are persisted.

AC-13: If the updated fingerprint conflicts with another saved route, the endpoint returns HTTP 409 and does not persist the move.

AC-14: Moving a request does not mutate any `CollectionRequest` document.

AC-15: Tests cover successful automatic best-fit move, missing route, closed route, missing request, invalid target vehicle, capacity violation, duplicate fingerprint conflict, recalculation, and REST status mapping.

Traceability IDs
----------------

- MR-001: Move request endpoint contract.
- MR-002: Saved route lookup and editability validation.
- MR-003: Move command and result models.
- MR-004: Assigned stop discovery and uniqueness validation.
- MR-005: Target vehicle validation.
- MR-006: Vehicle capacity validation.
- MR-007: Stop transfer and sequence recalculation.
- MR-008: Load and distance recalculation.
- MR-009: Saved route fingerprint refresh and duplicate conflict handling.
- MR-010: Saved route persistence update.
- MR-011: REST error mapping.
- MR-012: Read-only behavior for collection requests.
- MR-013: Focused unit, repository, and REST tests.
- MR-014: API documentation.
- MR-015: Automatic best-fit insertion calculation.

Notes
-----

- The feature intentionally performs a constrained manual route edit, not a full optimization pass across all vehicles.
- Only the moved request's placement inside the selected target vehicle is optimized.
- If later the product needs exact total distance from the depot after manual edits, saved route persistence should first include depot/start location in the saved route snapshot.
