# Tasks: save-route

Overview
--------
Atomic implementation tasks for persisting route suggestions, listing saved routes, blocking duplicates, and closing saved route suggestions when all assigned collection requests are completed.

Implementation Status
---------------------

- Status: Completed
- Save endpoint: `POST /api/collectors/routes/save`, backed by `POST /collectors/routes/save` in `CollectorRouteResource`.
- List endpoint: `GET /api/collectors/routes/saved`, backed by `GET /collectors/routes/saved` in `CollectorRouteResource`.
- MongoDB collection: `saved_routes`.
- Status lifecycle: `OPEN` when at least one assigned request is not completed; `CLOSED` when every assigned request is `COMPLETED`.
- Duplicate blocking: deterministic SHA-256 fingerprint from collector id plus ordered vehicle/sequence/collection request stop tuples.
- Completion integration: `CompletionUseCase` invokes `CloseSavedRoutesUseCase` after a collection request is persisted as `COMPLETED` and the completion event is published.

Phase 1 — Contract and Domain Model
-----------------------------------

T0 — Confirm endpoint contract and lifecycle rules

- ID: save-route-T0
- Status: Completed
- Traceability: SR-001, SR-006, SR-007, SR-008
- Depends on: none
- What: Confirm `POST /collectors/routes/save`, `GET /collectors/routes/saved`, saved route status values, duplicate semantics, and close-after-completion behavior.
- Where: `.specs/features/save-route/spec.md`, `.specs/features/save-route/design.md`.
- Done: Contract and lifecycle are implemented and documented.
- Tests: Documentation review and REST/use case test coverage.
- Gate: Spec, design, README, and implementation are consistent.

T1 — Add saved route domain/application models

- ID: save-route-T1
- Status: Completed
- Traceability: SR-003, SR-007
- Depends on: T0
- What: Add saved route status enum and models for saved route entity/result, save command, assigned collection ids, route snapshot, timestamps, and fingerprint.
- Where: `src/main/java/org/example/application/route/SavedRouteModels.java`.
- Done: Added `SavedRouteStatus`, `SavedRouteSuggestion`, `SaveRouteSuggestionCommand`, and `SavedRouteResult` without MongoDB or REST dependencies.
- Tests: Covered by save/list/close use case tests.
- Gate: IDE compilation and build pass.

T2 — Add saved route port

- ID: save-route-T2
- Status: Completed
- Traceability: SR-002, SR-003, SR-005, SR-006, SR-008
- Depends on: T1
- What: Define `SavedRoutePort` with methods to save, find by fingerprint, list all newest first, find open routes containing collection request id, and close a route.
- Where: `src/main/java/org/example/application/port/out/SavedRoutePort.java`.
- Done: Application use cases depend only on `SavedRoutePort`.
- Tests: Covered by mocked use case tests and repository adapter compile/build coverage.
- Gate: IDE compilation and build pass.

T3 — Add duplicate conflict exception

- ID: save-route-T3
- Status: Completed
- Traceability: SR-005
- Depends on: T0
- What: Add a typed exception for duplicate saved route suggestions so REST maps duplicates to HTTP 409.
- Where: `src/main/java/org/example/application/usecase/DuplicateSavedRouteException.java`.
- Done: Duplicate save attempts throw `DuplicateSavedRouteException` with `Route suggestion already saved`.
- Tests: `SaveRouteSuggestionUseCaseTest` and `CollectorRouteResourceTest` verify conflict behavior.
- Gate: IDE compilation and build pass.

Phase 2 — Save and List Use Cases
---------------------------------

T4 — Implement save route suggestion use case

