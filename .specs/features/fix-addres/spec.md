# Feature: fix-addres

Problem
-------
The Register microservice exposes Address as a value object without a unique id or lat/long. Collections service receives addresses without coordinates and must verify the address exists and obtain lat/long to perform nearby-collector discovery.

Goal
----
Provide a reliable validation + enrichment flow that confirms an address exists (by best-effort match) and fetches latitude/longitude using free Brazilian address APIs when possible.

Constraints & Assumptions
------------------------
- Addresses originate in Brazil (CEP + street + number + city/state available in many cases).
- This feature applies only to the addresses-sync topic; collectors-sync logic must not be changed by this work.
- External geocoding will use OpenStreetMap Nominatim as the primary source; ViaCEP validation is optional and not mandatory.
- Respect rate limits and usage policies (cache results, add timeouts, and set a configurable User-Agent).
- JWT auth already planned for service; address enrichment will be internal call within the service logic.

Acceptance Criteria
-------------------
AC-1: If incoming address already contains lat/long, no enrichment is performed and the coordinates are accepted.
AC-2: If CEP and number are provided but no lat/long, service must first call ViaCEP to enrich/normalize address (city/state/street) and then geocode the normalized address with Nominatim to obtain lat/long; ViaCEP enrichment is required for addresses-sync.
AC-3: If enrichment succeeds, the CollectionRequest persisted to MongoDB includes lat/long and enrichment metadata (source, timestamp).
AC-4: If enrichment fails, the request is flagged with status ADDRESS_UNVERIFIED (or similar) and a meaningful error is returned to the caller; failures are retriable later.
AC-5: Enriched addresses are cached in a new MongoDB collection to avoid repeated external calls within TTL.

Traceability IDs
-----------------
- FA-001: Accept coordinates if present
- FA-002: Enrich and validate address via ViaCEP (when CEP available)
- FA-003: Geocode with Nominatim and attach lat/long
- FA-004: Cache enriched addresses
- FA-005: Mark unresolved addresses and expose retry path

Notes
-----
- Nominatim usage policy forbids heavy production usage of the public instance — recommend adding a warning in docs and configuration to switch to a paid geocoding provider or self-hosted instance for production.
- Use descriptive User-Agent and contact email header when calling Nominatim.
