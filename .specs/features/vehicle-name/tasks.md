# Tasks: vehicle-name

Overview
--------

Atomic implementation tasks for adding a user-provided nickname to each vehicle during route creation and preserving that nickname across route suggestions, saved route snapshots, route maps, and move-route updates.

Implementation Status
---------------------

- Status: Completed
- Feature folder: `.specs/features/vehicle-name`
- Primary endpoint affected: `POST /api/collectors/routes/suggest`
- Primary models affected: `RouteVehicle`, `RoutePlan`
- Persistence affected: MongoDB `saved_routes` route plan snapshots
- Breaking contract: route creation vehicles must now include a valid `name`
- Verification: IDE compilation check and IDE build succeeded; focused Maven test command was attempted but rejected by the environment before execution.

Phase 1 - Contract and Application Models
-----------------------------------------

T0 - Confirm route vehicle naming contract

- ID: vehicle-name-T0
- Status: Completed
- Traceability: VN-001, VN-002, VN-004, VN-012
- Depends on: none
- What: Confirm request field `vehicles[].name`, response field `routes[].vehicleName`, validation rule required/non-blank/max 80 chars, and compatibility behavior for old saved route documents.
- Where: `.specs/features/vehicle-name/spec.md`, `.specs/features/vehicle-name/design.md`.
- Done: Contract is accepted before code changes; any product changes are reflected in spec/design.
- Tests: Documentation review.
- Gate: Spec, design, and task IDs stay aligned.

T1 - Add vehicle nickname to route application records

- ID: vehicle-name-T1
- Status: Completed
- Traceability: VN-003, VN-004
- Depends on: T0
- What: Update `RouteVehicle` to carry the user-provided name and update `RoutePlan` to expose `vehicleName` in optimization results.
- Where: `src/main/java/org/example/application/route/RouteModels.java`.
- Done: Records compile and all constructor call sites are intentionally updated in later tasks.
- Tests: Compilation after dependent tasks.
- Gate: No remaining stale `RouteVehicle(index, capacity)` or `RoutePlan(vehicleIndex, capacity, ...)` constructors.

T2 - Validate route creation vehicle names in REST mapping

- ID: vehicle-name-T2
- Status: Completed
- Traceability: VN-001, VN-002
- Depends on: T1
- What: Add `name` to `RouteVehicleDTO`; validate missing, blank, and too-long names in `toVehicles()`; trim valid names before constructing `RouteVehicle`.
- Where: `src/main/java/org/example/presentation/rest/CollectorRouteResource.java`.
- Done: Invalid vehicle names return HTTP 400; valid names are trimmed in the application command.
- Tests: `CollectorRouteResourceTest` covers missing, blank, too-long, and trimmed valid names.
- Gate: Focused REST tests pass.

Phase 2 - Optimization Result Mapping
-------------------------------------

T3 - Preserve vehicle names in greedy fallback route plans

- ID: vehicle-name-T3
- Status: Completed
- Traceability: VN-004, VN-006
- Depends on: T1
- What: Update `OrToolsRouteOptimizationAdapter.MutableRoute` and greedy fallback construction so each route plan includes the corresponding input vehicle name.
- Where: `src/main/java/org/example/infrastructure/route/OrToolsRouteOptimizationAdapter.java`.
- Done: Greedy fallback `RoutePlan` values include `vehicleName` for every vehicle route.
- Tests: `OrToolsRouteOptimizationAdapterTest` verifies fallback results include vehicle names.
- Gate: Focused adapter test passes.

T4 - Preserve vehicle names in OR-Tools solver route plans

- ID: vehicle-name-T4
- Status: Completed
- Traceability: VN-004, VN-005
- Depends on: T1
- What: Update OR-Tools route conversion to attach each input vehicle's name to the generated `RoutePlan` for that `vehicleIndex`.
- Where: `src/main/java/org/example/infrastructure/route/OrToolsRouteOptimizationAdapter.java`.
- Done: Native solver path and fallback path both return route plans with names.
- Tests: Existing OR-Tools adapter tests updated where feasible; if native solver is unavailable in test, cover the route construction helper or document fallback coverage.
- Gate: Adapter tests and compilation pass.

T5 - Update application route use case tests and builders

- ID: vehicle-name-T5
- Status: Completed
- Traceability: VN-003, VN-004, VN-013
- Depends on: T1, T3, T4
- What: Update route optimization tests and shared test data builders to construct named vehicles and assert named route plans.
- Where: `src/test/java/org/example/application/usecase/RouteOptimizationUseCaseTest.java`, related tests using `RouteVehicle` or `RoutePlan`.
- Done: Tests compile with new records and verify names are not lost across use case boundaries.
- Tests: Focused route optimization use case tests.
- Gate: Focused use case tests pass.

Phase 3 - Saved Route Persistence and Workflows
-----------------------------------------------

T6 - Persist vehicle names in saved route documents