- ID: save-route-T4
- Status: Completed
- Traceability: SR-001, SR-004, SR-005, SR-007, SR-010
- Depends on: T1, T2, T3
- What: Implement `SaveRouteSuggestionUseCase` to validate command, extract assigned request ids, load collection requests, build fingerprint, block duplicates, determine `OPEN`/`CLOSED`, and persist saved route.
- Where: `src/main/java/org/example/application/usecase/SaveRouteSuggestionUseCase.java`.
- Done: Valid route suggestions save; duplicates fail with conflict; invalid empty assigned stops fail validation; all-completed routes save as `CLOSED`; saving does not mutate collection requests.
- Tests: `SaveRouteSuggestionUseCaseTest` covers open save, close-on-save, duplicate rejection, invalid empty stops, and read-only behavior.
- Gate: Focused use case test passes.

T5 — Implement list saved routes use case

- ID: save-route-T5
- Status: Completed
- Traceability: SR-006
- Depends on: T1, T2
- What: Implement `ListSavedRoutesUseCase` to return all saved routes ordered newest first through the saved route port.
- Where: `src/main/java/org/example/application/usecase/ListSavedRoutesUseCase.java`.
- Done: Use case delegates to `SavedRoutePort.findAllOrderByCreatedAtDesc()` and maps to `SavedRouteResult`.
- Tests: `ListSavedRoutesUseCaseTest` passes.
- Gate: Focused use case test passes.

T6 — Implement close saved routes use case

- ID: save-route-T6
- Status: Completed
- Traceability: SR-007, SR-008, SR-009
- Depends on: T1, T2
- What: Implement `CloseSavedRoutesUseCase.closeRoutesContaining(requestId)` to find open saved routes containing the completed request, load assigned collection requests, and close routes whose assigned requests are all `COMPLETED`.
- Where: `src/main/java/org/example/application/usecase/CloseSavedRoutesUseCase.java`.
- Done: Fully completed routes close idempotently; partially completed routes remain open.
- Tests: `CloseSavedRoutesUseCaseTest` covers full close and partial-open behavior.
- Gate: Focused use case test passes.

Phase 3 — MongoDB Persistence
-----------------------------

T7 — Add saved route repository and adapter

- ID: save-route-T7
- Status: Completed
- Traceability: SR-002, SR-003, SR-005, SR-006, SR-008
- Depends on: T1, T2
- What: Implement MongoDB repository/adapter for `saved_routes`, including document mapping for route plans, stops, solver metadata, unassigned stops, timestamps, fingerprint, and status.
- Where: `src/main/java/org/example/infrastructure/repository/SavedRouteRepository.java`, `src/main/java/org/example/application/adapter/SavedRouteAdapter.java`.
- Done: Saved route persistence supports save, find by fingerprint, list newest first, find open by request id, and close update.
- Tests: Compile/build coverage verifies the reactive Mongo API usage. No Mongo integration fixture exists in this repo.
- Gate: IDE compilation and build pass.

T8 — Add MongoDB index documentation or setup

- ID: save-route-T8
- Status: Completed
- Traceability: SR-002, SR-005, SR-012
- Depends on: T7
- What: Document or create indexes for unique fingerprint, newest-first listing, open route lookup by assigned request id, and collector newest-first lookup.
- Where: `README.md`.
- Done: README documents `saved_routes` indexes for unique fingerprint, `createdAt`, `(status, assignedCollectionRequestIds)`, and `(collectorId, createdAt)`.
- Tests: Documentation review only.
- Gate: No documentation warnings in touched markdown.

Phase 4 — REST API
------------------

T9 — Add save route endpoint

- ID: save-route-T9
- Status: Completed
- Traceability: SR-001, SR-004, SR-005
- Depends on: T4
- What: Add `POST /collectors/routes/save` to accept collector id and route suggestion payload, delegate to save use case, and map responses/errors.
- Where: `src/main/java/org/example/presentation/rest/CollectorRouteResource.java`.
- Done: Success returns HTTP 201; duplicate returns HTTP 409; validation errors return HTTP 400.
- Tests: `CollectorRouteResourceTest` covers success, duplicate, and invalid body mapping.
- Gate: Focused REST test passes.

T10 — Add list saved routes endpoint

