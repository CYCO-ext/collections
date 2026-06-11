# Feature: vehicle-name

Problem
-------

Route creation currently identifies each vehicle only by its generated `vehicleIndex` and `capacity`. This is enough for optimization, but it is not enough for collector clients and operators to recognize vehicles in the route plan without mapping indexes back to UI state.

Collectors need to send a human-readable nickname for each vehicle when creating a route so every route plan can carry the same name through the suggestion, save, list, move, and map workflows.

Goal
----

Add a user-provided vehicle nickname to each vehicle used in route creation and preserve that nickname on every route returned or persisted by the service.

The nickname must be sent by the user at the moment of route creation, alongside each vehicle capacity. The service should validate the nickname, attach it to the corresponding optimized route plan, and keep it stable in saved route snapshots and route map responses.

Scope
-----

In scope:

- Add `name` to the route creation vehicle input accepted by `POST /api/collectors/routes/suggest`.
- Add `name` to the application `RouteVehicle` model.
- Add `vehicleName` to the application `RoutePlan` model so optimization results expose the nickname beside `vehicleIndex`.
- Ensure OR-Tools and greedy fallback route planners copy each input vehicle nickname into the resulting route plan.
- Ensure saved route persistence stores and restores the vehicle name in route plan snapshots.
- Ensure saved route listing, saved route map, route request movement, and duplicate route fingerprinting keep working with named vehicles.
- Add validation so every vehicle in a route creation request has a non-blank nickname.
- Add focused tests for route creation validation, route optimization result mapping, saved route persistence mapping, and REST contract behavior.
- Update README/API documentation examples that show route creation or route plan response payloads.

Out of scope for the first implementation:

- Renaming vehicles after a route is created or saved.
- Localizing or translating vehicle names.
- Enforcing global uniqueness of vehicle names across collectors, users, or saved routes.
- Replacing `vehicleIndex` as the technical route identifier.
- Changing route optimization behavior based on the nickname.
- Migrating existing saved route documents beyond tolerant reads where `vehicleName` is absent.

Constraints & Assumptions
-------------------------

- The service is Java 21 with Quarkus, Mutiny, and reactive MongoDB.
- Runtime routes include the global `/api` prefix from `quarkus.rest.path=/api`.
- Route creation is performed through `POST /collectors/routes/suggest` at resource level, exposed as `POST /api/collectors/routes/suggest`.
- Existing vehicle order determines `vehicleIndex`; this feature does not change index assignment.
- `vehicleIndex` remains the stable technical key for move-route operations, route map filtering, duplicate fingerprints, and persisted route plans.
- Vehicle nickname is user-facing metadata and should not affect solver constraints, route ordering, distance calculation, capacity handling, duplicate detection, or status lifecycle.
- Nickname should be trimmed before storage and response.
- Suggested validation for MVP: required, non-blank after trimming, maximum 80 characters.
- Duplicate vehicle names in the same route creation request are allowed unless product later requires uniqueness.
- Existing saved routes without a vehicle name should continue to deserialize and return `vehicleName: null` or omit the field depending on existing JSON behavior; they must not fail list/map operations.

Acceptance Criteria
-------------------

AC-1: A client can call `POST /api/collectors/routes/suggest` with each vehicle containing both `name` and `capacity`.

AC-2: Route creation rejects any vehicle whose `name` is missing, null, blank after trimming, or longer than the configured maximum with HTTP 400.

AC-3: Route creation trims valid vehicle names before constructing the application command.

AC-4: Each returned route plan includes the vehicle nickname that corresponds to its `vehicleIndex`.

AC-5: OR-Tools-backed optimization and greedy fallback optimization both preserve the input vehicle nickname in the returned `RoutePlan`.

AC-6: Saving a route suggestion persists route plan vehicle nicknames in MongoDB `saved_routes` documents.

AC-7: Listing saved routes returns persisted vehicle nicknames in each route plan snapshot.

AC-8: Getting a saved route map preserves vehicle nicknames where route plan data is included or used for selection, without changing map geometry behavior.

AC-9: Moving a request between vehicles keeps the existing names on source and target route plans and does not require clients to resend names.

AC-10: Duplicate route detection remains based on collector id plus normalized route stop ordering by `vehicleIndex` and `sequence`; changing only a vehicle nickname does not create a distinct duplicate fingerprint.

AC-11: Existing saved route documents without vehicle names remain readable.

AC-12: Tests cover missing/blank/too-long vehicle names, successful route suggestion with names, optimization adapter result mapping, saved route persistence mapping, and move-route name preservation.

Traceability IDs
----------------

- VN-001: Route creation vehicle input accepts `name`.
- VN-002: Route creation validates and trims vehicle nickname.
- VN-003: Application route vehicle model carries nickname.
- VN-004: Route plan result carries vehicle nickname.
- VN-005: OR-Tools solver result preserves nickname.
- VN-006: Greedy fallback result preserves nickname.
- VN-007: Saved route persistence stores/restores nickname.
- VN-008: Saved route list/map workflows expose or preserve nickname.
- VN-009: Move-route workflow preserves names during recalculation/update.
- VN-010: Duplicate fingerprint behavior remains index/order based.
- VN-011: Backward-compatible reads for saved routes without names.
- VN-012: API documentation and examples are updated.
- VN-013: Focused test coverage.

Notes
-----

- Suggested request field name: `name` inside each item of `vehicles`.
- Suggested response field name: `vehicleName` inside each route plan, avoiding ambiguity with route id or collector name.
- `vehicleIndex` should remain in all responses because clients still need it for move/map operations.