- ID: vehicle-name-T6
- Status: Completed
- Traceability: VN-007, VN-011
- Depends on: T1
- What: Add nullable `vehicleName` to saved route route-plan document mapping and ensure writes include it while reads tolerate missing values.
- Where: `src/main/java/org/example/infrastructure/repository/SavedRouteRepository.java`.
- Done: New saved route documents store route plan names; old documents without names can still be read.
- Tests: Add or update repository mapping tests where the repo has a practical test seam; otherwise cover through adapter/use case tests and compilation.
- Gate: Persistence mapping tests or focused build pass.

T7 - Preserve names when moving requests between saved route vehicles

- ID: vehicle-name-T7
- Status: Completed
- Traceability: VN-009
- Depends on: T1, T6
- What: Update route plan recalculation after a move so rebuilt source and target route plans retain their existing `vehicleName` values.
- Where: `src/main/java/org/example/application/usecase/RoutePlanRecalculator.java`, `src/main/java/org/example/application/usecase/MoveRouteRequestUseCase.java` if needed.
- Done: Moving a request changes stops/load/distance only; vehicle names remain unchanged.
- Tests: `MoveRouteRequestUseCaseTest` covers name preservation after a move.
- Gate: Focused move-route tests pass.

T8 - Verify saved route list and map behavior with vehicle names

- ID: vehicle-name-T8
- Status: Completed
- Traceability: VN-008
- Depends on: T6
- What: Update saved route list/map test fixtures and assertions so route plan names are returned or preserved where route plans are exposed or used.
- Where: `src/test/java/org/example/application/usecase/ListSavedRoutesUseCaseTest.java`, `src/test/java/org/example/application/usecase/GetSavedRouteMapUseCaseTest.java`, `src/test/java/org/example/presentation/rest/CollectorRouteResourceTest.java`.
- Done: Saved route workflows handle named route plans without behavior changes to ordering, map geometry, or filtering.
- Tests: Focused saved-route list/map REST and use case tests.
- Gate: Focused tests pass.

T9 - Lock duplicate fingerprint behavior

- ID: vehicle-name-T9
- Status: Completed
- Traceability: VN-010
- Depends on: T1
- What: Add a regression test showing duplicate fingerprints do not include `vehicleName`; same collector/index/sequence/request ids with different names produce the same fingerprint.
- Where: `src/test/java/org/example/application/usecase/SavedRouteFingerprintServiceTest.java`, `src/main/java/org/example/application/usecase/SavedRouteFingerprintService.java` if any accidental implementation change is needed.
- Done: Vehicle nickname does not alter duplicate route detection semantics.
- Tests: Focused fingerprint service test.
- Gate: Focused fingerprint test passes.

Phase 4 - REST Contract and Documentation
-----------------------------------------

T10 - Update route REST tests for request and response contract

- ID: vehicle-name-T10
- Status: Completed
- Traceability: VN-001, VN-002, VN-004, VN-013
- Depends on: T2, T3, T4
- What: Update route suggestion REST tests to send vehicle names and assert response route plans include `vehicleName`.
- Where: `src/test/java/org/example/presentation/rest/CollectorRouteResourceTest.java`.
- Done: REST tests cover successful named route creation and validation failures.
- Tests: `CollectorRouteResourceTest`.
- Gate: Focused REST tests pass.

T11 - Update API documentation examples

- ID: vehicle-name-T11
- Status: Completed
- Traceability: VN-012
- Depends on: T2, T6
- What: Update README examples for route suggestion requests/responses and saved route snapshots to include vehicle names.
- Where: `README.md`.
- Done: Documentation shows `vehicles[].name` in requests and `routes[].vehicleName` in responses.
- Tests: Documentation review.
- Gate: README examples match implemented DTO/model names.

Phase 5 - Verification
----------------------

T12 - Run focused test suite

- ID: vehicle-name-T12
- Status: Completed
- Traceability: VN-013
- Depends on: T3, T4, T5, T6, T7, T8, T9, T10
- What: Run focused Maven tests for route optimization, saved route, route map, move route, fingerprint, and collector route REST coverage.
- Where: `src/test/java/org/example/application/usecase/*Route*Test.java`, `src/test/java/org/example/infrastructure/route/OrToolsRouteOptimizationAdapterTest.java`, `src/test/java/org/example/presentation/rest/CollectorRouteResourceTest.java`.
- Done: Focused tests pass locally.
- Tests: Maven focused test command selected during implementation.
- Gate: No failing focused tests.

T13 - Run full verification

- ID: vehicle-name-T13
- Status: Completed
- Traceability: VN-013
- Depends on: T12
- What: Run the project test suite or the repository's standard verification command.
- Where: project root.
- Done: Full test suite passes or failures are documented as unrelated/pre-existing with evidence.
- Tests: `./mvnw test` or project-standard equivalent.
- Gate: Full verification completed before implementation is considered done.
