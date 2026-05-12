# Tasks: collection-by-id

Overview
--------
Atomic tasks for adding or verifying the read-only collection lookup endpoint `GET /api/collections/{id}`.

Implementation Status
---------------------

- Status: Completed
- Endpoint: `GET /api/collections/{id}`, backed by resource path `@Path("/collections")` plus global `quarkus.rest.path=/api`.
- Implementation: reused the existing `collection-id` code path instead of adding duplicate routes or use cases.
- Response: reuses `SearchCollectionsUseCase.CollectionSearchResult` so search and id lookup payloads stay aligned.
- Error behavior: blank ids fail with HTTP 400; missing collection requests fail with HTTP 404.
- Read-only behavior: lookup delegates to `CollectionRequestPort.findById(id)` and does not call update, workflow use cases, or event publishers.
- Verification: `GetCollectionByIdUseCaseTest`, `CollectionSearchResourceTest`, and IDE project build passed.

Phase 1 - Contract Alignment
----------------------------

T0 - Confirm canonical endpoint contract

- ID: collection-by-id-T0
- Status: Completed
- Traceability: CBID-001, CBID-002, CBID-004, CBID-005, CBID-007
- Depends on: none
- What: Confirm the runtime endpoint is `GET /api/collections/{id}`, the response matches search summary fields, blank ids return HTTP 400, missing ids return HTTP 404, and `/api/collections/search` remains the list search endpoint.
- Where: `.specs/features/collection-by-id/spec.md`, `.specs/features/collection-by-id/design.md`, `src/main/java/org/example/presentation/rest/CollectionSearchResource.java`.
- Done: Contract is implemented as `GET /api/collections/{id}`. The resource keeps `GET /collections/search` as an explicit route and exposes `GET /collections/{id}` for id lookup.
- Tests: `CollectionSearchResourceTest` covers search behavior, id success, validation, and not-found mapping.
- Gate: Passed with no duplicate collection-by-id endpoint introduced.

T1 - Reuse or consolidate existing collection-id implementation

- ID: collection-by-id-T1
- Status: Completed
- Traceability: CBID-001, CBID-003, CBID-004, CBID-006
- Depends on: T0
- What: Inspect existing `collection-id` implementation and decide whether to reuse it directly, rename docs, or add missing pieces only.
- Where: `src/main/java/org/example/application/usecase/GetCollectionByIdUseCase.java`, `src/main/java/org/example/presentation/rest/CollectionSearchResource.java`, `.specs/features/collection-id/`.
- Done: Existing implementation fully satisfies this feature. No duplicate use case, exception, route, or response model was added.
- Tests: Existing focused tests identified and run.
- Gate: Passed.

Phase 2 - Application Query
---------------------------

T2 - Provide collection-by-id result shape

- ID: collection-by-id-T2
- Status: Completed
- Traceability: CBID-004
- Depends on: T1
- What: Reuse the search collection summary result or add a cohesive equivalent with id, generatorId, addressId, materialIds, weight, status, selectedCollectorId, generatorConfirmed, collectorConfirmed, createdAt, and updatedAt.
- Where: `src/main/java/org/example/application/usecase/SearchCollectionsUseCase.java`, `src/main/java/org/example/application/usecase/GetCollectionByIdUseCase.java`.
- Done: `GetCollectionByIdUseCase` returns `SearchCollectionsUseCase.CollectionSearchResult`, keeping list and detail responses aligned.
- Tests: `GetCollectionByIdUseCaseTest` and `CollectionSearchResourceTest` cover the mapped result shape.
- Gate: IDE build passed.

T3 - Provide not-found behavior

- ID: collection-by-id-T3
- Status: Completed
- Traceability: CBID-005
- Depends on: T1
- What: Use or add an application exception for missing collection requests so REST can return HTTP 404.
- Where: `src/main/java/org/example/application/usecase/CollectionNotFoundException.java`.
- Done: Missing ids produce `Collection request not found: {id}` and REST maps the exception to HTTP 404.
- Tests: `GetCollectionByIdUseCaseTest` and `CollectionSearchResourceTest` verify not-found behavior.
- Gate: IDE build passed.

T4 - Implement or verify collection-by-id use case

