# Feature: search-collections

Problem
-------
Operators, generators, and collectors need a simple way to browse collection requests without calling several status-specific endpoints or loading unsorted records. The service should expose a read-only search endpoint that can filter collection requests by status and return the most recent requests first.

Goal
----
Add a collection request search API that returns collection requests ordered by date descending, with optional filtering by `CollectionRequest.Status`, `collectorId`, and `generatorId`.

The endpoint should support the current lifecycle statuses: `PENDING`, `IN_PROGRESS`, `COMPLETED`, and `REJECTED`. When no filters are provided, it should return all collection requests, still ordered by most recent date first. When multiple filters are provided, all filters are combined with AND semantics.

Scope
-----

In scope:

- REST endpoint to search collection requests.
- Optional query parameters for status, collector, and generator filtering.
- Sorting by date with newest records first.
- Application use case dedicated to collection search.
- Repository/port method that performs filtering and sorting close to MongoDB.
- Response DTO or domain-safe output shape for collection request summaries.
- Validation and tests for status filtering, invalid status input, default all-status search, and date ordering.

Out of scope for the first implementation:

- Full text search.
- Filtering by material, location, or weight.
- Pagination unless added during implementation to match existing API conventions.
- Authorization scoping beyond the project's existing security approach.
- Mutating collection request state.

Constraints & Assumptions
-------------------------

- The service is Java 21 with Quarkus, Mutiny, and reactive MongoDB.
- `CollectionRequest` already stores `createdAt` and `updatedAt`.
- "Date most recent first" means descending by `createdAt` for search results. If future product needs prefer status-change recency, `updatedAt` can be added as an explicit sort option later.
- Status query values must match `CollectionRequest.Status` enum names. Lowercase values may be normalized if the existing REST style supports it, but invalid values must return HTTP 400.
- Search is read-only and must not change `CollectionRequest.status`, selected collector, confirmation flags, or timestamps.
- MongoDB should handle status, collector, generator filtering and descending date order instead of fetching all records and sorting in application memory.
- Add MongoDB index recommendations for `createdAt desc`, `(status, createdAt desc)`, `(selectedCollectorId, createdAt desc)`, and `(generatorId, createdAt desc)` to support the endpoint efficiently.

Acceptance Criteria
-------------------

AC-1: A client can call a new endpoint to search collection requests.

AC-2: When no `status` query parameter is provided, the endpoint returns all collection requests ordered by `createdAt` descending.

AC-3: When a valid `status` query parameter is provided, the endpoint returns only collection requests with that status, ordered by `createdAt` descending.

AC-4: Supported status filter values are `PENDING`, `IN_PROGRESS`, `COMPLETED`, and `REJECTED`.

AC-4a: When `collectorId` is provided, the endpoint returns only collection requests where `selectedCollectorId` matches the provided collector.

AC-4b: When `generatorId` is provided, the endpoint returns only collection requests where `generatorId` matches the provided generator.

AC-4c: When multiple filters are provided, all filters are applied together.

AC-5: Invalid status filter values return HTTP 400 with a clear validation message.

AC-6: The search use case delegates to the collection request port/repository and does not mutate returned collection requests.

AC-7: The repository query applies filtering and sorting in MongoDB.

AC-8: Tests cover all-status search ordering, filtered search ordering, invalid status validation, and REST response shape.

Traceability IDs
----------------

- SC-001: Collection search endpoint
- SC-002: Optional status filter
- SC-010: Optional collectorId filter
- SC-011: Optional generatorId filter
- SC-003: Descending created date ordering
- SC-004: Status validation and error handling
- SC-005: Application search use case
- SC-006: Mongo-backed filtered/sorted repository query
- SC-007: Read-only search behavior
- SC-008: Focused unit and REST test coverage
- SC-009: Index/documentation notes

Notes
-----

- Suggested endpoint: `GET /collections/search?status=IN_PROGRESS&collectorId=collector-1&generatorId=generator-1`.
- If the existing API style strongly prefers resource-specific prefixes, `GET /collectors/requests/search` or `GET /generators/requests/search` can be used instead, but the design should keep the use case independent from actor-specific routes.
