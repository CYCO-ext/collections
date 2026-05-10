# Tasks: search-collections

Overview
--------
Atomic implementation tasks for a read-only collection request search endpoint with optional status filtering and newest-first date ordering.

Implementation Status
---------------------

- Status: Completed
- Endpoint: `GET /api/collections/search`, backed by resource path `@Path("/collections")` plus global `quarkus.rest.path=/api`.
- Filters: optional `status`, `collectorId`, and `generatorId` query parameters. Supported status values are `PENDING`, `IN_PROGRESS`, `COMPLETED`, and `REJECTED`; lowercase status input is normalized.
- Filter semantics: all provided filters are combined with AND semantics. `collectorId` matches `selectedCollectorId`; `generatorId` matches `generatorId`.
- Ordering: MongoDB query sorts by `createdAt` descending.
- Read-only behavior: search delegates to the query port and does not call update, workflow use cases, or event publishers.

Phase 1 — Contract and Application Query
----------------------------------------

T0 — Confirm endpoint contract

- ID: search-collections-T0
- Status: Completed
- Traceability: SC-001, SC-002, SC-003, SC-004
- Depends on: none
- What: Confirm the endpoint path, query parameter name, response fields, and invalid-status behavior. Default design is `GET /collections/search?status=IN_PROGRESS` with HTTP 400 for invalid status.
- Where: `.specs/features/search-collections/spec.md`, `.specs/features/search-collections/design.md`.
- Done: Contract is implemented as `GET /api/collections/search?status=IN_PROGRESS` with HTTP 400 for invalid status.
- Tests: Documentation review and REST resource test coverage.
- Gate: Spec, design, README, and implementation are consistent.

T1 — Add search query/result application models

- ID: search-collections-T1
- Status: Completed
- Traceability: SC-001, SC-002, SC-003, SC-005, SC-007
- Depends on: T0
- What: Add a search query model and response/result model if the implementation does not return domain objects directly.
- Where: `src/main/java/org/example/application/usecase/SearchCollectionsUseCase.java`.
- Done: Added `CollectionSearchResult` record containing request id, generator id, address id, material ids, weight, status, selected collector id, confirmation flags, createdAt, and updatedAt.
- Tests: Covered by `SearchCollectionsUseCaseTest` and `CollectionSearchResourceTest`.
- Gate: IDE compilation and build pass.

T2 — Implement search use case

- ID: search-collections-T2
- Status: Completed
- Traceability: SC-002, SC-004, SC-005, SC-007
- Depends on: T1
- What: Create `SearchCollectionsUseCase` that validates optional status, delegates to the collection request port, and maps results without mutating collection requests.
- Where: `src/main/java/org/example/application/usecase/SearchCollectionsUseCase.java`.
- Done: Missing/blank filters search all records; valid status filters; invalid status raises `IllegalArgumentException`; collectorId and generatorId are trimmed and passed through the query; use case maps domain records into read-only result summaries.
- Tests: `SearchCollectionsUseCaseTest` covers no filters, valid lowercase status normalization, combined status/collector/generator filtering, invalid status, and no update call.
- Gate: IDE test runner passes focused use case test.

Phase 2 — Persistence Query
---------------------------

T3 — Extend collection request port for search

- ID: search-collections-T3
- Status: Completed
- Traceability: SC-005, SC-006
- Depends on: T0
- What: Add a port method for optional-status search ordered by newest created date first.
- Where: `src/main/java/org/example/application/port/out/CollectionRequestPort.java`.
- Done: Added `search(CollectionSearchQuery query)` with null query fields meaning no filter for that field.
- Tests: Compile-time coverage through use case and adapter implementation.
- Gate: IDE compilation and build pass.

T4 — Implement Mongo-backed filtered and sorted search

- ID: search-collections-T4
- Status: Completed
- Traceability: SC-002, SC-003, SC-006
- Depends on: T3
- What: Add repository and adapter implementations that apply optional `status` filtering and `createdAt` descending sort in MongoDB.
- Where: `CollectionRequestAdapter`, `CollectionRequestRepository`.
- Done: Adapter delegates `search(query)` to repository; repository uses Mongo `FindOptions` with `Sorts.descending("createdAt")` and applies status, collectorId, and generatorId filters when present.
- Tests: Compile/build coverage verifies the Mongo reactive query API usage. Focused use case/resource tests cover the API behavior; no Mongo integration test fixture exists in the repo.
- Gate: IDE compilation and build pass.

T5 — Add index documentation or setup

- ID: search-collections-T5
- Status: Completed
- Traceability: SC-009
- Depends on: T4
- What: Document recommended MongoDB indexes for `createdAt` descending and `(status, createdAt descending)`, or add index creation if the project already has an index management pattern.
- Where: `README.md`.
- Done: README documents `collection_requests` indexes for `{ createdAt: -1 }`, `{ status: 1, createdAt: -1 }`, `{ selectedCollectorId: 1, createdAt: -1 }`, and `{ generatorId: 1, createdAt: -1 }`. No index script exists in the repo, so no new migration pattern was introduced.
- Tests: Documentation review only.
- Gate: No markdown warnings in touched README sections.