- ID: save-route-T10
- Status: Completed
- Traceability: SR-006
- Depends on: T5
- What: Add `GET /collectors/routes/saved` to return all saved routes ordered newest first.
- Where: `src/main/java/org/example/presentation/rest/CollectorRouteResource.java`.
- Done: Endpoint returns HTTP 200 with saved route list response.
- Tests: `CollectorRouteResourceTest` covers list response and delegation.
- Gate: Focused REST test passes.

Phase 5 — Completion Integration
--------------------------------

T11 — Integrate saved route closure with completion flow

- ID: save-route-T11
- Status: Completed
- Traceability: SR-008, SR-009
- Depends on: T6
- What: Update `CompletionUseCase` so after a collection request is persisted as `COMPLETED`, saved routes containing that request are evaluated and closed when all assigned requests are completed.
- Where: `src/main/java/org/example/application/usecase/CompletionUseCase.java`.
- Done: Completion still publishes completion event and now invokes saved route closure after completion persistence and event publication.
- Tests: `CompletionUseCaseTest` verifies closure is invoked after completed status is saved.
- Gate: Focused completion tests pass.

Phase 6 — Verification and Documentation
----------------------------------------

T12 — Add focused save/list/close use case tests

- ID: save-route-T12
- Status: Completed
- Traceability: SR-004, SR-005, SR-006, SR-007, SR-008, SR-010, SR-011
- Depends on: T4, T5, T6
- What: Add unit tests for save, duplicate, invalid save, list, close-on-save, close-after-completion logic, and read-only behavior.
- Where: `src/test/java/org/example/application/usecase`.
- Done: Added focused use case tests for save, list, and close logic.
- Tests: `SaveRouteSuggestionUseCaseTest`, `ListSavedRoutesUseCaseTest`, and `CloseSavedRoutesUseCaseTest` pass.
- Gate: Passed.

T13 — Add REST endpoint tests

- ID: save-route-T13
- Status: Completed
- Traceability: SR-001, SR-005, SR-006, SR-011
- Depends on: T9, T10
- What: Add resource tests for save success, duplicate conflict, invalid input, and saved route listing.
- Where: `src/test/java/org/example/presentation/rest/CollectorRouteResourceTest.java`.
- Done: REST tests cover save/list status mapping and response shape.
- Tests: `CollectorRouteResourceTest` passes.
- Gate: Passed.

T14 — Update API documentation

- ID: save-route-T14
- Status: Completed
- Traceability: SR-001, SR-002, SR-005, SR-006, SR-007, SR-012
- Depends on: T9, T10, T11
- What: Document save endpoint, list endpoint, saved route status lifecycle, duplicate behavior, Mongo collection, and indexes.
- Where: `README.md`.
- Done: README documents save/list endpoints, duplicate behavior, saved route lifecycle, and `saved_routes` index recommendations.
- Tests: Documentation review only.
- Gate: No documentation warnings in touched markdown.

T15 — Final verification

- ID: save-route-T15
- Status: Completed
- Traceability: SR-011
- Depends on: T1 through T14
- What: Run focused saved route tests, existing route optimization tests, completion tests, and project build when available.
- Where: IDE build and Maven/Quarkus test lifecycle.
- Done: IDE compilation check passes, focused saved-route tests pass, route resource tests pass, completion tests pass, and IDE project build passes.
- Tests: `SaveRouteSuggestionUseCaseTest`, `ListSavedRoutesUseCaseTest`, `CloseSavedRoutesUseCaseTest`, `CollectorRouteResourceTest`, and `CompletionUseCaseTest` passed.
- Gate: IDE build succeeded with 0 errors and 0 warnings.

Parallelization Notes
---------------------

- T1, T2, and T3 were implemented first to stabilize the model/port boundary.
- T4, T5, and T6 were implemented with mocked ports before REST/persistence verification.
- T7 was implemented against the port contract and verified by compilation/build due to no Mongo integration fixture.
- T11 was implemented after close-route logic was covered by tests.