- ID: collection-by-id-T4
- Status: Completed
- Traceability: CBID-002, CBID-003, CBID-004, CBID-005, CBID-006
- Depends on: T2, T3
- What: Implement or verify a use case that trims/validates id, calls `CollectionRequestPort.findById(id)`, maps found requests to summary response, and fails with not found when missing.
- Where: `src/main/java/org/example/application/usecase/GetCollectionByIdUseCase.java`.
- Done: Blank ids fail before persistence; existing ids return summary; missing ids fail with not found; no mutation paths are invoked.
- Tests: `GetCollectionByIdUseCaseTest` passed.
- Gate: IDE build passed.

Phase 3 - REST Endpoint
-----------------------

T5 - Implement or verify REST endpoint

- ID: collection-by-id-T5
- Status: Completed
- Traceability: CBID-001, CBID-002, CBID-004, CBID-005
- Depends on: T4
- What: Add or verify `GET /collections/{id}` in the collection query resource and map use case results/errors to HTTP responses.
- Where: `src/main/java/org/example/presentation/rest/CollectionSearchResource.java`.
- Done: Success returns HTTP 200; validation returns HTTP 400; missing collection returns HTTP 404.
- Tests: `CollectionSearchResourceTest` passed.
- Gate: IDE build passed.

T6 - Protect `/collections/search` route compatibility

- ID: collection-by-id-T6
- Status: Completed
- Traceability: CBID-007
- Depends on: T5
- What: Verify the search endpoint remains routed as list search and is not captured by the id path.
- Where: `src/main/java/org/example/presentation/rest/CollectionSearchResource.java`, `src/test/java/org/example/presentation/rest/CollectionSearchResourceTest.java`.
- Done: `/collections/search` remains explicitly declared and existing search tests pass alongside id endpoint tests.
- Tests: `CollectionSearchResourceTest` passed.
- Gate: Focused REST test suite passed.

Phase 4 - Verification and Documentation
----------------------------------------

T7 - Add or verify use case tests

- ID: collection-by-id-T7
- Status: Completed
- Traceability: CBID-002, CBID-003, CBID-004, CBID-005, CBID-006, CBID-008
- Depends on: T4
- What: Cover success, blank id validation, not found, full response mapping, and read-only behavior.
- Where: `src/test/java/org/example/application/usecase/GetCollectionByIdUseCaseTest.java`.
- Done: Tests cover success, blank id validation before repository call, missing id, mapped fields, and no update call.
- Tests: `GetCollectionByIdUseCaseTest` passed.
- Gate: Passed.

T8 - Add or verify REST tests

- ID: collection-by-id-T8
- Status: Completed
- Traceability: CBID-001, CBID-002, CBID-004, CBID-005, CBID-007, CBID-008
- Depends on: T5, T6
- What: Cover HTTP 200 response shape, HTTP 400 validation mapping, HTTP 404 not-found mapping, and search route compatibility.
- Where: `src/test/java/org/example/presentation/rest/CollectionSearchResourceTest.java`.
- Done: REST tests cover search behavior, id success, validation error mapping, and not-found mapping.
- Tests: `CollectionSearchResourceTest` passed.
- Gate: Passed.

T9 - Update API documentation

- ID: collection-by-id-T9
- Status: Completed
- Traceability: CBID-001, CBID-004, CBID-005, CBID-008
- Depends on: T5
- What: Document `GET /api/collections/{id}`, response fields, HTTP 400 validation, and HTTP 404 missing collection behavior.
- Where: `README.md`.
- Done: README documents the endpoint path, response example, blank id HTTP 400 behavior, and missing id HTTP 404 behavior.
- Tests: Documentation review only.
- Gate: Passed.

T10 - Final verification

- ID: collection-by-id-T10
- Status: Completed
- Traceability: CBID-008
- Depends on: T0 through T9
- What: Run focused use case tests, REST tests, and project build where available.
- Where: IDE test runner and project build.
- Done: Focused use case tests, REST tests, and IDE project build passed.
- Tests: `GetCollectionByIdUseCaseTest`, `CollectionSearchResourceTest`, and IDE project build.
- Gate: Build passed with 0 errors and 0 warnings.

Parallelization Notes
---------------------

- T1 determined that the existing `collection-id` implementation already satisfied the feature.
- T2 through T6 were verification tasks rather than code changes.
- T7 through T10 completed the focused test, documentation, and build verification gates.
