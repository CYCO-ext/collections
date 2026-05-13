# Tasks: cancel-request

Overview
--------
Atomic implementation tasks for allowing generators and collectors to cancel collection requests.

Implementation Status
---------------------

- Status: Completed
- Domain: added `CANCELLED` to `CollectionRequest.Status` and added `canCancel()` for `PENDING` and `IN_PROGRESS`.
- Use case: added `CancelCollectionRequestUseCase` with generator and collector cancellation paths.
- REST: added `POST /api/generators/requests/{requestId}/cancel` and `POST /api/collectors/requests/{requestId}/cancel`.
- Events: `COLLECTION_CANCELLED` events include actor context through optional `actorType` and `actorId` fields.
- Error mapping: validation -> 400, forbidden actor -> 403, request not found -> 404, terminal status conflict -> 409.
- Search compatibility: `status=CANCELLED` is accepted by enum parsing.
- Route compatibility: route optimization tests still reject non-`IN_PROGRESS` candidates.
- Verification: focused use case tests, REST tests, search tests, route tests, and IDE project build passed.

Phase 1 - Contract and Lifecycle
--------------------------------

T0 - Confirm cancellation contract

- ID: cancel-request-T0
- Status: Completed
- Traceability: CAN-001, CAN-002, CAN-003, CAN-005, CAN-006, CAN-009
- Depends on: none
- What: Confirm endpoints, actor payloads, allowed statuses, terminal status behavior, and REST error mapping.
- Where: `.specs/features/cancel-request/spec.md`, `.specs/features/cancel-request/design.md`.
- Done: Contract is documented with generator and collector paths, request bodies, success behavior, and error responses.
- Tests: Documentation review.
- Gate: Passed.

T1 - Add CANCELLED status and cancel helper

- ID: cancel-request-T1
- Status: Completed
- Traceability: CAN-001, CAN-006, CAN-010
- Depends on: T0
- What: Add `CANCELLED` to `CollectionRequest.Status` and optionally add `canCancel()` for `PENDING` and `IN_PROGRESS`.
- Where: `src/main/java/org/example/domain/entity/CollectionRequest.java`.
- Done: Status enum supports `CANCELLED`; `canCancel()` allows `PENDING` and `IN_PROGRESS`; search status parsing accepts `CANCELLED`.
- Tests: `SearchCollectionsUseCaseTest` passed.
- Gate: IDE build passed.

Phase 2 - Application Behavior
------------------------------

T2 - Add cancellation exceptions

- ID: cancel-request-T2
- Status: Completed
- Traceability: CAN-009
- Depends on: T0
- What: Add application exceptions for request not found, forbidden actor, and terminal status conflicts, or reuse existing equivalents if present.
- Where: `src/main/java/org/example/application/usecase/CollectionRequestNotFoundException.java`, `src/main/java/org/example/application/usecase/CollectionCancellationForbiddenException.java`, `src/main/java/org/example/application/usecase/CollectionCancellationConflictException.java`.
- Done: Exceptions carry clear messages and are mapped by REST resources.
- Tests: `CancelCollectionRequestUseCaseTest`, `GeneratorResourceTest`, and `CollectorResourceTest` passed.
- Gate: IDE build passed.

T3 - Implement shared cancellation use case

- ID: cancel-request-T3
- Status: Completed
- Traceability: CAN-004, CAN-005, CAN-006, CAN-007, CAN-008
- Depends on: T1, T2
- What: Add `CancelCollectionRequestUseCase` with `cancelByGenerator` and `cancelByCollector`, validation, ownership checks, status transition checks, persistence, and event publication.
- Where: `src/main/java/org/example/application/usecase/CancelCollectionRequestUseCase.java`.
- Done: Both actor paths use the same transition logic and publish `COLLECTION_CANCELLED`.
- Tests: `CancelCollectionRequestUseCaseTest` passed.
- Gate: Focused use case tests passed.

T4 - Extend cancellation event payload

- ID: cancel-request-T4
- Status: Completed
- Traceability: CAN-008
- Depends on: T3
- What: Add optional `actorType` and `actorId` fields to `CollectionEvent` and populate them for cancellation events.
- Where: `src/main/java/org/example/infrastructure/event/CollectionEvent.java`, `src/main/java/org/example/application/usecase/CancelCollectionRequestUseCase.java`.
- Done: Existing event fields remain intact and cancellation events include actor context.
- Tests: `CancelCollectionRequestUseCaseTest` asserts actor fields.
- Gate: Event changes are additive and tests passed.

