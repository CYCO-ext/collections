# Tasks: google-geocoding

Overview
--------
Atomic implementation tasks for switching address coordinate enrichment from Nominatim to Google Geocoding API while preserving ViaCEP normalization, duplicate reuse, cache reuse, and existing address processing behavior.

Implementation Status
---------------------

- Status: Completed
- Provider: Google Geocoding API
- Endpoint: `GET /maps/api/geocode/json`
- Required secret: `GOOGLE_GEOCODING_API_KEY`
- Query rule: include all available address data: street, number, city, state, ZIP code, country
- Cache rule: reuse existing address cache before provider calls

Phase 1 - Contract and Configuration
------------------------------------

T0 - Confirm enrichment replacement contract

- ID: google-geocoding-T0
- Status: Completed
- Traceability: GG-001, GG-004, GG-005, GG-006
- Depends on: none
- What: Confirm Google becomes the active coordinate provider in `AddressEnrichmentAdapter`, while ViaCEP normalization, duplicate detection, and cache reuse remain unchanged.
- Where: `.specs/features/google-geocoding/spec.md`, `.specs/features/google-geocoding/design.md`.
- Done: Spec and design agree that only coordinate lookup switches from Nominatim to Google.
- Tests: Documentation review.
- Gate: Feature docs consistently describe the same flow.

T1 - Add Google Geocoding configuration

- ID: google-geocoding-T1
- Status: Completed
- Traceability: GG-003
- Depends on: T0
- What: Add application properties for Google base URL, API key, timeout, and default country.
- Where: `src/main/resources/application.properties`.
- Done: `GOOGLE_GEOCODING_API_KEY`, `GOOGLE_GEOCODING_BASE_URL`, `GOOGLE_GEOCODING_TIMEOUT_MS`, and `GOOGLE_GEOCODING_COUNTRY` are supported without hardcoded secrets.
- Tests: Configuration can be overridden in tests.
- Gate: IDE compilation passes.

Phase 2 - Google Client
-----------------------

T2 - Add Google geocoding request/result model if useful

- ID: google-geocoding-T2
- Status: Completed
- Traceability: GG-001, GG-002
- Depends on: T0
- What: Add small records/classes for formatted address input and coordinate result if this keeps the client API clearer than `Optional<double[]>`.
- Where: `src/main/java/org/example/infrastructure/address/GoogleGeocodingClient.java` or adjacent model file.
- Done: Client can receive structured address data or a formatted address without depending on external DTOs.
- Tests: Covered by Google client tests.
- Gate: IDE compilation passes.

T3 - Implement GoogleGeocodingClient

- ID: google-geocoding-T3
- Status: Completed
- Traceability: GG-001, GG-002, GG-003, GG-007
- Depends on: T1, T2
- What: Implement HTTP client for `GET /maps/api/geocode/json`, including formatted address creation, URL encoding, API key query parameter, timeout, response parsing, and failure handling.
- Where: `src/main/java/org/example/infrastructure/address/GoogleGeocodingClient.java`.
- Done: Client returns coordinates from `results[0].geometry.location.lat/lng` on `OK`, returns empty for no result or provider failure, and never logs the API key.
- Tests: `GoogleGeocodingClientTest` covers request path, query data, key parameter, OK parsing, ZERO_RESULTS, provider errors, HTTP errors, invalid JSON, and missing API key behavior.
- Gate: Focused client test passes.

T4 - Verify full address query construction

- ID: google-geocoding-T4
- Status: Completed
- Traceability: GG-002
- Depends on: T3
- What: Add tests proving street, number, city, state, ZIP code, and country are present in the Google `address` query when available.
- Where: `src/test/java/org/example/infrastructure/address/GoogleGeocodingClientTest.java`.
- Done: Test fails if number or ZIP code is omitted from the encoded address query.
- Tests: Google client request mapping test.
- Gate: Focused client test passes.

Phase 3 - Enrichment Flow Replacement
-------------------------------------

T5 - Replace Nominatim injection with Google client

- ID: google-geocoding-T5
- Status: Completed
- Traceability: GG-001, GG-004
- Depends on: T3
- What: Inject `GoogleGeocodingClient` into `AddressEnrichmentAdapter` and remove Nominatim from the active enrichment path.
- Where: `src/main/java/org/example/infrastructure/address/AddressEnrichmentAdapter.java`.
- Done: `enrichCoordinates` calls Google after cache miss; Nominatim is not called by address enrichment.
- Tests: Address enrichment tests mock Google client instead of Nominatim.
- Gate: IDE compilation passes.

