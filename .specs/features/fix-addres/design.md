# Design: Address validation & enrichment

Overview
--------
Add an AddressEnrichment component in the infrastructure layer to validate and enrich addresses received from the Register microservice. The component composes external calls:
- ViaCEP (https://viacep.com.br/ws/{cep}/json/) — when CEP is available, required to enrich/normalize address fields (city, state) for addresses-sync
- Nominatim (OpenStreetMap) — geocoding source to convert the normalized address to lat/long

Where to implement
------------------
- New package: org.example.infrastructure.address (clients + repository)
- Service interface: org.example.application.port.out.AddressEnrichmentPort
- Implementation: org.example.infrastructure.address.AddressEnrichmentAdapter
- Cache repository: org.example.infrastructure.repository.AddressCacheRepository (MongoDB collection: address_cache)

Enrichment Flow
---------------
1. Input: AddressVO (may include cep, street, number, city, state, lat, long)
2. If lat/long present -> return input (FA-001)
3. Normalize: when CEP is present, query ViaCEP to enrich street/city/state (ViaCEP enrichment is required for addresses-sync); otherwise normalize using provided fields
4. Check cache (key: normalizedAddress or cep+number). If found and not expired -> return cached lat/long
5. Build geocoding query string: "street number, city, state, Brazil" and call Nominatim with format=json&limit=1
6. If Nominatim returns result -> persist to cache (with source metadata) and return enriched address
7. If Nominatim returns no result or error -> mark address unresolved and return a controlled error allowing retry

Reliability & Ops
-----------------
- Add configuration properties:
  - enrichment.enabled (boolean)
  - nominatim.endpoint (default public Nominatim URL)
  - viacep.enabled (boolean, default true)  # ViaCEP enrichment is enabled by default for addresses-sync
  - viacep.endpoint (default ViaCEP URL)
  - enrichment.cache.ttl (seconds)
  - enrichment.timeout.ms
  - enrichment.max-retries
  - enrichment.user-agent
- Respect Nominatim usage policy: include User-Agent header and email contact
- Implement simple rate-limiting (per-process) and exponential backoff on 429/5xx
- Add circuit breaker (fail open after repeated errors) to avoid saturating external services

Data Model
----------
New collection: address_cache
- _id: normalized_key (string)
- cep: string
- street: string
- number: string
- city: string
- state: string
- lat: double
- lon: double
- source: string (nominatim|other)
- created_at, updated_at
- ttl index on created_at (expireAfterSeconds configured via enrichment.cache.ttl)

Integration Points
------------------
- CollectionRequestUseCase: call AddressEnrichmentPort before persisting a new request (or as a pre-persist step)
- API behavior: POST /api/generators/requests should attempt enrichment synchronously (configurable) and return enriched request or flag ADDRESS_UNVERIFIED

Testing
-------
- Unit tests for clients using mocked HTTP responses
- Integration tests that spin up embedded MongoDB and mock external endpoints (or use WireMock)
- Contract tests for ViaCEP/Nominatim behavior

Security & Privacy
------------------
- Do not send user PII beyond what is necessary for geocoding
- Log external calls at debug level only; avoid storing raw responses containing sensitive data

Production note
---------------
- Public Nominatim instance is not suitable for high QPS; recommend a paid geocoding provider or self-hosted Nominatim in production. Add an environment flag to switch providers.
