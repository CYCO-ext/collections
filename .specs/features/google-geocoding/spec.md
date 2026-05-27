# Feature: google-geocoding

Problem
-------
Address coordinate enrichment currently uses Nominatim after optional ViaCEP normalization. This can produce inconsistent results and depends on a public OpenStreetMap service that is not ideal for production address geocoding.

The service needs to switch coordinate enrichment to Google Geocoding API and send all available address data, including street, city, ZIP code, and number, so generated latitude/longitude values are based on a complete address query.

Goal
----
Use Google Geocoding API as the coordinate provider for address enrichment.

The enrichment flow should keep existing address normalization, duplicate detection, and cache behavior, but replace the Nominatim coordinate lookup with Google Geocoding. Requests to Google must include a complete formatted address built from number, street, city, state, ZIP code, and country when available.

Scope
-----

In scope:

- Add Google Geocoding API configuration using environment variables.
- Add a Google Geocoding client/adapter for coordinate lookup.
- Build geocoding requests from all address fields: street, city, ZIP code, number, state, and country.
- Keep ViaCEP as optional field normalization before geocoding unless explicitly disabled by config.
- Replace Nominatim usage in `AddressEnrichmentAdapter` with Google Geocoding.
- Preserve cache lookup and cache write behavior using the existing normalized address cache key.
- Preserve duplicate address reuse before external geocoding.
- Update enrichment source values from `nominatim` to `google-geocoding` variants.
- Handle Google zero results, denied requests, invalid API key, quota/rate errors, server errors, and timeouts.
- Add tests for request mapping, response parsing, enrichment integration, cache reuse, duplicate reuse, and failure behavior.
- Update README with Google Geocoding env vars and operational notes.

Out of scope:

- Removing ViaCEP normalization.
- Replacing existing address storage schema.
- Batch geocoding.
- Reverse geocoding.
- Places API usage.
- Frontend changes.
- Data migration for already persisted coordinates.
- Provider failover to Nominatim unless explicitly added later.

Constraints & Assumptions
-------------------------

- Runtime is Quarkus Java and currently uses `AddressEnrichmentAdapter` for Kafka address sync enrichment.
- Existing `Address` fields are `street`, `city`, `zipCode`, `number`, `state`, `latitude`, `longitude`, `enrichmentStatus`, and `enrichmentSource`.
- ViaCEP may fill street, city, state, and normalized ZIP code before geocoding.
- Google Geocoding API key must be supplied through environment configuration and must not be hardcoded.
- Google Geocoding response coordinates are returned as `geometry.location.lat` and `geometry.location.lng`.
- Address query must include the house number when provided. If number is absent, geocoding may still run with the remaining address data.
- Cache keys should continue to be based on normalized address fields so repeated address events do not call Google again.
- Existing coordinates supplied by the event remain authoritative and should not trigger Google geocoding.
- If Google cannot return coordinates, the address should remain saved with `ADDRESS_UNVERIFIED` and no partial coordinates.
- Provider errors should not prevent the service from processing the address event unless the existing flow already treats enrichment failures as fatal.

Acceptance Criteria
-------------------

AC-1: Address coordinate enrichment uses Google Geocoding API instead of Nominatim.

AC-2: The Google request contains all available address data: street, number, city, state, ZIP code, and country.

AC-3: The Google API key is read from `GOOGLE_GEOCODING_API_KEY` and is never committed.

AC-4: The Google endpoint/base URL and timeout are configurable for tests and deployment.

AC-5: If an address already has latitude and longitude, the service skips Google and marks the source as `provided`.

AC-6: If a duplicate address already exists, the service reuses it and does not call Google.

AC-7: If a cached address has coordinates, the service reuses the cached coordinates and does not call Google.

AC-8: If Google returns a valid result, the service stores latitude and longitude on the address and writes the result to the cache.

AC-9: If Google returns `ZERO_RESULTS`, the address is marked `ADDRESS_UNVERIFIED` and cached with source `google-geocoding` or `viacep+google-geocoding`.

AC-10: Google provider failures, quota/rate errors, denied requests, invalid API key errors, server errors, invalid responses, and timeouts are mapped to clear behavior and logs.

AC-11: Existing Nominatim client usage is removed from the enrichment path, and tests no longer mock Nominatim for address enrichment.

AC-12: README documents the new Google configuration and deployment requirement.

Traceability IDs
----------------

- GG-001: Google Geocoding integration
- GG-002: Complete address query construction
- GG-003: Configuration and secrets
- GG-004: Address enrichment flow replacement
- GG-005: Cache preservation
- GG-006: Duplicate preservation
- GG-007: Provider error handling
- GG-008: Enrichment source/status semantics
- GG-009: Test coverage
- GG-010: Documentation

Reference
---------

Google Geocoding API address geocoding endpoint: `GET /maps/api/geocode/json?address={formatted-address}&key={api-key}`.