T6 - Preserve ViaCEP normalization before Google geocoding

- ID: google-geocoding-T6
- Status: Completed
- Traceability: GG-002, GG-004
- Depends on: T5
- What: Ensure Google receives fields after ViaCEP has filled missing street, city, state, or normalized ZIP code.
- Where: `src/main/java/org/example/infrastructure/address/AddressEnrichmentAdapter.java`, `src/test/java/org/example/infrastructure/address/AddressEnrichmentAdapterTest.java`.
- Done: Test demonstrates an event with ZIP code and number can be normalized by ViaCEP before Google lookup.
- Tests: Address enrichment adapter test.
- Gate: Focused adapter test passes.

T7 - Update enrichment source/status semantics

- ID: google-geocoding-T7
- Status: Completed
- Traceability: GG-008
- Depends on: T5
- What: Replace `nominatim` source strings with `google-geocoding` and `viacep+google-geocoding` while preserving `provided` and `cache`.
- Where: `src/main/java/org/example/infrastructure/address/AddressEnrichmentAdapter.java`.
- Done: Success and no-result paths set the correct source and status.
- Tests: Address enrichment adapter success and no-result tests.
- Gate: Focused adapter test passes.

T8 - Preserve duplicate and cache short-circuit behavior

- ID: google-geocoding-T8
- Status: Completed
- Traceability: GG-005, GG-006
- Depends on: T5
- What: Verify duplicate address reuse skips Google and cache lookup, and cache hit skips Google.
- Where: `src/test/java/org/example/infrastructure/address/AddressEnrichmentAdapterTest.java`.
- Done: Tests use `verify(googleGeocodingClient, never())` for duplicate and cache-hit paths.
- Tests: Address enrichment adapter tests.
- Gate: Focused adapter test passes.

Phase 4 - Legacy Cleanup and Docs
---------------------------------

T9 - Remove or isolate Nominatim from active configuration

- ID: google-geocoding-T9
- Status: Completed
- Traceability: GG-004, GG-010
- Depends on: T5
- What: Remove Nominatim from active README/config references, or clearly mark it legacy if the class remains for future fallback.
- Where: `src/main/resources/application.properties`, `README.md`, optionally `NominatimClient.java` and `NominatimClientTest.java`.
- Done: Operational docs do not tell deployers to configure Nominatim for coordinate enrichment.
- Tests: Compilation and documentation review.
- Gate: No references imply Nominatim is the active enrichment provider.

T10 - Update README

- ID: google-geocoding-T10
- Status: Completed
- Traceability: GG-003, GG-010
- Depends on: T1, T5, T7
- What: Document Google Geocoding API configuration, required API key, optional base URL/timeout/country, ViaCEP normalization, cache behavior, and provider source values.
- Where: `README.md`.
- Done: README explains how to configure `GOOGLE_GEOCODING_API_KEY` in deployment.
- Tests: Documentation review.
- Gate: README matches implemented properties and behavior.

Phase 5 - Verification
----------------------

T11 - Update and run focused tests

- ID: google-geocoding-T11
- Status: Completed
- Traceability: GG-009
- Depends on: T3 through T8
- What: Run focused tests for Google client and address enrichment after updating mocks from Nominatim to Google.
- Where: Maven/IDE test runner.
- Done: `GoogleGeocodingClientTest` and `AddressEnrichmentAdapterTest` pass.
- Tests: Focused tests.
- Gate: No focused test failures.

T12 - Run compilation/build check

- ID: google-geocoding-T12
- Status: Completed
- Traceability: GG-009
- Depends on: T1 through T11
- What: Run IDE compilation or project build to catch removed injection/config references.
- Where: IDE build or Maven.
- Done: No compilation errors.
- Tests: Build/compilation check.
- Gate: No compilation errors.

Parallelization Notes
---------------------

- T1 and T2 can run after T0.
- T3 depends on T1/T2.
- T4 can be written alongside T3.
- T5, T6, T7, and T8 are tightly related and should be implemented together to avoid inconsistent tests.
- T9 and T10 should happen after the enrichment path is changed.
- T11 and T12 are final verification gates.
