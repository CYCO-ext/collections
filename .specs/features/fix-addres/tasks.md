# Tasks: fix-addres

Overview
--------
Atomic tasks to implement address validation & enrichment. Medium scope — create code, tests, config, and docs. Tasks are ordered and small; many can be executed in parallel once interfaces are defined.

T1 — Add port & adapters skeleton
- ID: fix-addres-T1
- What: Create AddressEnrichmentPort interface and AddressEnrichmentAdapter class with method enrich(AddressVO): Uni<AddressVO>
- Where: application.port.out, infrastructure.address
- Done when: interface and adapter skeleton compile and are wired as CDI bean
- Tests: compile-time only

T2 — Create AddressCache repository
- ID: fix-addres-T2
- What: Add MongoDB repository and domain model for address_cache with TTL index
- Done when: collection created by application and repository CRUD has unit tests
- Tests: integration test using embedded MongoDB to save/read entries

T3 — Implement ViaCEP client
- ID: fix-addres-T3
- What: HTTP client for ViaCEP with timeout and error handling; returns normalized address fields (street, city, state) for a given CEP
- Done when: client returns normalized address DTO for a given cep in unit tests
- Tests: unit tests mocking ViaCEP responses

T4 — Implement Nominatim geocoding client
- ID: fix-addres-T4
- What: HTTP client calling Nominatim (with configurable endpoint and User-Agent) and parsing lat/lon
- Done when: client returns lat/lon for a crafted address in unit tests
- Tests: unit tests mocking Nominatim responses; test for 429 handling/backoff

T5 — Implement enrichment orchestrator
- ID: fix-addres-T5
- What: Implement orchestration: check coords, optionally normalize via ViaCEP (if enabled), check cache, geocode (Nominatim), persist cache, return enriched AddressVO
- Done when: end-to-end unit/integration test demonstrates enrichment success and cache writes
- Tests: integration test with mocked external endpoints

T6 — Integrate into CollectionRequestUseCase
- ID: fix-addres-T6
- What: Call enrichment before persisting new requests; handle ADDRESS_UNVERIFIED state on failure
- Done when: API tests for POST /api/generators/requests show enriched lat/long when possible and proper status when unresolved
- Tests: existing integration tests updated/extended

T7 — Add config, metrics & docs
- ID: fix-addres-T7
- What: Add config properties, metrics for enrichment calls, and docs (update feature spec and README)
- Done when: configuration values present in application.properties and README updated
- Tests: none (manual verification)

T8 — CI & mocks for external deps
- ID: fix-addres-T8
- What: Add WireMock or mocked clients in CI to validate behavior without external calls
- Done when: CI pipeline runs tests that rely on mocked external endpoints
- Tests: CI passing

Execution notes
---------------
- Respect Nominatim policy: set User-Agent and contact email via config
- Keep enrichment.enabled toggle for easier rollback

Optional enhancements
---------------------
- Bulk retry worker to re-enrich ADDRESS_UNVERIFIED requests
- Support alternative geocoding providers behind a provider interface

Estimated files touched
----------------------
- src/main/java/org/example/application/port/out/AddressEnrichmentPort.java
- src/main/java/org/example/infrastructure/address/AddressEnrichmentAdapter.java
- src/main/java/org/example/infrastructure/address/ViacepClient.java
- src/main/java/org/example/infrastructure/address/NominatimClient.java
- src/main/java/org/example/infrastructure/repository/AddressCacheRepository.java
- src/test/... (unit + integration tests)

Next step
---------
Start with T1 and T2 to establish interfaces and storage, then implement T3/T4 clients in parallel (P). After clients are ready, implement orchestrator T5 and integrate T6.
