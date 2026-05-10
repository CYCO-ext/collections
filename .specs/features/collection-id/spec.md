# Feature: collection-id

Problem
-------
Clients can search collection requests as a list, but they also need a direct way to load one collection request when they already know its id. This is needed for details screens, workflow refreshes after state transitions, and links from notifications or route/search results.

Goal
----
Add a read-only endpoint to fetch a single collection request by id.

The endpoint should return the same collection request summary fields used by `search-collections`, but for exactly one collection request. If the id does not exist, the API should return HTTP 404 instead of a generic bad request or empty successful response.

Scope
-----

In scope:

- REST endpoint to fetch a collection request by id.
- Application use case dedicated to read-by-id behavior, or a cohesive extension of an existing query use case.
- Reuse the existing `CollectionRequestPort.findById(id)` persistence path.
- Stable response shape containing id, generatorId, addressId, materialIds, weight, status, selectedCollectorId, confirmation flags, createdAt, and updatedAt.
- Validation for missing/blank id.
- HTTP 404 behavior when the collection request is not found.
- Unit and REST tests for success, blank id validation, and not-found behavior.

Out of scope for the first implementation:

- Updating or mutating collection requests.
- Loading/enriching the full address object.
- Loading generator or collector profile details.
- Authorization scoping beyond the project's existing security approach.
- Expanding list search filters.

Constraints & Assumptions
-------------------------

- The service is Java 21 with Quarkus, Mutiny, and reactive MongoDB.
- `CollectionRequestPort.findById(id)` already exists and returns `null` when no document is found.
- Runtime routes include the global `/api` prefix from `quarkus.rest.path=/api`.
- The response should not expose persistence-only details beyond the collection request fields already documented for search.
- This endpoint is read-only and must not call `update`, workflow use cases, or event publishers.

Acceptance Criteria
-------------------

AC-1: A client can call `GET /api/collections/{id}` to fetch one collection request.

AC-2: When the collection request exists, the endpoint returns HTTP 200 with the collection request summary.

AC-3: The response includes id, generatorId, addressId, materialIds, weight, status, selectedCollectorId, generatorConfirmed, collectorConfirmed, createdAt, and updatedAt.

AC-4: When `{id}` is missing or blank after trimming, the application behavior fails validation with a clear message.

AC-5: When `{id}` does not match any collection request, the endpoint returns HTTP 404.

AC-6: The use case delegates to `CollectionRequestPort.findById(id)` and does not mutate collection requests.

AC-7: Tests cover successful lookup, not found, blank id validation, REST response shape, and REST 404 behavior.

Traceability IDs
----------------

- CID-001: Collection by id endpoint
- CID-002: Id validation
- CID-003: Existing collection request lookup through port
- CID-004: Stable collection summary response
- CID-005: Not-found HTTP 404 behavior
- CID-006: Read-only behavior
- CID-007: Focused unit and REST test coverage

Notes
-----

- Suggested endpoint: `GET /collections/{id}` at resource level, exposed as `GET /api/collections/{id}` at runtime.
- Reuse the same response field set as `search-collections` to keep clients consistent.
