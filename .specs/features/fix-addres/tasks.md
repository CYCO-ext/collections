# Tasks: fix-addres

Overview
--------
Atomic tasks for address validation and enrichment across Register sync topics.

Phase 1 implemented the shared address enrichment flow for `addresses-sync`. Phase 2 extends the same flow to `collector-sync` and is now implemented.

Completed Phase 1
-----------------

T0 — Update Address entity and SyncAddressEvent

- ID: fix-addres-T0
- Status: Completed
- Traceability: FA-001, FA-002, FA-009
- What: Add `number`, `state`, `enrichmentStatus`, and `enrichmentSource` fields to Address. Update SyncAddressEvent to include `number`.
- Done: Address entity, SyncAddressEvent, and repository mapping compile with the new fields.

T1 — Add port & adapters skeleton + duplicate detection

- ID: fix-addres-T1
- Status: Completed
- Traceability: FA-001, FA-003
- What: Create AddressEnrichmentPort and AddressEnrichmentAdapter. Generate an address id when the producer does not send one. Detect duplicates by `(zipCode, street, number, city, state)` before geocoding/persisting a new address.
- Done: The adapter calls `AddressRepository.findDuplicate(...)` after ViaCEP normalization and reuses the existing address when found.

T2 — Create AddressCache repository

- ID: fix-addres-T2
- Status: Completed
- Traceability: FA-007
- What: Add MongoDB repository for `address_cache`; cache key is `cep+number+street+city+state`.
- Done: AddressCacheRepository supports lookup/upsert and stores street, number, city, state, latitude, longitude, source, and TTL-compatible timestamps.

T3 — Implement ViaCEP client

- ID: fix-addres-T3
- Status: Completed
- Traceability: FA-005
- What: HTTP client for ViaCEP with configurable endpoint, timeout, User-Agent, and error handling.
- Done: ViacepClient returns normalized CEP, street, city, and state. Unit test covers mocked ViaCEP response parsing.

T4 — Implement Nominatim geocoding client

- ID: fix-addres-T4
- Status: Completed
- Traceability: FA-006
- What: HTTP client calling Nominatim with configurable endpoint and User-Agent, parsing latitude/longitude.
- Done: NominatimClient parses mocked coordinate responses and handles 429 responses as empty results.

T5 — Implement enrichment orchestrator

- ID: fix-addres-T5
- Status: Completed
- Traceability: FA-001, FA-003, FA-004, FA-005, FA-006, FA-007, FA-008, FA-009
- What: Orchestrate ViaCEP normalization, deterministic id generation, duplicate detection, cache lookup, Nominatim geocoding, cache writes, and enrichment status/source assignment.
- Done: AddressEnrichmentAdapter implements the full flow. Unit tests verify success and duplicate-reuse paths.

T6 — Integrate into addresses-sync consumer

- ID: fix-addres-T6
- Status: Completed
- Traceability: FA-001 through FA-009
- What: Update `addresses-sync` consumer to call enrichment and persist enriched addresses.
- Done: `consumeAddress` calls AddressEnrichmentPort and persists the returned enriched/reused address.

T7 — Add config, migration, and docs

- ID: fix-addres-T7
- Status: Completed
- Traceability: FA-003, FA-007
- What: Add config properties, MongoDB index migration, and README documentation.
- Done: application.properties includes enrichment, ViaCEP, and Nominatim configuration; db-migration.js creates duplicate and TTL indexes; README describes the flow and topics.

T8 — CI & mocks for external deps

- ID: fix-addres-T8
- Status: Completed locally
- Traceability: FA-005, FA-006
- What: Add mocked external dependency tests so behavior does not depend on real ViaCEP/Nominatim calls.
- Done: Unit tests use local HTTP mocks for clients and Mockito for orchestration. CI workflow changes were not needed in this repository because tests run through the existing Maven test lifecycle.

Completed Phase 2 — collector-sync enrichment
---------------------------------------------

T9 — Map collector-sync address into shared enrichment input

- ID: fix-addres-T9
- Status: Completed
- Traceability: FA-010
- Depends on: T5
- What: Inspect `SyncCollectorEvent` and collector consumer mapping. Add or adjust a mapper so the embedded collector address is converted into the same input shape used by `addresses-sync` enrichment.
- Where: Sync event DTOs, collector sync consumer, address enrichment input factory/mapper if present.
- Done: `collector-sync` can provide street, number, city, state, zipCode, latitude, and longitude to AddressEnrichmentPort without duplicating enrichment logic.
- Tests: Unit test for collector event address mapping, including `number`.
- Gate: IDE compilation check passes.

T10 — Invoke AddressEnrichmentPort from collector-sync

- ID: fix-addres-T10
- Status: Completed
- Traceability: FA-010, FA-011
- Depends on: T9
- What: Update `consumeCollector` so collector address data calls the shared AddressEnrichmentPort before collector persistence.
- Where: SyncEventConsumer or the collector sync application service used by it.
- Done: `collector-sync` invokes the same enrichment/deduplication flow as `addresses-sync`; no collector-specific ViaCEP/Nominatim calls are introduced.
- Tests: Consumer/service test verifies AddressEnrichmentPort is called once when collector payload contains address data.
- Gate: IDE compilation check passes.

T11 — Persist collector with returned Address ID/reference

- ID: fix-addres-T11
- Status: Completed
- Traceability: FA-011
- Depends on: T10
- What: Store the Address ID returned by AddressEnrichmentPort on the collector record, reusing the existing collector address reference model.
- Where: Collector entity/model, collector repository mapping, collector sync persistence path.
- Done: Collector persistence references the enriched or reused Address ID. If enrichment returns `ADDRESS_UNVERIFIED`, the collector still links to that persisted Address.
- Tests: Repository or service test proves a collector persisted from sync has the returned Address ID/reference.
- Gate: IDE compilation check passes.

T12 — Prevent duplicate addresses from repeated collector-sync events

- ID: fix-addres-T12
- Status: Completed
- Traceability: FA-003, FA-010, FA-011
- Depends on: T10, T11
- What: Verify collector sync relies on the shared duplicate lookup by `(zipCode, street, number, city, state)` and does not create duplicate Address documents on repeated messages.
- Where: AddressRepository duplicate lookup, AddressEnrichmentAdapter tests, collector sync tests.
- Done: Reprocessing the same collector address links to the same existing Address ID and does not insert another `collection_addresses` document.
- Tests: Regression test with two equivalent collector sync payloads; assert one address and both collector writes/reference updates use the same Address ID.
- Gate: IDE compilation check passes; Maven test lifecycle should pass when command approval/dependencies are available.

T13 — Update docs/config notes for both sync topics

- ID: fix-addres-T13
- Status: Completed
- Traceability: FA-010, FA-011
- Depends on: T10, T11, T12
- What: Update README or operational docs so address enrichment is described as applying to both `addresses-sync` and `collector-sync`.
- Where: README and any sync-topic documentation.
- Done: Documentation no longer says collector sync bypasses enrichment; it explains fail-open `ADDRESS_UNVERIFIED` behavior for collector addresses.
- Tests: Documentation review only.
- Gate: No code gate required.

Verification
------------

- IDE compilation check: passed after Phase 2 implementation.
- Focused collector-sync unit tests added for enrichment invocation, returned Address persistence, and duplicate address ID reuse.
- IDE project build: passed with 0 errors and 0 warnings.
- Maven focused test command was not executed because the tool approval flow rejected it.
