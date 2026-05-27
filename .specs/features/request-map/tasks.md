# Tasks: request-map

Overview
--------
Atomic implementation tasks for generating map-ready GeoJSON for saved vehicle routes through OpenRouteService, persisting route maps in MongoDB, reusing unchanged maps, and regenerating maps when a vehicle route changes.

Implementation Status
---------------------

- Status: Completed
- Endpoint: `GET /api/collectors/routes/saved/{savedRouteId}/map`
- Provider: OpenRouteService Directions GeoJSON, `POST /v2/directions/driving-car/geojson`
- MongoDB collection: `route_maps`
- Cache rule: reuse when current vehicle-route fingerprint matches stored fingerprint; regenerate when it differs.

Phase 1 — Contract and Models
-----------------------------

T0 — Confirm endpoint contract and cache semantics

- ID: request-map-T0
- Status: Completed
- Traceability: RM-001, RM-004, RM-005, RM-006
- Depends on: none
- What: Confirm `GET /collectors/routes/saved/{savedRouteId}/map`, optional `vehicleIndex`, response shape, and reuse/regeneration behavior.
- Where: `.specs/features/request-map/spec.md`, `.specs/features/request-map/design.md`.
- Done: Contract is documented and consistent with implementation naming.
- Tests: Documentation review.
- Gate: Spec, design, task list, and eventual README agree on endpoint and behavior.

T1 — Add OpenRouteService configuration

- ID: request-map-T1
- Status: Completed
- Traceability: RM-002, RM-009
- Depends on: T0
- What: Add configuration properties for base URL, API key, and timeout using environment variables.
- Where: `src/main/resources/application.properties` and a config class if the project pattern requires it.
- Done: `OPENROUTESERVICE_API_KEY`, `OPENROUTESERVICE_BASE_URL`, and `OPENROUTESERVICE_TIMEOUT_MS` are supported without hardcoded secrets.
- Tests: Configuration can be injected in unit/REST tests.
- Gate: IDE compilation passes.

T2 — Add route map application models

- ID: request-map-T2
- Status: Completed
- Traceability: RM-001, RM-003, RM-005
- Depends on: T0
- What: Add application records/enums for route map entity, vehicle map result, provider, coordinate, and get-map query/result.
- Where: `src/main/java/org/example/application/route/RouteMapModels.java` or existing route model package.
- Done: Models are independent from MongoDB and REST DTO classes.
- Tests: Covered by use case and mapping tests.
- Gate: IDE compilation passes.

T3 — Add route map validation exception

- ID: request-map-T3
- Status: Completed
- Traceability: RM-008
- Depends on: T2
- What: Add typed validation exception for invalid vehicle index and insufficient routable coordinates.
- Where: `src/main/java/org/example/application/usecase/RouteMapValidationException.java`.
- Done: Use case can signal validation failures for REST 400 mapping.
- Tests: Use case and REST tests cover validation paths.
- Gate: IDE compilation passes.

Phase 2 — Fingerprint and Use Case
----------------------------------

T4 — Implement route map fingerprint service

- ID: request-map-T4
- Status: Completed
- Traceability: RM-004, RM-006, RM-007
- Depends on: T2
- What: Implement deterministic SHA-256 fingerprint generation from saved route id, vehicle index, ordered coordinates, and ordered stop identifiers.
- Where: `src/main/java/org/example/application/usecase/RouteMapFingerprintService.java`.
- Done: Same route produces same fingerprint; changed stop order, coordinates, or vehicle assignment changes fingerprint.
- Tests: `RouteMapFingerprintServiceTest` covers stable and changing fingerprint cases.
- Gate: Focused unit test passes.

T5 — Add route map persistence port

- ID: request-map-T5
- Status: Completed
- Traceability: RM-003, RM-005, RM-006
- Depends on: T2
- What: Define `RouteMapPort` for finding route maps by saved route/vehicle and upserting generated maps.
- Where: `src/main/java/org/example/application/port/out/RouteMapPort.java`.
- Done: Application use case depends only on the port.
- Tests: Covered by mocked use case tests.
- Gate: IDE compilation passes.

T6 — Add OpenRouteService directions port

- ID: request-map-T6
- Status: Completed
- Traceability: RM-002, RM-008
- Depends on: T2
- What: Define `OpenRouteServiceDirectionsPort` for fetching driving-car GeoJSON from ordered coordinates.
- Where: `src/main/java/org/example/application/port/out/OpenRouteServiceDirectionsPort.java`.
- Done: Application layer has no direct HTTP client dependency.
- Tests: Covered by mocked use case tests and adapter tests.
- Gate: IDE compilation passes.

T7 — Implement get saved route map use case

- ID: request-map-T7
- Status: Completed
- Traceability: RM-001, RM-002, RM-003, RM-005, RM-006, RM-007, RM-008
- Depends on: T2, T3, T4, T5, T6
- What: Load saved route, extract routable coordinates per vehicle, reuse matching stored maps, call OpenRouteService for missing/stale maps, persist generated maps, and return combined result.
- Where: `src/main/java/org/example/application/usecase/GetSavedRouteMapUseCase.java`.
- Done: Matching maps are reused; stale vehicle maps are regenerated; unchanged vehicle maps are reused; invalid route data fails validation.
- Tests: `GetSavedRouteMapUseCaseTest` covers generated map, reuse, stale regeneration, partial regeneration, missing saved route, invalid vehicle index, and insufficient coordinates.
- Gate: Focused use case test passes.

