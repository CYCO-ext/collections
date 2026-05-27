# Design: google-geocoding

Overview
--------
Replace the coordinate lookup portion of address enrichment with Google Geocoding API. The existing flow should remain recognizable:

1. Build `Address` from `SyncAddressEvent`.
2. Optionally normalize address fields through ViaCEP.
3. Generate deterministic address id.
4. Reuse duplicate addresses before external calls.
5. Reuse cached coordinates before external calls.
6. Call the coordinate provider only when coordinates are missing and cache miss occurs.
7. Persist/cache enrichment result with status and source.

The change is focused on replacing `NominatimClient` usage with a Google-specific client that receives a structured address input and builds a complete formatted address query.

Current Flow
------------

Current coordinate path in `AddressEnrichmentAdapter`:

```text
enrich(event)
  -> fromEvent
  -> normalizeWithViaCep
  -> findDuplicate
  -> enrichCoordinates
       -> existing coordinates: source=provided
       -> cache hit: source=cache
       -> enrichWithNominatim
```

Target coordinate path:

```text
enrich(event)
  -> fromEvent
  -> normalizeWithViaCep
  -> findDuplicate
  -> enrichCoordinates
       -> existing coordinates: source=provided
       -> cache hit: source=cache
       -> enrichWithGoogleGeocoding
```

Configuration
-------------

Add properties:

```properties
google.geocoding.base-url=${GOOGLE_GEOCODING_BASE_URL:https://maps.googleapis.com}
google.geocoding.api-key=${GOOGLE_GEOCODING_API_KEY:}
google.geocoding.timeout-ms=${GOOGLE_GEOCODING_TIMEOUT_MS:5000}
google.geocoding.country=${GOOGLE_GEOCODING_COUNTRY:Brazil}
```

Optional later property if provider switching is needed:

```properties
enrichment.geocoding-provider=${ENRICHMENT_GEOCODING_PROVIDER:google}
```

MVP should make Google the active provider directly because the feature request is to switch enrichment to Google.

Google Request
--------------

Outbound endpoint:

```text
GET {google.geocoding.base-url}/maps/api/geocode/json?address={encoded-address}&key={api-key}
```

Formatted address should include all available data in a stable order:

```text
{street}, {number}, {city}, {state}, {zipCode}, {country}
```

If all fields are present, example:

```text
Praça da Sé, 100, São Paulo, SP, 01001000, Brazil
```

If number is present, it must not be omitted. If a field is missing, skip only that field and keep the remaining fields ordered.

Implementation detail: introduce a focused request model so the client does not receive a domain entity directly if the project wants a clearer boundary:

```text
GoogleGeocodingAddress
- street
- number
- city
- state
- zipCode
- country
```

Response Mapping
----------------

Expected successful response shape:

```json
{
  "status": "OK",
  "results": [
    {
      "geometry": {
        "location": {
          "lat": -23.55052,
          "lng": -46.633308
        }
      }
    }
  ]
}
```

Client result model:

```text
GeocodingResult
- latitude
- longitude
- providerStatus
```

For the MVP, returning `Optional<double[]>` is acceptable if it keeps the diff small, but a named result record is preferred for readability and later metadata.

Status handling:

- `OK` with lat/lng: return coordinates.
- `ZERO_RESULTS`: return empty result; enrichment becomes `ADDRESS_UNVERIFIED`.
- `OVER_QUERY_LIMIT`, `OVER_DAILY_LIMIT`, `REQUEST_DENIED`, `INVALID_REQUEST`, `UNKNOWN_ERROR`: log provider status and return empty result for address processing continuity.
- Non-2xx HTTP status: log and return empty result.
- Invalid/missing JSON fields: log and return empty result.
- Timeout/network error: log and return empty result.
- Missing API key: return empty result with an explicit log or fail fast if a startup validation pattern already exists. MVP should avoid hard failure during tests by making missing-key behavior explicit.

AddressEnrichmentAdapter Changes
--------------------------------

Rename or replace methods:

```text
enrichWithNominatim(Address address, String cacheKey)
```

with:

```text
enrichWithGoogleGeocoding(Address address, String cacheKey)
```

Source values:

- Existing coordinates: `provided`.
- Cache hit: `cache`.
- ViaCEP + Google success/failure: `viacep+google-geocoding`.
- Google only success/failure: `google-geocoding`.

The existing `buildCacheKey(Address)` can stay unchanged because it already includes ZIP code, number, street, city, and state.

The existing `buildQuery(Address)` should either be renamed to `buildFormattedAddress(Address)` or moved into `GoogleGeocodingClient`. The query builder must preserve the number field.

Component Changes
-----------------

Application/infrastructure boundary remains lightweight because current enrichment clients are infrastructure classes injected directly into `AddressEnrichmentAdapter`.

New infrastructure class:

```text
src/main/java/org/example/infrastructure/address/GoogleGeocodingClient.java
```

Responsibilities:

- Read Google geocoding config.
- Build URL with encoded formatted address and API key.
- Send GET request with timeout.
- Parse Google JSON response.
- Return coordinates or empty result.
- Log provider status and failure context without logging API key.

Changed infrastructure class:

```text
src/main/java/org/example/infrastructure/address/AddressEnrichmentAdapter.java
```

Responsibilities:

- Inject `GoogleGeocodingClient` instead of `NominatimClient` for coordinates.
- Call Google geocoding after cache miss.
- Set source/status values consistently.

Legacy class handling:

```text
src/main/java/org/example/infrastructure/address/NominatimClient.java
```

Options:

- Leave unused for now if removal would expand the feature unnecessarily.
- Remove only after confirming no other code references it.

Recommended MVP: remove from enrichment path and tests, but do not delete the class unless no references remain and tests are updated cleanly.

Testing Strategy
----------------

Unit tests for `GoogleGeocodingClient`:

- Sends request to `/maps/api/geocode/json`.
- Encodes full address including street, number, city, state, ZIP code, and country.
- Includes API key as `key` query parameter.
- Parses `OK` response latitude/longitude from `geometry.location`.
- Returns empty for `ZERO_RESULTS`.
- Returns empty for provider status errors.
- Returns empty for non-2xx HTTP status.
- Returns empty for invalid JSON.

Unit tests for `AddressEnrichmentAdapter`:

- Existing coordinates skip Google and source is `provided`.
- Duplicate address skips Google and cache lookup.
- Cache hit skips Google and source is `cache`.
- ViaCEP-normalized fields are sent to Google after cache miss.
- Google success sets coordinates, `ENRICHED`, and source `viacep+google-geocoding` or `google-geocoding`.
- Google empty result sets `ADDRESS_UNVERIFIED` and provider source.
- Cache upsert uses the final address and provider source.

Regression tests:

- Existing tests that mock `NominatimClient` should be updated to mock `GoogleGeocodingClient`.
- `NominatimClientTest` can remain if class remains, but it should no longer be part of the acceptance path.

Documentation Impact
--------------------

Update README with:

- `GOOGLE_GEOCODING_API_KEY` required for coordinate enrichment.
- Optional `GOOGLE_GEOCODING_BASE_URL`, `GOOGLE_GEOCODING_TIMEOUT_MS`, and `GOOGLE_GEOCODING_COUNTRY`.
- Note that ViaCEP may still normalize Brazilian address fields before Google geocoding.
- Note that cached coordinates prevent repeated Google calls for the same normalized address.

Security and Operations
-----------------------

- Never log the Google API key.
- Configure API key in Cloud Run/GCP environment variables or secret manager.
- Restrict API key in Google Cloud where possible.
- Monitor quota and billing for Geocoding API usage.
- Cache hits reduce repeated provider calls.
