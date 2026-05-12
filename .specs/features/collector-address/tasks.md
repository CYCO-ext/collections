# Tasks: collector-address

Overview
--------
Atomic implementation tasks for a read-only endpoint that retrieves a collector's address information.

Implementation Status
---------------------

- Status: Completed
- Endpoint: `GET /api/collectors/{collectorId}/address`, backed by resource path `@Path("/collectors")` plus global `quarkus.rest.path=/api`.
- Response: `GetCollectorAddressUseCase.CollectorAddressResult` with collector id, address id, address fields, coordinates, and enrichment metadata.
- Error behavior: blank collector ids fail with HTTP 400; missing collectors and collectors without address data fail with HTTP 404.
- Read-only behavior: lookup delegates to `CollectorDiscoveryPort.findCollectorById(collectorId)` and does not call update methods or event publishers.

Phase 1 — Contract and Application Query
----------------------------------------

T0 — Confirm endpoint contract

- ID: collector-address-T0
- Status: Completed
- Traceability: CAD-001, CAD-002, CAD-005, CAD-006
- Depends on: none
- What: Confirm endpoint path, response fields, validation behavior, and not-found behavior. Default design is `GET /collectors/{collectorId}/address`, exposed as `GET /api/collectors/{collectorId}/address` at runtime.
- Where: `.specs/features/collector-address/spec.md`, `.specs/features/collector-address/design.md`.
- Done: Contract is implemented as `GET /api/collectors/{collectorId}/address` with HTTP 400 for blank ids and HTTP 404 for missing collector/address data.
- Tests: Documentation review and REST resource test coverage.
- Gate: Spec, design, README, and implementation are consistent.

T1 — Add collector address result model

- ID: collector-address-T1
- Status: Completed
- Traceability: CAD-005
- Depends on: T0
- What: Add an application result model containing collectorId, addressId, street, number, city, state, zipCode, latitude, longitude, enrichmentStatus, and enrichmentSource.
- Where: `src/main/java/org/example/application/usecase/GetCollectorAddressUseCase.java`.
- Done: Added `CollectorAddressResult` record and return it from the use case/resource.
- Tests: Covered by `GetCollectorAddressUseCaseTest` and `CollectorResourceTest`.
- Gate: IDE compilation and build pass.

T2 — Add not-found exception handling

- ID: collector-address-T2
- Status: Completed
- Traceability: CAD-006
- Depends on: T0
- What: Add or reuse typed not-found exceptions for missing collector and missing collector address so REST maps those cases to HTTP 404.
- Where: `src/main/java/org/example/application/usecase/CollectorNotFoundException.java`, `src/main/java/org/example/application/usecase/CollectorAddressNotFoundException.java`.
- Done: Added typed exceptions with clear messages for missing collector and missing collector address.
- Tests: Unit/resource tests verify 404 mapping.
- Gate: IDE compilation and build pass.

T3 — Implement get collector address use case

- ID: collector-address-T3
- Status: Completed
- Traceability: CAD-002, CAD-003, CAD-004, CAD-005, CAD-006, CAD-007
- Depends on: T1, T2
- What: Add `GetCollectorAddressUseCase` that validates collector id, loads collector, resolves address, maps result, and fails with not-found when required data is missing.
- Where: `src/main/java/org/example/application/usecase/GetCollectorAddressUseCase.java`.
- Done: Blank ids fail validation before port calls; missing collector fails with 404-ready error; null address fails with 404-ready error; successful lookup returns address details; use case is read-only.
- Tests: `GetCollectorAddressUseCaseTest` covers success, blank id, collector not found, missing address, and result mapping.
- Gate: IDE test runner passes focused use case test.

Phase 2 — REST Endpoint
-----------------------

T4 — Add collector address REST endpoint

- ID: collector-address-T4
- Status: Completed
- Traceability: CAD-001, CAD-002, CAD-005, CAD-006
- Depends on: T3
- What: Add `GET /collectors/{collectorId}/address` and map use case results/errors to HTTP responses.
- Where: `src/main/java/org/example/presentation/rest/CollectorResource.java`.
- Done: Success returns HTTP 200 with address details; validation errors return HTTP 400; missing collector/address returns HTTP 404.
- Tests: `CollectorResourceTest` covers success, validation, collector not found, and missing address.
- Gate: IDE test runner passes focused resource test.

T5 — Keep route behavior clear

- ID: collector-address-T5
- Status: Completed
- Traceability: CAD-001
- Depends on: T4
- What: Ensure the new `/{collectorId}/address` route does not conflict with existing collector command routes such as `/requests/{requestId}/accept`, `/requests/{requestId}/reject`, and `/routes/suggest`.
- Where: REST resource tests and endpoint definitions.
- Done: `CollectorResourceTest` keeps command delegation coverage and `CollectorRouteResourceTest` still passes.
- Tests: `CollectorResourceTest` and `CollectorRouteResourceTest` pass.
- Gate: Focused REST tests pass.

Phase 3 — Verification and Documentation
----------------------------------------

T6 — Add focused use case tests

- ID: collector-address-T6
- Status: Completed
- Traceability: CAD-002, CAD-003, CAD-004, CAD-005, CAD-006, CAD-007, CAD-008
- Depends on: T3
- What: Test collector address use case behavior.
- Where: `src/test/java/org/example/application/usecase/GetCollectorAddressUseCaseTest.java`.
- Done: Tests cover existing collector with address, blank id, collector not found, missing address, and result mapping.
- Tests: `GetCollectorAddressUseCaseTest` passes in IntelliJ test runner.
- Gate: Passed.

T7 — Add REST endpoint tests

- ID: collector-address-T7
- Status: Completed
- Traceability: CAD-001, CAD-002, CAD-005, CAD-006, CAD-008
- Depends on: T4, T5
- What: Test the REST endpoint response status and shape.
- Where: `src/test/java/org/example/presentation/rest/CollectorResourceTest.java`.
- Done: Tests cover HTTP 200 success, HTTP 400 validation mapping, HTTP 404 collector not found, HTTP 404 address not found, and no regression to existing collector command delegation.
- Tests: `CollectorResourceTest` passes in IntelliJ test runner.
- Gate: Passed.

T8 — Update API documentation

- ID: collector-address-T8
- Status: Completed
- Traceability: CAD-001, CAD-005, CAD-006, CAD-009
- Depends on: T4
- What: Document endpoint path, response example, validation behavior, and 404 behavior.
- Where: `README.md`.
- Done: README documents `GET /api/collectors/{collectorId}/address`, response shape, blank collector id HTTP 400 behavior, and missing collector/address HTTP 404 behavior.
- Tests: Documentation review only.
- Gate: No documentation warnings in touched markdown.

T9 — Final verification

- ID: collector-address-T9
- Status: Completed
- Traceability: CAD-008
- Depends on: T1 through T8
- What: Run focused collector-address tests, existing collector route/resource tests, and project build when available.
- Where: IDE build and Maven/Quarkus test lifecycle.
- Done: IDE compilation check passes, focused collector-address tests pass, existing collector route tests pass, and IDE project build passes.
- Tests: `GetCollectorAddressUseCaseTest`, `CollectorResourceTest`, and `CollectorRouteResourceTest` passed.
- Gate: IDE build succeeded with 0 errors and 0 warnings.

Parallelization Notes
---------------------

- T1, T2, and T3 were implemented together because the result and exceptions are directly used by the use case.
- T7 includes route behavior coverage by keeping existing collector command delegation and collector route tests passing.
