# Design: request-map

Overview
--------
Add a map-generation layer for saved route suggestions. The service will transform each saved vehicle route into ordered OpenRouteService coordinates, call the Directions GeoJSON endpoint, persist the returned GeoJSON in MongoDB, and reuse the persisted map while the vehicle route remains unchanged.

This feature does not change route optimization. OR-Tools remains responsible for deciding which stops belong to each vehicle and in which order. OpenRouteService is used only to convert an already planned vehicle route into road-following map geometry.

Proposed API
------------

Get-or-create route map endpoint:

```text
GET /collectors/routes/saved/{savedRouteId}/map
```

Runtime URL:

```text
GET /api/collectors/routes/saved/{savedRouteId}/map
```

Optional query parameters for later extension:

```text
vehicleIndex=0
refresh=false
```

MVP behavior:

- Without `vehicleIndex`, return maps for all vehicle routes in the saved route.
- With `vehicleIndex`, return only that vehicle map.
- `refresh=true` may force provider recalculation, but is optional for MVP. The required behavior is automatic recalculation on fingerprint mismatch.

Response shape:

```json
{
  "savedRouteId": "saved-route-123",
  "provider": "OPEN_ROUTE_SERVICE",
  "profile": "driving-car",
  "maps": [
    {
      "vehicleIndex": 0,
      "fingerprint": "sha256:...",
      "reused": true,
      "geoJson": {
        "type": "FeatureCollection",
        "features": []
      },
      "createdAt": "2026-05-26T10:00:00Z",
      "updatedAt": "2026-05-26T10:00:00Z"
    }
  ]
}
```

OpenRouteService Request
------------------------

Outbound endpoint:

```text
POST {openrouteservice.base-url}/v2/directions/driving-car/geojson
```

Default base URL:

```text
https://api.openrouteservice.org
```

Headers:

```text
Authorization: ${OPENROUTESERVICE_API_KEY}
Content-Type: application/json
Accept: application/json
```

Request body:

```json
{
  "coordinates": [
    [-46.6333, -23.5505],
    [-46.6400, -23.5600],
    [-46.6500, -23.5700]
  ]
}
```

Coordinates must be sent as `[longitude, latitude]`.

Configuration
-------------

Add application properties:

```properties
openrouteservice.base-url=${OPENROUTESERVICE_BASE_URL:https://api.openrouteservice.org}
openrouteservice.api-key=${OPENROUTESERVICE_API_KEY:}
openrouteservice.timeout-ms=${OPENROUTESERVICE_TIMEOUT_MS:10000}
```

The API key must come from deployment configuration. It must not be committed to the repository.

Data Model
----------

Application model:

```text
RouteMap
- id
- savedRouteId
- vehicleIndex
- provider = OPEN_ROUTE_SERVICE
- profile = driving-car
- fingerprint
- geoJson
- createdAt
- updatedAt
```

MongoDB collection:

```text
route_maps
```

Suggested document shape:

```json
{
  "_id": "route-map-123",
  "savedRouteId": "saved-route-123",
  "vehicleIndex": 0,
  "provider": "OPEN_ROUTE_SERVICE",
  "profile": "driving-car",
  "fingerprint": "sha256:...",
  "geoJson": {
    "type": "FeatureCollection",
    "features": []
  },
  "createdAt": "2026-05-26T10:00:00Z",
  "updatedAt": "2026-05-26T10:00:00Z"
}
```

Recommended unique index:

```text
savedRouteId + vehicleIndex + provider + profile
```

The document should be replaced when the current vehicle route fingerprint differs from the stored fingerprint.

Fingerprint Strategy
--------------------

The fingerprint should be deterministic and independent of provider response details.

Inputs:

- saved route id
- vehicle index
- ordered start coordinate, if present
- ordered collection request ids
- ordered stop coordinates
- end coordinate, if present
- route version or updatedAt of the saved route, only if it is already stable and deterministic

Suggested normalized string:

```text
savedRouteId|vehicleIndex|lng,lat|collectionRequestId:lng,lat|collectionRequestId:lng,lat|lng,lat
```

Hash with SHA-256 and store as `sha256:<hex>`.

Primary Flow
------------