Phase 3 - REST Endpoints
------------------------

T5 - Add generator cancellation endpoint

- ID: cancel-request-T5
- Status: Completed
- Traceability: CAN-002, CAN-005, CAN-009
- Depends on: T3
- What: Add `POST /generators/requests/{requestId}/cancel` with body `{ "generatorId": "..." }`.
- Where: `src/main/java/org/example/presentation/rest/GeneratorResource.java`.
- Done: Endpoint delegates to `cancelByGenerator` and maps validation, forbidden, not-found, and conflict errors.
- Tests: `GeneratorResourceTest` passed.
- Gate: Focused REST tests passed.

T6 - Add collector cancellation endpoint

- ID: cancel-request-T6
- Status: Completed
- Traceability: CAN-003, CAN-005, CAN-009
- Depends on: T3
- What: Add `POST /collectors/requests/{requestId}/cancel` with body `{ "collectorId": "..." }`.
- Where: `src/main/java/org/example/presentation/rest/CollectorResource.java`.
- Done: Endpoint delegates to `cancelByCollector` and maps validation, forbidden, not-found, and conflict errors.
- Tests: `CollectorResourceTest` passed.
- Gate: Focused REST tests passed.

Phase 4 - Compatibility and Documentation
-----------------------------------------

T7 - Update search status compatibility tests

- ID: cancel-request-T7
- Status: Completed
- Traceability: CAN-001, CAN-010
- Depends on: T1
- What: Add or update search use case/resource tests proving `status=CANCELLED` is accepted.
- Where: `src/test/java/org/example/application/usecase/SearchCollectionsUseCaseTest.java`.
- Done: `status=cancelled` parses to `CollectionRequest.Status.CANCELLED` and delegates to collection search.
- Tests: `SearchCollectionsUseCaseTest` passed.
- Gate: Focused test passed.

T8 - Review route and saved-route compatibility

- ID: cancel-request-T8
- Status: Completed
- Traceability: CAN-011
- Depends on: T1, T3
- What: Verify canceled requests are excluded from route suggestions by existing `IN_PROGRESS` eligibility and record whether saved-route cancellation closure should be deferred.
- Where: `src/test/java/org/example/application/usecase/RouteOptimizationUseCaseTest.java`, `src/test/java/org/example/presentation/rest/CollectorRouteResourceTest.java`, `.specs/features/cancel-request/design.md`.
- Done: Existing route eligibility remains based on `IN_PROGRESS`; route tests passed. Saved-route cancellation closure remains documented as out of scope/follow-up.
- Tests: `RouteOptimizationUseCaseTest` and `CollectorRouteResourceTest` passed.
- Gate: Focused route tests passed.

T9 - Update README/API docs

- ID: cancel-request-T9
- Status: Completed
- Traceability: CAN-002, CAN-003, CAN-006, CAN-009, CAN-010, CAN-012
- Depends on: T5, T6, T7
- What: Document generator/collector cancel endpoints, request bodies, error behavior, and `CANCELLED` search status.
- Where: `README.md`.
- Done: README documents both cancellation endpoints, error behavior, and `CANCELLED` as a supported search status.
- Tests: Documentation review only.
- Gate: Passed.

T10 - Final verification

- ID: cancel-request-T10
- Status: Completed
- Traceability: CAN-012
- Depends on: T0 through T9
- What: Run focused use case tests, REST tests, search compatibility tests, relevant route tests if touched, and project build.
- Where: IDE test runner and project build.
- Done: All focused tests and build passed.
- Tests: `CancelCollectionRequestUseCaseTest`, `GeneratorResourceTest`, `CollectorResourceTest`, `SearchCollectionsUseCaseTest`, `RouteOptimizationUseCaseTest`, `CollectorRouteResourceTest`, and IDE project build.
- Gate: Build passed with zero compilation errors and warnings.

Parallelization Notes
---------------------

- T1 and T2 were completed before the shared use case.
- T5 and T6 were implemented after the use case stabilized.
- T7 and T8 verified compatibility after the new status and transition behavior were in place.
