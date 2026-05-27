# Feature: request-map

Problem
-------
Saved route suggestions currently preserve the planned stops and vehicle assignments, but they do not persist a map-ready road geometry for the route. Clients that need to render the route on a map would have to recalculate geometry themselves or call a routing provider repeatedly for the same saved route.

Collectors need a reusable map representation of each vehicle route. The service should call OpenRouteService once for a route version, store the returned GeoJSON in MongoDB, and reuse the stored map while the vehicle route remains unchanged.

Goal
----
Add request map generation for saved routes using OpenRouteService Directions GeoJSON.

The service should use `POST /v2/directions/driving-car/geojson` to generate map geometry from the ordered route points, save the generated map in MongoDB, and reuse the saved map on later requests unless the saved vehicle route changes.

Scope
-----

In scope:

- Configuration for OpenRouteService base URL and API key.
- Application model for route map snapshots stored per saved route and per vehicle route.
- MongoDB persistence for generated route maps.
- Port and adapter to call OpenRouteService Directions GeoJSON.
- Endpoint to get or create map geometry for a saved route.
- Cache reuse based on a deterministic vehicle-route fingerprint.
- Cache invalidation/recalculation when a saved route vehicle plan changes, including move-request updates.
- Tests for map generation, reuse, stale-map regeneration, persistence mapping, REST status mapping, and OpenRouteService request mapping.

Out of scope for the first implementation:

- Frontend map rendering.
- Turn-by-turn navigation UI.
- Supporting non-car profiles.
- Real-time traffic.
- Offline maps.
- Per-segment styling or route editing in GeoJSON.
- Calling OpenRouteService during route suggestion before the route is saved.
- Replacing OR-Tools route optimization with OpenRouteService optimization.

Constraints & Assumptions
-------------------------

- Runtime routes include the global `/api` prefix from `quarkus.rest.path=/api`.
- Existing saved routes are stored in MongoDB collection `saved_routes`.
- Vehicle route changes are represented by changes to ordered stops inside a saved route, including changes made by `move-request`.
- OpenRouteService expects coordinates in `[longitude, latitude]` order.
- The route map should be generated from the ordered stop coordinates for each vehicle route.
- If a saved route has a start point and/or end point in its route snapshot, those points should be included in the coordinates sent to OpenRouteService.
- A vehicle route with fewer than two routable coordinates cannot generate a driving route and should return a validation error for that vehicle map.
- Generated GeoJSON should be stored as provider output without lossy transformation so clients can render it directly.
- The OpenRouteService API key must be supplied by environment variable and must not be hardcoded.
- Reuse must be based on a deterministic fingerprint derived from saved route id, vehicle index, and the ordered routable coordinates/stops.
- OpenRouteService failures should not mutate saved routes. The endpoint should return a provider error response mapped to an appropriate HTTP status.
- A saved map is considered reusable only when its fingerprint matches the current vehicle route fingerprint.

Acceptance Criteria
-------------------

AC-1: A client can request map geometry for a saved route through a REST endpoint.

AC-2: The service loads the saved route and builds ordered route coordinates for each vehicle route.

AC-3: The service calls OpenRouteService with `POST /v2/directions/driving-car/geojson` using JSON body `coordinates` in `[longitude, latitude]` order.

AC-4: The service includes the OpenRouteService API key in the outbound request using configuration from environment variables.

AC-5: The generated GeoJSON response is saved in MongoDB with saved route id, vehicle index, route fingerprint, provider, profile, createdAt, updatedAt, and raw GeoJSON payload.

AC-6: Repeated requests for the same saved route and unchanged vehicle routes reuse the existing MongoDB route map instead of calling OpenRouteService again.

AC-7: When a vehicle route changes, including after moving a request between vehicles, a later map request detects the fingerprint mismatch and regenerates the affected vehicle map.

AC-8: Regeneration only replaces stale vehicle maps. Unchanged vehicle maps are reused.

AC-9: If the saved route does not exist, the endpoint returns HTTP 404.

AC-10: If a vehicle route has insufficient coordinates for routing, the endpoint returns HTTP 400 with a clear validation message.

AC-11: If OpenRouteService returns an error or times out, the endpoint returns a non-success response without saving a partial map.

AC-12: Tests verify OpenRouteService request mapping, cache reuse, stale-map regeneration, unchanged-map reuse, REST error mapping, and MongoDB document mapping.

Traceability IDs
----------------

- RM-001: Request map endpoint
- RM-002: OpenRouteService integration
- RM-003: MongoDB route map persistence
- RM-004: Deterministic route map fingerprint
- RM-005: Reuse unchanged route maps
- RM-006: Regenerate maps when vehicle route changes
- RM-007: Move-request integration
- RM-008: Error handling and validation
- RM-009: Configuration and secrets
- RM-010: Test coverage

Reference
---------

OpenRouteService documents the Directions GeoJSON endpoint as `POST /v2/directions/{profile}/geojson`, where `driving-car` is the profile required for this feature: https://giscience.github.io/openrouteservice/api-reference/endpoints/directions/requests-and-return-types
