# Feature: or-tools-routes

Problem
-------
Collectors can see nearby collection requests today, but the app does not help them decide the best order to visit multiple pickup points when they have one or more vehicles available.

A collector needs to ask the Collections service for a suggested route plan by sending operational limits such as vehicle count, vehicle capacity, start location, and optional route preferences. The service should return optimized routes that assign eligible collection requests to vehicles while respecting capacity constraints.

Goal
----
Add route optimization for collectors using Google OR-Tools Vehicle Routing Problem support.

The collector sends route planning input, the service builds an optimization problem from eligible collection requests and known coordinates, and the response returns one suggested route per vehicle with ordered stops, load totals, distance estimates, and any unassigned requests.

Scope
-----

In scope:

- Collector-facing REST endpoint to request a route suggestion.
- Request DTO containing vehicle count, vehicle capacities, start/depot location, optional end behavior, candidate request IDs or filters, and solver options.
- Route optimization use case that loads collection requests, addresses, and collector data.
- OR-Tools adapter for capacitated vehicle routing.
- Distance matrix abstraction so the MVP can use Haversine distance and later switch to OSRM, Google Distance Matrix, or another routing provider.
- Response DTO with routes, ordered stops, estimated distance, estimated load, skipped/unassigned stops, and solver status.
- Validation, bounded solver time, and tests for capacity, multi-vehicle assignment, and infeasible inputs.

Out of scope for the first implementation:

- Reserving or accepting collection requests from the route suggestion response.
- Real road-network distance integration.
- Turn-by-turn navigation.
- Live traffic.
- Time windows unless the implementation discovers existing request scheduling fields.
- Persisting optimized route history unless explicitly requested later.

Constraints & Assumptions
-------------------------

- The service is Java 21 with Quarkus and Mutiny.
- Collection request pickup weight maps to OR-Tools demand.
- Vehicle capacity uses the same unit as collection request weight for MVP.
- A route suggestion is read-only. It must not change `CollectionRequest.status`, selected collector, or confirmation fields.
- Only collection requests with status `IN_PROGRESS` can be routed. `PENDING`, `COMPLETED`, and `REJECTED` requests are returned as unassigned.
- A collector can provide either one capacity applied to all vehicles or explicit capacity per vehicle.
- The start point can be the collector's current location, the collector's registered address, or an explicit address/coordinate in the request.
- The MVP can assume every vehicle starts at the same depot. Separate starts/ends can be added later through the same request model.
- Only requests with usable coordinates can be optimized. Requests without coordinates are returned as unassigned with a clear reason.
- If the candidate demand exceeds total vehicle capacity, the solver may either fail or drop lower-priority stops based on the request option.
- OR-Tools native libraries must be loaded safely during application startup or adapter initialization.

Acceptance Criteria
-------------------

AC-1: A collector can call a new endpoint to request route optimization by providing vehicle count, capacity information, start location, and candidate selection options.

AC-2: The endpoint validates that vehicle count is positive, capacities are positive, coordinates are valid, and at least one candidate collection request can be considered.

AC-3: The route planning use case loads candidate `CollectionRequest` records and pickup `Address` records, then excludes or reports candidates that are not `IN_PROGRESS`, not accepted by the collector's material profile, or missing usable coordinates.

AC-4: The optimization engine uses OR-Tools to solve a capacitated vehicle routing problem where each pickup demand is based on request weight and each vehicle capacity is respected.

AC-5: The service returns one route per vehicle with ordered stops, collection request IDs, address IDs, coordinates, per-stop demand, accumulated load, estimated distance, and total route distance.

AC-6: The response includes unassigned candidates with reason codes such as `MISSING_COORDINATES`, `NOT_IN_PROGRESS`, `MATERIAL_NOT_ACCEPTED`, `OVER_CAPACITY`, or `SOLVER_DROPPED`.

AC-7: The service supports a bounded solver time limit and returns solver metadata including status, elapsed time, objective distance, and whether the result is optimal, feasible, partial, or infeasible.

AC-8: The first implementation uses a distance matrix port; the default adapter may use Haversine distance, but OR-Tools code must not depend directly on address repositories or REST DTOs.

AC-9: Repeated route suggestion calls with the same request data are idempotent and do not mutate collection requests or collectors.

AC-10: Tests cover single-vehicle capacity, multi-vehicle capacity, insufficient total capacity with dropped stops enabled, missing coordinates, invalid input, and REST response shape.

Traceability IDs
----------------

- ORR-001: Collector route suggestion endpoint
- ORR-002: Route optimization request validation
- ORR-003: Candidate request and address loading
- ORR-004: Collector material compatibility filtering
- ORR-005: Distance matrix abstraction
- ORR-006: OR-Tools CVRP adapter
- ORR-007: Multi-vehicle capacity constraints
- ORR-008: Unassigned stop reason reporting
- ORR-009: Solver limits and metadata
- ORR-010: Read-only/idempotent suggestion behavior
- ORR-011: Route response DTOs
- ORR-012: Focused test coverage

Notes
-----

- Google OR-Tools supports vehicle routing problems and capacitated vehicle routing through distance callbacks, demand callbacks, vehicle capacities, and routing dimensions.
- The implementation should check the current OR-Tools Java Maven artifact/version before coding and pin the chosen version in `pom.xml`.
- The MVP should prefer predictable behavior over perfect real-world routing accuracy. Road-network distance can be added behind `DistanceMatrixPort` later without changing the REST contract.
