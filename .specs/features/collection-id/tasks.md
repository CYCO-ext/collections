# Tasks: collection-id

Overview
--------
Atomic implementation tasks for a read-only endpoint that fetches one collection request by id.

Implementation Status
---------------------

- Status: Completed
- Endpoint: `GET /api/collections/{id}`, backed by resource path `@Path("/collections")` plus global `quarkus.rest.path=/api`.
- Response: reuses `SearchCollectionsUseCase.CollectionSearchResult` so list and detail payloads stay aligned.
- Error behavior: blank ids fail with HTTP 400; missing collection requests fail with HTTP 404.
- Read-only behavior: lookup delegates to `CollectionRequestPort.findById(id)` and does not call update, workflow use cases, or event publishers.

Phase 1 — Contract and Application Query
----------------------------------------

T0 — Confirm endpoint contract

- ID: collection-id-T0
- Status: Completed
- Traceability: CID-001, CID-002, CID-004, CID-005
- Depends on: none
- What: Confirm endpoint path, response fields, validation behavior, and not-found behavior. Default design is `GET /collections/{id}`, exposed as `GET /api/collections/{id}` at runtime.
- Where: `.specs/features/collection-id/spec.md`, `.specs/features/collection-id/design.md`.
- Done: Contract is implemented as `GET /api/collections/{id}` with HTTP 400 for blank ids and HTTP 404 for missing collection requests.
- Tests: Documentation review and REST resource test coverage.
- Gate: Spec, design, README, and implementation are consistent.

T1 — Add collection-by-id result model

- ID: collection-id-T1
- Status: Completed
- Traceability: CID-004
- Depends on: T0
- What: Add or reuse an application result model with the same fields as `search-collections`: id, generatorId, addressId, materialIds, weight, status, selectedCollectorId, generatorConfirmed, collectorConfirmed, createdAt, and updatedAt.
- Where: `src/main/java/org/example/application/usecase/SearchCollectionsUseCase.java`.
- Done: Reused `SearchCollectionsUseCase.CollectionSearchResult` for the id detail response.
- Tests: Covered by `GetCollectionByIdUseCaseTest` and `CollectionSearchResourceTest`.
- Gate: IDE compilation and build pass.

T2 — Add not-found exception

- ID: collection-id-T2
- Status: Completed
- Traceability: CID-005
- Depends on: T0
- What: Add a small application exception for missing collection requests so REST can map not found to HTTP 404 without treating it as a generic bad request.
- Where: `src/main/java/org/example/application/usecase/CollectionNotFoundException.java`.
- Done: Added `CollectionNotFoundException`, producing `Collection request not found: {id}`.
- Tests: Unit and resource tests verify not-found behavior and 404 mapping.
- Gate: IDE compilation and build pass.

T3 — Implement get collection by id use case

- ID: collection-id-T3
- Status: Completed
- Traceability: CID-002, CID-003, CID-004, CID-005, CID-006
- Depends on: T1, T2
- What: Add `GetCollectionByIdUseCase` that validates id, delegates to `CollectionRequestPort.findById(id)`, maps found requests to the result model, and fails with not-found when missing.
- Where: `src/main/java/org/example/application/usecase/GetCollectionByIdUseCase.java`.
- Done: Blank ids fail validation before repository call; existing ids return summary; missing ids fail with not-found; use case does not mutate collection requests.
- Tests: `GetCollectionByIdUseCaseTest` covers success, blank id, not found, result mapping, and no update call.
- Gate: IDE test runner passes focused use case test.

Phase 2 — REST Endpoint
-----------------------

T4 — Add collection by id REST endpoint

- ID: collection-id-T4
- Status: Completed
- Traceability: CID-001, CID-002, CID-004, CID-005
- Depends on: T3
- What: Add `GET /collections/{id}` to the collection query REST resource and map use case results/errors to HTTP responses.
- Where: `src/main/java/org/example/presentation/rest/CollectionSearchResource.java`.
- Done: Success returns HTTP 200 with summary; validation errors return HTTP 400; missing ids return HTTP 404.
- Tests: `CollectionSearchResourceTest` covers success, validation, and not found.
- Gate: IDE test runner passes focused resource test.

T5 — Keep route conflict behavior clear

- ID: collection-id-T5
- Status: Completed
- Traceability: CID-001
- Depends on: T4
- What: Ensure `GET /collections/search` remains routed to search and does not conflict with `GET /collections/{id}`.
- Where: REST resource tests and endpoint definitions.
- Done: Existing search endpoint test still passes while id endpoint tests prove normal ids route through `getById`.
- Tests: `CollectionSearchResourceTest` and `SearchCollectionsUseCaseTest` pass.
- Gate: Focused REST/search tests pass.

Phase 3 — Verification and Documentation
----------------------------------------

T6 — Add focused use case tests

- ID: collection-id-T6
- Status: Completed
- Traceability: CID-002, CID-003, CID-004, CID-005, CID-006, CID-007
- Depends on: T3
- What: Test the collection-by-id use case behavior.
- Where: `src/test/java/org/example/application/usecase/GetCollectionByIdUseCaseTest.java`.
- Done: Tests cover existing id, blank id, missing id, result mapping, and no update call.
- Tests: `GetCollectionByIdUseCaseTest` passes in IntelliJ test runner.
- Gate: Passed.

T7 — Add REST endpoint tests

- ID: collection-id-T7
- Status: Completed
- Traceability: CID-001, CID-002, CID-004, CID-005, CID-007
- Depends on: T4, T5
- What: Test the REST endpoint response status and shape.
- Where: `src/test/java/org/example/presentation/rest/CollectionSearchResourceTest.java`.
- Done: Tests cover HTTP 200 success, HTTP 400 validation mapping, HTTP 404 not-found mapping, and search route regression coverage.
- Tests: `CollectionSearchResourceTest` passes in IntelliJ test runner.
- Gate: Passed.

T8 — Update API documentation

- ID: collection-id-T8
- Status: Completed
- Traceability: CID-001, CID-004, CID-005
- Depends on: T4
- What: Document endpoint path, response example, validation behavior, and 404 behavior.
- Where: `README.md`.
- Done: README documents `GET /api/collections/{id}`, response shape, blank id HTTP 400 behavior, and missing id HTTP 404 behavior.
- Tests: Documentation review only.
- Gate: No documentation warnings in touched markdown.

T9 — Final verification

- ID: collection-id-T9
- Status: Completed
- Traceability: CID-007
- Depends on: T1 through T8
- What: Run focused collection-id tests, existing collection search tests, and project build when available.
- Where: IDE build and Maven/Quarkus test lifecycle.
- Done: IDE compilation check passes, focused collection-id/search tests pass, and IDE project build passes.
- Tests: `GetCollectionByIdUseCaseTest`, `CollectionSearchResourceTest`, and `SearchCollectionsUseCaseTest` passed.
- Gate: IDE build succeeded with 0 errors and 0 warnings.

Parallelization Notes
---------------------

- T1 reused the search collection response model.
- T2 and T3 were implemented together because the not-found exception is directly used by the use case.
- T7 includes the route conflict check by keeping `/collections/search` coverage in the same resource test class.