Phase 3 — Infrastructure
------------------------

T8 — Implement MongoDB route map repository and adapter

- ID: request-map-T8
- Status: Completed
- Traceability: RM-003, RM-005, RM-006
- Depends on: T2, T5
- What: Add MongoDB repository/adapter for `route_maps`, including raw GeoJSON storage and upsert by saved route id, vehicle index, provider, and profile.
- Where: `src/main/java/org/example/infrastructure/repository/RouteMapRepository.java`, `src/main/java/org/example/application/adapter/RouteMapAdapter.java`.
- Done: Repository can find existing vehicle maps and replace stale maps atomically enough for MVP.
- Tests: Mapping test or build coverage verifies MongoDB document mapping.
- Gate: IDE compilation passes.

T9 — Implement OpenRouteService HTTP adapter

- ID: request-map-T9
- Status: Completed
- Traceability: RM-002, RM-008, RM-009
- Depends on: T1, T2, T6
- What: Implement adapter that sends `POST /v2/directions/driving-car/geojson`, authorization header, JSON coordinates in `[longitude, latitude]` order, timeout handling, and provider error mapping.
- Where: `src/main/java/org/example/infrastructure/route/OpenRouteServiceDirectionsAdapter.java` or equivalent infrastructure package.
- Done: Adapter returns raw GeoJSON on success and throws typed provider exception on failures/timeouts.
- Tests: Adapter test verifies URL, method, headers, body coordinate order, success mapping, and failure mapping using a stub HTTP server or mock client.
- Gate: Focused adapter test passes.

Phase 4 — REST API
------------------

T10 — Add route map REST DTOs and mappers

- ID: request-map-T10
- Status: Completed
- Traceability: RM-001, RM-003, RM-008
- Depends on: T2, T7
- What: Add response DTOs for saved route map and vehicle maps, preserving `geoJson` as a JSON object.
- Where: Existing route REST DTO area or `CollectorRouteResource` nested DTOs if that is the local pattern.
- Done: DTOs do not stringify GeoJSON and include `reused`, `fingerprint`, provider, profile, and timestamps.
- Tests: REST tests inspect response shape.
- Gate: IDE compilation passes.

T11 — Add get saved route map endpoint

- ID: request-map-T11
- Status: Completed
- Traceability: RM-001, RM-008
- Depends on: T7, T10
- What: Add `GET /collectors/routes/saved/{savedRouteId}/map` with optional `vehicleIndex`, delegate to use case, and map errors to HTTP 400, 404, 502, 504, or 500 as appropriate.
- Where: `src/main/java/org/example/presentation/rest/CollectorRouteResource.java` or a focused route map resource.
- Done: Endpoint returns generated or reused route maps for saved routes.
- Tests: `CollectorRouteResourceTest` or new REST test covers success, not found, validation, and provider failure.
- Gate: Focused REST test passes.

Phase 5 — Move-Request Consistency and Documentation
----------------------------------------------------

T12 — Ensure move-request changes make maps stale by fingerprint

- ID: request-map-T12
- Status: Completed
- Traceability: RM-006, RM-007
- Depends on: T4, T7
- What: Verify saved route mutation from move-request changes the route data used by the fingerprint. Add or adjust tests so moving a request to another vehicle causes only affected vehicle maps to regenerate on next map request.
- Where: `src/test/java/org/example/application/usecase/MoveRouteRequestUseCaseTest.java`, `GetSavedRouteMapUseCaseTest`.
- Done: No direct OpenRouteService call happens during move-request; stale detection happens on next map request.
- Tests: Move-request plus map-use-case tests cover fingerprint behavior.
- Gate: Focused tests pass.

T13 — Update README and operational docs

- ID: request-map-T13
- Status: Completed
- Traceability: RM-001, RM-002, RM-003, RM-009
- Depends on: T11
- What: Document the route map endpoint, OpenRouteService env vars, MongoDB `route_maps` collection, and cache reuse behavior.
- Where: `README.md`.
- Done: README includes endpoint and operational configuration without secrets.
- Tests: Documentation review.
- Gate: README matches implemented API.

Phase 6 — Verification
----------------------

T14 — Run focused tests and build

- ID: request-map-T14
- Status: Completed
- Traceability: RM-010
- Depends on: T1 through T13
- What: Run focused unit/REST tests for request-map and route move interactions, then run project build.
- Where: IDE test runner or Maven/Quarkus test command available in the project.
- Done: Focused tests and build pass, or failures are documented with concrete cause.
- Tests: `RouteMapFingerprintServiceTest`, `GetSavedRouteMapUseCaseTest`, OpenRouteService adapter test, route map REST test, move-request affected tests, and project build.
- Gate: No compilation errors; no failing focused tests.

Parallelization Notes
---------------------

- T1, T2, and T3 can be done after T0.
- T4, T5, and T6 can be implemented in parallel after T2.
- T8 and T9 can be implemented in parallel after their ports exist.
- T10 can start after T2, but T11 depends on T7.
- T12 should run after T7 and should verify behavior against the existing move-request implementation.
