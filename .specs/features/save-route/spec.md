# Feature: save-route

Problem
-------
Collectors can request optimized route suggestions, but suggestions are currently transient. Once the response is returned, the service has no durable record of which suggested route plan the collector intended to follow, cannot list previously saved route plans, and cannot prevent the same suggestion from being saved multiple times.

Operators and collector clients need route suggestions to be persisted so the app can show active saved routes, avoid duplicate saves, and close saved suggestions automatically once every collection request in the saved plan is completed.

Goal
----
Add saved route suggestion persistence.

The service should provide an endpoint to save a route suggestion, create a MongoDB collection for saved routes, block duplicate saved suggestions, expose an endpoint to list saved routes, and close saved route suggestions when all collection requests assigned to the saved route have status `COMPLETED`.

Scope
-----

In scope:

- MongoDB collection for saved route suggestions.
- Domain/application model for `SavedRouteSuggestion` with lifecycle status.
- Endpoint to save a route suggestion produced by route optimization.
- Endpoint to return all saved routes.
- Duplicate detection for equivalent saved suggestions.
- Saved route lifecycle status, at minimum `OPEN` and `CLOSED`.
- Close/update saved route status after save when all assigned collection requests are completed.
- Integration with collection completion flow so saved routes close after later collection completions.
- Tests for save, duplicate blocking, list, close-on-save, close-after-completion, and persistence mapping.

Out of scope for the first implementation:

- Editing saved routes.
- Deleting saved routes.
- Re-optimizing saved routes.
- Turn-by-turn navigation.
- Per-stop completion status separate from `CollectionRequest.status`.
- Historical route analytics or reporting.
- Authorization scoping beyond the project's existing security approach.

Constraints & Assumptions
-------------------------

- The service is Java 21 with Quarkus, Mutiny, and reactive MongoDB.
- Runtime routes include the global `/api` prefix from `quarkus.rest.path=/api`.
- Existing route suggestion response uses `RouteOptimizationResult`, `RoutePlan`, `RouteStop`, `SolverMetadata`, and `UnassignedRouteStop` application models.
- Only assigned route stops count toward closing a saved route. Unassigned stops do not block closure.
- A saved suggestion with zero assigned stops is invalid and should not be saved.
- Collection request ids present in route stops must exist and belong to the route collector before save succeeds.
- The saved route should be read-only with respect to collection requests: saving a route must not change `CollectionRequest.status`, selected collector, or confirmation fields.
- Duplicate detection should be deterministic and database-backed. Suggested MVP fingerprint: collector id plus normalized ordered route stop collection request ids grouped by vehicle/sequence.
- Listing all saved routes may be unpaginated for MVP, but should return newest saved routes first.
- Closing saved routes should be idempotent.

Acceptance Criteria
-------------------

AC-1: A client can call `POST /api/collectors/routes/save` with collector id and a route suggestion payload to persist it.

AC-2: Saving validates collector id, at least one route, at least one assigned stop, route stop collection request ids, and compatible request ownership/status assumptions.

AC-3: Saving creates a document in a MongoDB `saved_routes` collection containing collector id, route status, route plans, stops, solver metadata, unassigned stops, assigned collection request ids, duplicate fingerprint, createdAt, and updatedAt.

AC-4: A newly saved route has status `OPEN` unless every assigned collection request is already `COMPLETED`, in which case it is saved as `CLOSED`.

AC-5: A duplicate route suggestion for the same collector and same normalized route stop ordering is rejected with HTTP 409 Conflict.

AC-6: A client can call `GET /api/collectors/routes/saved` to return all saved routes ordered by `createdAt` descending.

AC-7: When a collection request is later completed, saved routes containing that collection request are evaluated and any route whose assigned requests are all `COMPLETED` is updated to `CLOSED`.

AC-8: Closing saved routes is idempotent; already closed routes remain closed without error.

AC-9: Saving and listing saved routes do not mutate collection request records.

AC-10: Tests cover successful save, duplicate rejection, invalid empty route, list ordering, close-on-save when all assigned requests are complete, close-after-completion, and REST response status mapping.

Traceability IDs
----------------

- SR-001: Save route suggestion endpoint
- SR-002: Saved route MongoDB collection
- SR-003: Saved route document/model
- SR-004: Save request validation
- SR-005: Duplicate suggestion fingerprint and conflict behavior
- SR-006: Get all saved routes endpoint
- SR-007: Saved route status lifecycle
- SR-008: Close route after all assigned collections complete
- SR-009: Completion flow integration
- SR-010: Read-only behavior for collection requests
- SR-011: Focused unit, repository, and REST test coverage
- SR-012: API and index documentation

Notes
-----

- Suggested save endpoint: `POST /collectors/routes/save` at resource level, exposed as `POST /api/collectors/routes/save`.
- Suggested list endpoint: `GET /collectors/routes/saved` at resource level, exposed as `GET /api/collectors/routes/saved`.
- Suggested MongoDB collection name: `saved_routes`.
- Suggested saved route statuses: `OPEN`, `CLOSED`.
