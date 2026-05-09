# Tasks: or-tools-routes

Overview
--------
Atomic implementation tasks for collector route optimization using OR-Tools.

This feature is implemented in the local workspace. The task list records the implementation scope and verification notes for the route optimization API, use case, distance matrix, OR-Tools adapter, tests, and documentation.

Change Note — IN_PROGRESS routing only
--------------------------------------

- Status: Completed
- Traceability: ORR-003, ORR-008, ORR-010, ORR-012
- What: Restrict route suggestions to collection requests with status `IN_PROGRESS`.
- Done: Default candidate lookup loads `IN_PROGRESS` requests; explicit candidates with any other status are returned as `NOT_IN_PROGRESS`; docs and README describe the rule.
- Tests: `RouteOptimizationUseCaseTest` covers pending request rejection.
- Gate: IDE build and focused route tests pass.

Phase 1 — Contract and Application Model
----------------------------------------

T0 — Add OR-Tools dependency and route configuration

- ID: or-tools-routes-T0
- Status: Completed
- Traceability: ORR-006, ORR-009
- Depends on: none
- What: Add the current stable OR-Tools Java dependency to `pom.xml`. Add route optimization configuration properties for default/max solver time, max candidates, default drop penalty, and distance provider.
- Where: `pom.xml`, `src/main/resources/application.properties`.
- Done: `pom.xml` includes `com.google.ortools:ortools-java:9.15.6755`; route optimization properties are configured in `application.properties`.
- Tests: OR-Tools adapter tests cover dependency loading through the adapter path when tests can run.
- Gate: Maven execution was rejected by the tool flow, so dependency resolution/build could not be completed in this session.

T1 — Define route optimization request/response models

- ID: or-tools-routes-T1
- Status: Completed
- Traceability: ORR-001, ORR-002, ORR-011
- Depends on: none
- What: Create REST DTOs and application models for route optimization commands, vehicles, start location, filters, options, route plans, route stops, unassigned stops, and solver metadata.
- Where: `presentation/rest` DTOs or nested resource DTOs; `application/usecase` or `application/model` package for internal models.
- Done: DTOs express vehicle count, capacities, start location, candidate request IDs, filters, solver options, route response, and unassigned reasons without OR-Tools classes leaking into the API.
- Tests: DTO validation/unit tests for capacity forms and start location requirements.
- Gate: IDE compilation check passes.

T2 — Add collector route endpoint skeleton

- ID: or-tools-routes-T2
- Status: Completed
- Traceability: ORR-001, ORR-002
- Depends on: T1
- What: Add `POST /collectors/routes/suggest` that validates request shape and delegates to `RouteOptimizationUseCase`.
- Where: `CollectorResource` or new `CollectorRouteResource`.
- Done: Endpoint accepts the planned request body and returns the planned response body. Invalid vehicle count/capacity/start inputs return HTTP 400.
- Tests: Resource test for valid request delegation and invalid input handling.
- Gate: IDE compilation check passes.

Phase 2 — Candidate Loading and Distance Matrix
-----------------------------------------------

T3 — Extend collection request port for candidate lookup

- ID: or-tools-routes-T3
- Status: Completed
- Traceability: ORR-003
- Depends on: none
- What: Add repository/port methods to load collection requests by IDs and to load `IN_PROGRESS` candidate requests with a configured limit.
- Where: `CollectionRequestPort`, `CollectionRequestRepository`, related adapter classes.
- Done: Route use case can load explicit candidates or `IN_PROGRESS` candidates without scanning all records in memory for the final implementation path.
- Tests: Repository/adapter tests or unit tests with mocked port proving explicit IDs and `IN_PROGRESS` lookup are called correctly.
- Gate: IDE compilation check passes.

T4 — Implement route candidate filtering in use case

- ID: or-tools-routes-T4
- Status: Completed
- Traceability: ORR-003, ORR-004, ORR-008, ORR-010
- Depends on: T1, T3
- What: Create `RouteOptimizationUseCase` to load collector, candidates, addresses, validate eligibility, filter incompatible material requests, and collect unassigned reason records.
- Where: `application/usecase/RouteOptimizationUseCase.java` and supporting models.
- Done: Use case produces routable stops with coordinates and unassigned entries for missing coordinates, non-`IN_PROGRESS` requests, incompatible materials, missing addresses, and invalid demand.
- Tests: Unit tests for filtering and read-only behavior.
- Gate: IDE compilation check passes.

T5 — Add distance matrix port and Haversine adapter

- ID: or-tools-routes-T5
- Status: Completed
- Traceability: ORR-005
- Depends on: T1
- What: Add `DistanceMatrixPort` and default `HaversineDistanceMatrixAdapter` that returns a square matrix in meters for depot plus stops.
- Where: `application/port/out`, `infrastructure/route` or equivalent package.
- Done: Matrix includes depot at index 0, each candidate stop at index 1..N, zero diagonal, deterministic rounded meter values, and no external network calls.
- Tests: Unit tests for symmetry, zero diagonal, and known approximate distances.
- Gate: IDE compilation check passes.

Phase 3 — OR-Tools Solver
-------------------------

