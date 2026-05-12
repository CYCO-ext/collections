# Feature: collection-by-id

Problem
-------
Clients need a direct way to retrieve a single collection request when they already know its id. This supports details screens, notification links, workflow refreshes, and handoffs from search or route suggestion responses.

This feature is the canonical `collection-by-id` planning name for the same business capability previously captured as `collection-id`. Implementation should reuse or consolidate the existing `GET /api/collections/{id}` behavior rather than adding a second endpoint.

Goal
----
Add a read-only endpoint that searches for one collection request by id and returns the same collection summary shape used by collection search.

The endpoint must return HTTP 200 when the collection exists, HTTP 400 when the id is missing or blank after trimming, and HTTP 404 when no collection request exists for the provided id.

Scope
-----

In scope:

- REST endpoint to search one collection request by id.
- Application use case for id lookup, or reuse of the existing collection id query use case.
- Reuse `CollectionRequestPort.findById(id)` for persistence access.
- Stable response fields aligned with `search-collections`.
- Validation for missing or blank id.
- Not-found behavior mapped to HTTP 404.
- Unit and REST tests for success, validation, not found, and response shape.
- README/API documentation for the endpoint.

Out of scope:

- Mutating collection requests.
- Loading nested address, generator, collector, or saved-route details.
- Authorization or ownership scoping beyond the service's existing security model.
- New list filters or pagination.
- A second endpoint that duplicates `GET /api/collections/{id}`.

Constraints & Assumptions
-------------------------

- The service uses Java 21, Quarkus, Mutiny, and reactive MongoDB.
- Runtime REST paths include the configured global `/api` prefix.
- `CollectionRequestPort.findById(id)` is available and returns `null` when no collection request is found.
- The endpoint is read-only and must not call update operations, workflow transition use cases, or event publishers.
- `/collections/search` must continue to route to list search and must not be shadowed by `/collections/{id}`.

Acceptance Criteria
-------------------

AC-1: A client can call `GET /api/collections/{id}` to retrieve one collection request.

AC-2: Existing collection requests return HTTP 200 with id, generatorId, addressId, materialIds, weight, status, selectedCollectorId, generatorConfirmed, collectorConfirmed, createdAt, and updatedAt.

AC-3: Blank or whitespace-only ids fail validation with HTTP 400 and a clear message.

AC-4: Nonexistent ids return HTTP 404 with a clear not-found message.

AC-5: The lookup delegates to `CollectionRequestPort.findById(id)` and does not mutate the collection request.

AC-6: `/api/collections/search` remains available for list search.

AC-7: Focused use case and REST tests cover success, validation, not-found, response shape, and route conflict behavior.

Traceability IDs
----------------

- CBID-001: Collection by id endpoint
- CBID-002: Id validation
- CBID-003: Existing collection lookup through port
- CBID-004: Search-aligned summary response
- CBID-005: Not-found HTTP 404 behavior
- CBID-006: Read-only behavior
- CBID-007: Search route compatibility
- CBID-008: Focused tests and documentation