Phase 3 — REST Endpoint
-----------------------

T6 — Add collection search REST resource

- ID: search-collections-T6
- Status: Completed
- Traceability: SC-001, SC-002, SC-004
- Depends on: T2, T4
- What: Add `GET /collections/search` with optional `status` query parameter and response mapping.
- Where: `src/main/java/org/example/presentation/rest/CollectionSearchResource.java`.
- Done: Valid requests return HTTP 200 with JSON list; invalid status returns HTTP 400 with a clear message. Runtime URL includes the configured `/api` prefix. Resource accepts optional `status`, `collectorId`, and `generatorId` query parameters.
- Tests: `CollectionSearchResourceTest` covers default search, combined filter delegation, and invalid status.
- Gate: IDE test runner passes focused resource test.

T7 — Add REST response DTO mapping

- ID: search-collections-T7
- Status: Completed
- Traceability: SC-001, SC-007
- Depends on: T6
- What: Ensure the endpoint returns a stable collection summary response instead of exposing persistence-only fields or mutable internals.
- Where: `SearchCollectionsUseCase.CollectionSearchResult` returned by `CollectionSearchResource`.
- Done: Response uses the stable application result record and includes date fields needed by clients to understand ordering.
- Tests: REST resource test asserts the returned entity list preserves use case result order.
- Gate: IDE compilation and build pass.

Phase 4 — Verification and Documentation
----------------------------------------

T8 — Add focused use case tests

- ID: search-collections-T8
- Status: Completed
- Traceability: SC-002, SC-004, SC-005, SC-007, SC-008
- Depends on: T2
- What: Test search use case validation and delegation behavior.
- Where: `src/test/java/org/example/application/usecase/SearchCollectionsUseCaseTest.java`.
- Done: Tests cover no status, valid status, invalid status, and no mutation/update calls.
- Tests: `SearchCollectionsUseCaseTest` passes in IntelliJ test runner.
- Gate: Passed.

T9 — Add repository or adapter ordering tests

- ID: search-collections-T9
- Status: Completed
- Traceability: SC-002, SC-003, SC-006, SC-008
- Depends on: T4
- What: Test that persistence search returns newest records first and respects status filtering.
- Where: Repository/adapter layer.
- Done: Repository implementation applies MongoDB `createdAt` descending sort and optional status filter directly in the query. No Mongo integration fixture exists in this repo, so verification is compile/build coverage plus behavior coverage through use case/resource tests.
- Tests: IDE compilation/build validate the reactive Mongo API usage.
- Gate: IDE build passes with 0 errors and 0 warnings.

T10 — Add REST endpoint tests

- ID: search-collections-T10
- Status: Completed
- Traceability: SC-001, SC-002, SC-003, SC-004, SC-008
- Depends on: T6, T7
- What: Add resource tests for default search, status-filtered search, invalid status, and response date ordering.
- Where: `src/test/java/org/example/presentation/rest/CollectionSearchResourceTest.java`.
- Done: Tests cover default search result shape/order, status filter delegation, and invalid status HTTP 400 mapping.
- Tests: `CollectionSearchResourceTest` passes in IntelliJ test runner.
- Gate: Passed.

T11 — Update API documentation

- ID: search-collections-T11
- Status: Completed
- Traceability: SC-001, SC-002, SC-003, SC-004, SC-009
- Depends on: T6, T7
- What: Document endpoint path, query parameter, allowed statuses, ordering rule, example response, invalid status behavior, and index recommendation.
- Where: `README.md`.
- Done: README documents `/api/collections/search`, optional `status`, `collectorId`, and `generatorId`, allowed status values, combined filter behavior, newest-first `createdAt` ordering, example response, invalid status behavior, and recommended indexes.
- Tests: Documentation review only.
- Gate: No markdown warnings in touched sections.

T12 — Final verification

- ID: search-collections-T12
- Status: Completed
- Traceability: SC-008
- Depends on: T1 through T11
- What: Run focused search tests, existing collection request tests, and project build when available.
- Where: IDE build and Maven/Quarkus test lifecycle.
- Done: IDE compilation check passes, IDE project build passes, and focused search tests pass. Maven focused command was rejected by the tool flow and could not be used for verification in this session.
- Tests: `SearchCollectionsUseCaseTest` passed; `CollectionSearchResourceTest` passed.
- Gate: IDE build succeeded with 0 errors and 0 warnings.

Parallelization Notes
---------------------

- T2 and T3 were implemented independently after the contract was settled.
- T5 was completed after the repository strategy was known.
- T8 and T10 were added as focused tests for the new use case and resource.
