# Design: Address validation & enrichment

Overview
--------
Use a shared AddressEnrichment component in the infrastructure layer to normalize, deduplicate, enrich, and persist addresses received from Register sync topics.

The component composes external calls:

- ViaCEP (`https://viacep.com.br/ws/{cep}/json/`) — when CEP is available, required to normalize address fields such as street, city, and state.
- Nominatim (OpenStreetMap) — geocoding source used to convert a normalized address into latitude/longitude when coordinates were not supplied by the producer.

The shared flow is consumed by:

- `addresses-sync`: standalone address events.
- `collector-sync`: collector events that include address data requiring the same enrichment and deduplication guarantees.

Where to implement
------------------

Existing/shared components:

- Service interface: `org.example.application.port.out.AddressEnrichmentPort`
- Implementation: `org.example.infrastructure.address.AddressEnrichmentAdapter`
- External clients: `org.example.infrastructure.address.ViacepClient`, `org.example.infrastructure.address.NominatimClient`
- Address repository: repository responsible for `collection_addresses`
- Cache repository: repository responsible for MongoDB collection `address_cache`

New collector integration points:

- `SyncEventConsumer.consumeCollector` must map the embedded collector address into the shared address enrichment input.
- Collector persistence must store the resulting Address ID/reference returned by `AddressEnrichmentPort`.
- Collector address handling must use the same duplicate lookup as `addresses-sync`; no collector-specific duplicate rules should be introduced unless required by the data model.

Shared Enrichment Flow
----------------------

1. Input: address data from either `SyncAddressEvent` or the address section of `SyncCollectorEvent`.
2. Normalize raw fields: trim, normalize CEP formatting, normalize casing where repository queries expect it, and preserve the original street number.
3. If CEP is present, call ViaCEP to enrich/normalize street, city, and state.
4. Build the duplicate key from normalized `(zipCode, street, number, city, state)`.
5. Query `collection_addresses` for an existing address with the same duplicate key.
6. If found, return the existing address and skip address creation. If the existing address has coordinates, skip external geocoding.
7. If no duplicate exists, generate an address ID when producer did not provide one. Prefer deterministic ID generation from the normalized duplicate key; use UUID only when the key is incomplete.
8. If latitude/longitude are present in the incoming payload, persist them with enrichment source `provided`.
9. If latitude/longitude are missing, check `address_cache` with a normalized key containing CEP, street, number, city, and state.
10. If cache hit is valid, populate coordinates and metadata from cache.
11. If cache miss, build a Nominatim query such as `street number, city, state, Brazil` and call Nominatim with `format=json&limit=1`.
12. If Nominatim returns coordinates, persist them to `collection_addresses`, upsert `address_cache`, and mark status `ENRICHED`.
13. If Nominatim returns no result or external calls fail, persist the address with status `ADDRESS_UNVERIFIED` and no duplicate document retry loop.
14. Return the persisted/reused Address to the caller.

Consumer Behavior
-----------------

`addresses-sync`:

- Convert the standalone sync address payload to the shared enrichment input.
- Persist the returned Address document or reuse the returned existing Address ID.

`collector-sync`:

- Extract the collector address payload.
- Call the same `AddressEnrichmentPort` before collector persistence.
- Persist or update the collector record with the returned Address ID/reference.
- Do not persist embedded collector address data as a separate, unnormalized collector-only address when the shared Address flow succeeds.
- If address enrichment fails open with `ADDRESS_UNVERIFIED`, still link the collector to the persisted unverified Address so later retry/backfill can repair coordinates.

Reliability & Ops
-----------------

Configuration properties:

- `enrichment.enabled` (boolean)
- `nominatim.endpoint` (default public Nominatim URL)
- `viacep.enabled` (boolean, default true)
- `viacep.endpoint` (default ViaCEP URL)
- `enrichment.cache.ttl` (seconds)
- `enrichment.timeout.ms`
- `enrichment.max-retries`
- `enrichment.user-agent`

Operational behavior:

- Include a descriptive User-Agent header and configured contact where supported.
- Respect Nominatim usage policy.
- Use cache before external geocoding for both sync topics.
- Fail open by persisting `ADDRESS_UNVERIFIED` instead of rejecting sync messages.
- Keep external-call logs at debug level and avoid logging full raw payloads.

Data Model
----------

MongoDB collection: `collection_addresses` (primary storage)

- `_id`: generated deterministic ID or UUID fallback
- `street`: string, persisted primary field
- `number`: string, persisted primary field
- `city`: string, persisted primary field
- `state`: string, persisted primary field
- `zipCode`: string
- `latitude`: double, nullable
- `longitude`: double, nullable
- `enrichment_status`: string, values `ENRICHED`, `ADDRESS_UNVERIFIED`, `PENDING`
- `enrichment_source`: string, examples `viacep+nominatim`, `nominatim`, `cache`, `provided`
- `enrichment_timestamp`: timestamp, nullable
- `created_at`: timestamp
- `updated_at`: timestamp

Indexes:

- Compound unique index on `(zipCode, street, number, city, state)` for duplicate prevention.

MongoDB collection: `address_cache` (TTL-based enrichment cache)

- `_id`: normalized cache key, e.g. `cep+number+street+city+state`
- `cep`: string
- `street`: string
- `number`: string
- `city`: string
- `state`: string
- `lat`: double
- `lon`: double
- `source`: string
- `created_at`: timestamp
- `updated_at`: timestamp

Indexes:

- TTL index on `created_at`, configured by `enrichment.cache.ttl`.

Testing
-------

- Unit tests for ViaCEP and Nominatim clients using mocked HTTP responses.
- Unit tests for `AddressEnrichmentAdapter` covering duplicate reuse, provided coordinates, cache hit, geocoding success, and failure-to-unverified behavior.
- Consumer tests for `addresses-sync` verifying the shared enrichment port is invoked.
- Consumer tests for `collector-sync` verifying the shared enrichment port is invoked and the returned Address ID is stored on the collector.
- Regression test proving repeated `collector-sync` messages with the same address do not create duplicate address documents.

Security & Privacy
------------------

- Send only address fields required for geocoding.
- Do not log raw external responses at info/error level.
- Do not expose enrichment internals through public API responses unless already part of the Address model contract.

Production note
---------------

Public Nominatim is not suitable for high QPS. Keep provider configuration and documentation clear so production can switch to a paid geocoding provider or self-hosted Nominatim.
