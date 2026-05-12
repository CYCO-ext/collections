# Feature: collector-address

Problem
-------
Clients that work with collectors need a direct way to retrieve the address associated with a collector. Today address data exists in the domain and is used internally for discovery and route planning, but there is no dedicated read endpoint for collector address details.

Goal
----
Add a read-only endpoint that returns address information for one collector.

The endpoint should load the collector by id, resolve the collector address, and return a stable address response containing the address id, street, number, city, state, zipCode, latitude, longitude, enrichmentStatus, and enrichmentSource.

Scope
-----

In scope:

- Collector-facing REST endpoint to retrieve a collector's address.
- Application use case dedicated to collector address lookup.
- Reuse `CollectorDiscoveryPort.findCollectorById(collectorId)`.
- Reuse `AddressPort.findById(addressId)` when the collector stores only an address reference or when the embedded address must be refreshed.
- Stable response DTO/model for collector address details.
- Validation for missing/blank collector id.
- HTTP 404 behavior for missing collector or missing collector address.
- Tests for success, validation, collector not found, missing address, and REST response shape.

Out of scope for the first implementation:

- Updating collector addresses.
- Address enrichment or geocoding.
- Searching collectors by address.
- Returning full collector profile or accepted materials beyond identifiers needed for context.
- Authorization scoping beyond the project's existing security approach.

Constraints & Assumptions
-------------------------

- The service is Java 21 with Quarkus, Mutiny, and reactive MongoDB.
- Runtime routes include the global `/api` prefix from `quarkus.rest.path=/api`.
- `Collector` currently contains an `Address` object, not just an address id.
- `AddressPort.findById(id)` exists and can be used when the collector address has an id.
- If the collector contains a complete embedded address, the MVP may return it directly.
- If the collector address is null, or address lookup by id returns null when required, the endpoint should return HTTP 404.
- The endpoint is read-only and must not mutate collector or address records.

Acceptance Criteria
-------------------

AC-1: A client can call `GET /api/collectors/{collectorId}/address` to retrieve a collector's address.

AC-2: When the collector exists and has a usable address, the endpoint returns HTTP 200 with address details.

AC-3: The response includes `collectorId`, `addressId`, `street`, `number`, `city`, `state`, `zipCode`, `latitude`, `longitude`, `enrichmentStatus`, and `enrichmentSource`.

AC-4: Blank collector ids fail validation with HTTP 400 and a clear message.

AC-5: Missing collector ids return HTTP 404.

AC-6: Collectors without an address return HTTP 404.

AC-7: The use case delegates to collector/address ports and does not mutate collector or address data.

AC-8: Tests cover successful lookup, blank collector id, collector not found, missing address, REST response shape, and route behavior.

Traceability IDs
----------------

- CAD-001: Collector address endpoint
- CAD-002: Collector id validation
- CAD-003: Collector lookup through port
- CAD-004: Address resolution through embedded address or address port
- CAD-005: Stable address response model
- CAD-006: Not-found error behavior
- CAD-007: Read-only behavior
- CAD-008: Focused unit and REST test coverage
- CAD-009: API documentation

Notes
-----

- Suggested endpoint: `GET /collectors/{collectorId}/address`, exposed as `GET /api/collectors/{collectorId}/address` at runtime.
- The response should include address enrichment metadata because existing address synchronization/enrichment already tracks it.