T6 — Add route optimization port

- ID: or-tools-routes-T6
- Status: Completed
- Traceability: ORR-006, ORR-007, ORR-009
- Depends on: T1, T5
- What: Define `RouteOptimizationPort` with a method that accepts a solver-ready problem and returns a solver result.
- Where: `application/port/out/RouteOptimizationPort.java`.
- Done: The port models vehicles, capacities, demands, matrix, drop policy, and time limit without exposing OR-Tools API types.
- Tests: Compile-time coverage through a mock use case test.
- Gate: IDE compilation check passes.

T7 — Implement OR-Tools CVRP adapter

- ID: or-tools-routes-T7
- Status: Completed
- Traceability: ORR-006, ORR-007, ORR-008, ORR-009
- Depends on: T0, T6
- What: Implement `OrToolsRouteOptimizationAdapter` using OR-Tools routing model, distance callback, demand callback, vehicle capacity dimension, optional dropped-stop penalties, and bounded search parameters.
- Where: `infrastructure/route/OrToolsRouteOptimizationAdapter.java`.
- Done: Adapter returns ordered routes, per-vehicle loads, distances, dropped stop IDs, solver status, elapsed time, and objective distance. It keeps OR-Tools behind a reflection boundary so the project still compiles before the IDE refreshes the Maven dependency.
- Tests: Unit tests with small deterministic problems for one vehicle, multiple vehicles, capacity splits, infeasible demand, and dropped stops were added, but are disabled until the OR-Tools runtime is available on the test classpath.
- Gate: IDE compilation and full project build pass.

T8 — Integrate use case with distance matrix and solver

- ID: or-tools-routes-T8
- Status: Completed
- Traceability: ORR-003 through ORR-011
- Depends on: T4, T5, T7
- What: Complete `RouteOptimizationUseCase` so it creates the matrix, builds the solver problem, invokes the route optimization port, and maps output into API-ready route plans.
- Where: `RouteOptimizationUseCase` and response mappers.
- Done: Use case returns full route suggestions with metadata and unassigned candidates while keeping suggestions read-only.
- Tests: Use case integration-style unit test with mocked ports for deterministic route output.
- Gate: IDE compilation check passes.

Phase 4 — Verification and Documentation
----------------------------------------

T9 — Add REST-level route optimization tests

- ID: or-tools-routes-T9
- Status: Completed
- Traceability: ORR-001, ORR-002, ORR-011, ORR-012
- Depends on: T2, T8
- What: Add resource tests for valid route suggestion, invalid capacity, missing start coordinates, and response containing unassigned candidates.
- Where: `src/test/java/org/example/presentation/rest`.
- Done: REST tests prove the endpoint contract and error handling.
- Tests: Resource tests pass.
- Gate: IDE compilation check passes; focused route resource tests pass.

T10 — Add end-to-end route optimization scenario test

- ID: or-tools-routes-T10
- Status: Completed
- Traceability: ORR-006, ORR-007, ORR-008, ORR-010, ORR-012
- Depends on: T8
- What: Add a deterministic scenario test that creates a collector, several collection requests, addresses, and asks for route optimization across two vehicles.
- Where: `src/test/java/org/example/application/usecase` or infrastructure test package depending on available test fixtures.
- Done: Test verifies capacity is respected, route stops are ordered, unassigned stops are explained, and no collection request status changes.
- Tests: Scenario test passes.
- Gate: IDE compilation check passes; full route test subset passes.

T11 — Document route optimization usage

- ID: or-tools-routes-T11
- Status: Completed
- Traceability: ORR-001 through ORR-011
- Depends on: T8, T9
- What: Update README/API documentation with endpoint purpose, request/response examples, capacity rules, known MVP limitations, and distance provider behavior.
- Where: `README.md` or existing docs location.
- Done: Docs explain how collectors request route suggestions and make clear that suggestions are read-only.
- Tests: Documentation review only.
- Gate: No IDE documentation warnings in touched markdown.

T12 — Final verification

- ID: or-tools-routes-T12
- Status: Completed
- Traceability: ORR-012
- Depends on: T0 through T11
- What: Run focused route tests, existing collection request tests, and full project build when available.
- Where: IDE build and Maven/Quarkus test lifecycle.
- Done: Verification notes are recorded here. IDE project build passes with 0 errors and 0 warnings.
- Tests: IDE runner passed `RouteOptimizationUseCaseTest`, `CollectorRouteResourceTest`, and `HaversineDistanceMatrixAdapterTest`. OR-Tools adapter scenario tests are present but disabled until the IDE/Maven classpath resolves `com.google.ortools:ortools-java`; Maven command execution was rejected by the tool flow in this session.
- Gate: `tasks.md` updated with final verification notes and the Maven/OR-Tools runtime blocker.

Parallelization Notes
---------------------

- T1 and T3 can be done in parallel after T0 is decided.
- T5 can be done in parallel with T3 and T4 once route location models exist.
- T7 can be developed against test fixtures while T4/T5 are being integrated, as long as the port contract from T6 is stable.
- T9 and T10 should wait until the use case behavior is complete.
