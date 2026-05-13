# Tasks: delete-suggestion

Overview
--------
Atomic implementation tasks for deleting a saved route suggestion by id.

Implementation Status
---------------------

- Status: Completed
- Endpoint: `DELETE /api/collectors/routes/saved/{savedRouteId}`.
- Success behavior: existing saved route suggestions are physically deleted and return HTTP 204 No Content.
- Error behavior: blank ids return HTTP 400; unknown saved route ids return HTTP 404; unexpected failures return HTTP 500.
- Persistence: `SavedRouteRepository.deleteById` deletes from MongoDB `saved_routes` by `_id` and reports whether a document was removed.
- Verification: `DeleteSavedRouteSuggestionUseCaseTest`, `CollectorRouteResourceTest`, `ListSavedRoutesUseCaseTest`, `SaveRouteSuggestionUseCaseTest`, and IDE project build passed.

Phase 1 - Contract
------------------

T0 - Confirm delete endpoint contract

- ID: delete-suggestion-T0
- Status: Completed
- Traceability: DSUG-001, DSUG-002, DSUG-005
- Depends on: none
- What: Confirm endpoint path, success status, validation behavior, and not-found behavior.
- Where: `.specs/features/delete-suggestion/spec.md`, `.specs/features/delete-suggestion/design.md`.
- Done: Contract uses `DELETE /api/collectors/routes/saved/{savedRouteId}`, HTTP 204 on success, HTTP 400 for blank ids, and HTTP 404 for unknown ids.
- Tests: Documentation review.
- Gate: Passed.

Phase 2 - Application and Persistence
-------------------------------------

T1 - Add not-found exception

- ID: delete-suggestion-T1
- Status: Completed
- Traceability: DSUG-005
- Depends on: T0
- What: Add a saved route suggestion not-found exception with a clear message.
- Where: `src/main/java/org/example/application/usecase/SavedRouteSuggestionNotFoundException.java`.
- Done: Exception message is `Saved route suggestion not found: {savedRouteId}`.
- Tests: `DeleteSavedRouteSuggestionUseCaseTest` and `CollectorRouteResourceTest` passed.
- Gate: IDE build passed.

T2 - Extend saved route port

- ID: delete-suggestion-T2
- Status: Completed
- Traceability: DSUG-003
- Depends on: T0
- What: Add `deleteById(String savedRouteId)` to `SavedRoutePort`, returning whether a document was deleted.
- Where: `src/main/java/org/example/application/port/out/SavedRoutePort.java`.
- Done: Port exposes a delete operation with a boolean existence result.
- Tests: Focused tests and IDE build passed.
- Gate: IDE build passed.

T3 - Implement repository delete

- ID: delete-suggestion-T3
- Status: Completed
- Traceability: DSUG-004
- Depends on: T2
- What: Add MongoDB delete support by `_id` in the saved route repository.
- Where: `src/main/java/org/example/infrastructure/repository/SavedRouteRepository.java`.
- Done: `deleteOne(Filters.eq("_id", savedRouteId))` returns true when `deletedCount > 0`.
- Tests: Build verification.
- Gate: IDE build passed.

T4 - Wire saved route adapter

- ID: delete-suggestion-T4
- Status: Completed
- Traceability: DSUG-003, DSUG-004
- Depends on: T2, T3
- What: Delegate `SavedRoutePort.deleteById` to the repository.
- Where: `src/main/java/org/example/application/adapter/SavedRouteAdapter.java`.
- Done: Adapter implements the new port method.
- Tests: Build verification.
- Gate: IDE build passed.

T5 - Implement delete use case

- ID: delete-suggestion-T5
- Status: Completed
- Traceability: DSUG-002, DSUG-003, DSUG-005, DSUG-006
- Depends on: T1, T2
- What: Add `DeleteSavedRouteSuggestionUseCase` with id validation, port delegation, and not-found conversion.
- Where: `src/main/java/org/example/application/usecase/DeleteSavedRouteSuggestionUseCase.java`.
- Done: Use case trims ids, fails blank ids before port call, throws not-found when delete returns false, and does not touch collection requests.
- Tests: `DeleteSavedRouteSuggestionUseCaseTest` passed.
- Gate: Focused use case tests passed.

Phase 3 - REST and Tests
------------------------

T6 - Add REST delete endpoint

- ID: delete-suggestion-T6
- Status: Completed
- Traceability: DSUG-001, DSUG-002, DSUG-005
- Depends on: T5
- What: Add `DELETE /collectors/routes/saved/{savedRouteId}` to `CollectorRouteResource`.
- Where: `src/main/java/org/example/presentation/rest/CollectorRouteResource.java`.
- Done: Success returns HTTP 204; validation maps to 400; not found maps to 404; unexpected errors map to 500.
- Tests: `CollectorRouteResourceTest` passed.
- Gate: Focused REST tests passed.

T7 - Add use case tests

- ID: delete-suggestion-T7
- Status: Completed
- Traceability: DSUG-002, DSUG-003, DSUG-005, DSUG-006, DSUG-007
- Depends on: T5
- What: Test success, trimming, blank id validation, unknown id, and port failure propagation.
- Where: `src/test/java/org/example/application/usecase/DeleteSavedRouteSuggestionUseCaseTest.java`.
- Done: Use case behavior is covered without relying on REST.
- Tests: `DeleteSavedRouteSuggestionUseCaseTest` passed.
- Gate: Focused test passed.

T8 - Update REST tests

- ID: delete-suggestion-T8
- Status: Completed
- Traceability: DSUG-001, DSUG-002, DSUG-005, DSUG-007
- Depends on: T6
- What: Add REST resource tests for delete success and error mapping.
- Where: `src/test/java/org/example/presentation/rest/CollectorRouteResourceTest.java`.
- Done: Resource test covers 204, 400, 404, and 500 paths.
- Tests: `CollectorRouteResourceTest` passed.
- Gate: Focused REST test passed.

Phase 4 - Documentation and Verification
----------------------------------------

T9 - Update README/API docs

- ID: delete-suggestion-T9
- Status: Completed
- Traceability: DSUG-001, DSUG-002, DSUG-005, DSUG-007
- Depends on: T6
- What: Document delete saved route endpoint and error behavior.
- Where: `README.md`.
- Done: README includes `DELETE /api/collectors/routes/saved/{savedRouteId}`, HTTP 204 success, and 400/404 behavior.
- Tests: Documentation review only.
- Gate: Passed.

T10 - Final verification

- ID: delete-suggestion-T10
- Status: Completed
- Traceability: DSUG-007
- Depends on: T0 through T9
- What: Run focused use case tests, REST tests, saved route tests, and project build.
- Where: IDE test runner and project build.
- Done: All focused tests and build passed.
- Tests: `DeleteSavedRouteSuggestionUseCaseTest`, `CollectorRouteResourceTest`, `ListSavedRoutesUseCaseTest`, `SaveRouteSuggestionUseCaseTest`, and IDE project build.
- Gate: Build passed with 0 errors and 0 warnings.

Parallelization Notes
---------------------

- T1 and T2 were completed before the use case.
- T3 and T4 wired persistence before REST.
- T7 and T8 completed focused verification around the use case and endpoint.
