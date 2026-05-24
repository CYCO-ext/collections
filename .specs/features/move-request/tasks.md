# Tasks: move-request

Overview
--------

Atomic implementation tasks for moving one assigned collection request between vehicles inside a saved route, automatically calculating its best-fit position in the target vehicle, recalculating affected route plans, and persisting the updated saved route snapshot.

Implementation Status
---------------------

- Status: Completed
- Endpoint: `POST /api/collectors/routes/saved/{savedRouteId}/move-request`.
- Request body: collection request id, optional source vehicle index, and target vehicle index only.
- Persistence: update existing `saved_routes` documents.
- Route generation: constrained manual edit only; no OR-Tools re-optimization in the MVP.
- Insertion behavior: system chooses the target vehicle insertion position with the lowest additional Haversine route distance.
- Distance recalculation: reuse Haversine stop-to-stop distance, with explicit handling for missing depot data.

Phase 1 — Contract and Models
-----------------------------

T0 — Confirm automatic move contract

- ID: move-request-T0
- Status: Completed
- Traceability: MR-001, MR-002, MR-005, MR-008, MR-015
- Depends on: none
- What: Confirm endpoint path, request body without target sequence, editable status rule, automatic best-fit insertion semantics, and depot-distance MVP behavior.
- Where: `.specs/features/move-request/spec.md`, `.specs/features/move-request/design.md`.
- Done: Contract is documented and implementation follows the documented behavior.
- Tests: Documentation review plus REST/use case test coverage.
- Gate: Spec, design, README, and implementation are consistent.

T1 — Add move command and validation exception models

- ID: move-request-T1
- Status: Completed
- Traceability: MR-003, MR-011
- Depends on: T0
- What: Add application model for moving a request inside a saved route and any typed validation exception needed for HTTP 400 mapping.
- Where: `src/main/java/org/example/application/route/SavedRouteModels.java` or a focused new route-edit model file; `src/main/java/org/example/application/usecase` for exception types if needed.
- Done: Command includes saved route id, collection request id, optional source vehicle index, and target vehicle index. It does not include target sequence.
- Tests: Covered by use case and REST tests.
- Gate: IDE compilation passes.

T2 — Extract or centralize saved route fingerprint generation

- ID: move-request-T2
- Status: Completed
- Traceability: MR-009
- Depends on: T1
- What: Extract the fingerprint logic currently used by save route so saving and moving use the same deterministic route-layout fingerprint.
- Where: Existing `SaveRouteSuggestionUseCase` and a new helper/service such as `SavedRouteFingerprintService`.
- Done: Save route and move route both compute identical fingerprints for identical collector/vehicle/sequence/request layouts.
- Tests: Fingerprint tests cover deterministic ordering and changed layout producing changed fingerprint.
- Gate: Existing save-route tests continue to pass.

Phase 2 — Persistence Port and Repository
-----------------------------------------

T3 — Extend saved route port for route updates

- ID: move-request-T3
- Status: Completed
- Traceability: MR-002, MR-009, MR-010
- Depends on: T1
- What: Add persistence operations needed to find a saved route by id, check duplicate fingerprint excluding the current saved route, and persist the updated saved route.
- Where: `src/main/java/org/example/application/port/out/SavedRoutePort.java`.
- Done: Application layer can load, duplicate-check, and update saved routes without depending on Mongo classes.
- Tests: Compile coverage plus use case mocks.
- Gate: IDE compilation passes.

T4 — Implement saved route repository update operations

- ID: move-request-T4
- Status: Completed
- Traceability: MR-002, MR-009, MR-010
- Depends on: T3
- What: Implement MongoDB repository and adapter support for the new saved route port methods.
- Where: `src/main/java/org/example/infrastructure/repository/SavedRouteRepository.java`, `src/main/java/org/example/application/adapter/SavedRouteAdapter.java`.
- Done: Saved route documents can be found by id, duplicate-checked by fingerprint excluding id, and updated after a move.
- Tests: Repository/adapter compile coverage; add mapping tests if available in the repo test style.
- Gate: IDE compilation passes.

Phase 3 — Move and Recalculation Logic
--------------------------------------

T5 — Add route plan recalculation helper

- ID: move-request-T5
- Status: Completed
- Traceability: MR-007, MR-008
- Depends on: T1
- What: Add a focused helper that rebuilds route stop sequence, accumulated load, per-leg distance, total load, and total distance for modified route plans.
- Where: `src/main/java/org/example/application/usecase` or `src/main/java/org/example/application/route` depending on existing project convention.
- Done: Given route plans after a stop transfer, affected plans return contiguous sequences and recalculated load/distance fields.
- Tests: Unit tests cover empty source vehicle, empty target vehicle, multi-stop recalculation, and preserved depot-distance handling.
- Gate: Focused helper test passes.

T6 — Add best-fit insertion calculator

- ID: move-request-T6
- Status: Completed
- Traceability: MR-008, MR-015
- Depends on: T5
- What: Add a helper that evaluates every valid insertion position in the target vehicle and chooses the position with the lowest additional Haversine route distance, using earliest sequence as deterministic tie-breaker.
- Where: `src/main/java/org/example/application/usecase` or `src/main/java/org/example/application/route` depending on existing project convention.
- Done: The calculator returns the correct insertion index without requiring a client-provided sequence.
- Tests: Unit tests cover inserting before first stop, between stops, after last stop, empty target route, and tie handling.
- Gate: Focused calculator test passes.