1. Client calls `GET /api/collectors/routes/saved/{savedRouteId}/map`.
2. REST resource validates `savedRouteId` and optional query parameters.
3. `GetSavedRouteMapUseCase` loads the saved route through `SavedRoutePort`.
4. If the saved route does not exist, return not found.
5. The use case extracts vehicle route points from the saved route snapshot.
6. For each requested vehicle route, the use case calculates the current fingerprint.
7. The use case asks `RouteMapPort` for an existing map by saved route id and vehicle index.
8. If a stored map exists and its fingerprint matches, return it with `reused=true`.
9. If no map exists or the fingerprint differs, call `OpenRouteServiceDirectionsPort`.
10. Persist the returned GeoJSON with the current fingerprint.
11. Return the persisted map with `reused=false` for newly generated maps.

Move-Request Integration
------------------------

The existing move-request feature changes stops between vehicles and recalculates route plans. This feature should not call OpenRouteService during move-request execution.

Instead:

1. Move-request updates the saved route snapshot.
2. Existing route map documents remain stored but may become stale.
3. The next map request recalculates fingerprints for affected vehicle routes.
4. Vehicle maps with mismatched fingerprints are regenerated.
5. Vehicle maps with matching fingerprints are reused.

This keeps move-request fast and avoids provider calls inside route mutation transactions.

Components
----------

Application layer:

- `RouteMapModels` for map result, vehicle map result, provider enum, and command/query records.
- `GetSavedRouteMapUseCase` for get-or-create behavior.
- `RouteMapFingerprintService` for deterministic fingerprint generation.
- `RouteMapValidationException` for insufficient coordinates or invalid vehicle index.

Ports:

- `RouteMapPort`
  - `findBySavedRouteIdAndVehicleIndex(savedRouteId, vehicleIndex)`
  - `upsert(RouteMap map)`
  - `findBySavedRouteId(savedRouteId)` if needed for cleanup/listing
- `OpenRouteServiceDirectionsPort`
  - `fetchDrivingCarGeoJson(List<RouteCoordinate> coordinates)`

Infrastructure:

- `RouteMapRepository` for MongoDB collection `route_maps`.
- `RouteMapAdapter` implementing `RouteMapPort`.
- `OpenRouteServiceDirectionsAdapter` using a Quarkus REST client or Vert.x WebClient.

Presentation:

- Extend `CollectorRouteResource` with `GET /routes/saved/{savedRouteId}/map`, or create a focused resource if the file becomes too large.
- REST DTOs should keep raw `geoJson` as JSON object/document so clients receive renderable GeoJSON.

Error Handling
--------------

- Missing saved route: HTTP 404.
- Invalid vehicle index: HTTP 400.
- Fewer than two routable coordinates: HTTP 400.
- Missing OpenRouteService API key: HTTP 500 or startup/config error. Prefer a clear application error if startup validation is not used.
- OpenRouteService 4xx: map to HTTP 502 unless the error is caused by invalid coordinates, then HTTP 400 if detectable.
- OpenRouteService 5xx or timeout: HTTP 502 or 504.
- MongoDB failure: HTTP 500.

Testing Strategy
----------------

Unit tests:

- Fingerprint is stable for the same vehicle route.
- Fingerprint changes when stop order, coordinates, or vehicle assignment changes.
- Use case reuses a matching stored map without calling OpenRouteService.
- Use case regenerates stale maps when fingerprint differs.
- Use case reuses unchanged vehicle maps while regenerating changed ones.
- Validation fails when a vehicle route has fewer than two coordinates.

REST tests:

- `GET /api/collectors/routes/saved/{id}/map` returns 200 for generated maps.
- Missing route returns 404.
- Invalid vehicle index returns 400.
- Provider failure maps to 502/504.

Infrastructure tests:

- OpenRouteService adapter sends `POST /v2/directions/driving-car/geojson`.
- Adapter sends coordinates in `[longitude, latitude]` order.
- Adapter sends authorization header from configuration.
- Route map repository maps raw GeoJSON without losing fields.

Documentation Impact
--------------------

Update README after implementation with:

- The new map endpoint.
- `OPENROUTESERVICE_API_KEY` and optional base URL/timeout variables.
- MongoDB collection `route_maps`.
- Operational note that OpenRouteService calls are cached by vehicle route fingerprint.