T7 — Implement move route request use case

- ID: move-request-T7
- Status: Completed
- Traceability: MR-002, MR-004, MR-005, MR-006, MR-007, MR-008, MR-009, MR-010, MR-012, MR-015
- Depends on: T2, T3, T5, T6
- What: Implement `MoveRouteRequestUseCase` to validate the saved route, locate the stop, validate target vehicle/capacity, calculate best-fit insertion, perform the move, recalculate plans, refresh fingerprint/assigned ids, reject duplicate layout, and persist the update.
- Where: `src/main/java/org/example/application/usecase/MoveRouteRequestUseCase.java`.
- Done: Successful moves return updated `SavedRouteResult`; invalid moves fail with typed exceptions; collection requests are not mutated.
- Tests: `MoveRouteRequestUseCaseTest` covers success and validation paths.
- Gate: Focused use case test passes.

T8 — Preserve saved route lifecycle behavior

- ID: move-request-T8
- Status: Completed
- Traceability: MR-002, MR-010, MR-012
- Depends on: T7
- What: Ensure moving a request does not reopen closed routes, close open routes, or change collection request statuses; closed routes are rejected before any persistence write.
- Where: `MoveRouteRequestUseCase`, saved route tests.
- Done: Lifecycle fields are preserved except `updatedAt`; closed route move attempts do not persist updates.
- Tests: Use case tests verify closed route rejection and no write on failure.
- Gate: Focused use case test passes.

Phase 4 — REST API
------------------

T9 — Add move request endpoint

- ID: move-request-T9
- Status: Completed
- Traceability: MR-001, MR-003, MR-011
- Depends on: T7
- What: Add `POST /collectors/routes/saved/{savedRouteId}/move-request` to map request DTO to command, delegate to use case, and return the updated saved route.
- Where: `src/main/java/org/example/presentation/rest/CollectorRouteResource.java`.
- Done: Request DTO accepts collection request id, optional source vehicle index, and target vehicle index only. Success returns HTTP 200 with updated saved route result.
- Tests: `CollectorRouteResourceTest` covers success response shape.
- Gate: Focused REST test passes.

T10 — Map REST errors for move request

- ID: move-request-T10
- Status: Completed
- Traceability: MR-011
- Depends on: T9
- What: Map saved route not found to HTTP 404, validation failures to HTTP 400, duplicate fingerprint to HTTP 409, and unexpected failures to HTTP 500.
- Where: `CollectorRouteResource` and exception classes.
- Done: Endpoint returns stable status codes and meaningful error messages.
- Tests: REST tests cover 404, 400, 409, and 500-style fallback where feasible.
- Gate: Focused REST test passes.

Phase 5 — Verification and Documentation
----------------------------------------

T11 — Add focused use case and helper tests

- ID: move-request-T11
- Status: Completed
- Traceability: MR-004, MR-005, MR-006, MR-007, MR-008, MR-009, MR-012, MR-013, MR-015
- Depends on: T5, T6, T7, T8
- What: Add unit tests for successful automatic best-fit move, missing route, closed route, request missing from route, duplicate request in route, wrong source vehicle, invalid target vehicle, request already in target vehicle, capacity violation, duplicate fingerprint, recalculation, and read-only collection behavior.
- Where: `src/test/java/org/example/application/usecase`.
- Done: Tests cover all core behavior without requiring MongoDB integration.
- Tests: Focused use case/helper tests pass.
- Gate: Focused test class passes.

T12 — Add REST endpoint tests

- ID: move-request-T12
- Status: Completed
- Traceability: MR-001, MR-011, MR-013
- Depends on: T9, T10
- What: Add resource tests for successful move, missing route, validation error, duplicate fingerprint conflict, and response mapping. Verify the request body does not require or use target sequence.
- Where: `src/test/java/org/example/presentation/rest/CollectorRouteResourceTest.java`.
- Done: REST tests verify endpoint path, request mapping, status codes, and updated route response.
- Tests: `CollectorRouteResourceTest` passes.
- Gate: Focused REST test passes.

T13 — Update API documentation

- ID: move-request-T13
- Status: Completed
- Traceability: MR-001, MR-014, MR-015
- Depends on: T9, T10
- What: Document the move request endpoint, request body without target sequence, automatic best-fit insertion behavior, validation behavior, response, status codes, and depot-distance MVP limitation.
- Where: `README.md`.
- Done: README route section explains how to move a request between vehicles in a saved route and how the system chooses the insertion position.
- Tests: Documentation review only.
- Gate: README and specs are consistent.

T14 — Final verification

- ID: move-request-T14
- Status: Completed
- Traceability: MR-013, MR-014
- Depends on: T11, T12, T13
- What: Run IDE build and focused tests for route use cases/resource tests, then update this task ledger with completion notes.
- Where: Project test/build tooling and `.specs/features/move-request/tasks.md`.
- Done: Build and focused tests pass, or any blockers are documented with exact failure details.
- Tests: IDE build; `MoveRouteRequestUseCaseTest`; `CollectorRouteResourceTest`; any focused helper tests.
- Gate: No compile errors; focused tests pass or documented blocker exists.
