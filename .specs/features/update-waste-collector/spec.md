# Feature: update-waste-collector

Problem
-------
The collections service stores collector snapshots from Kafka so route discovery and collector address lookups can work locally. It currently consumes collector create/sync events, but there is no dedicated consumer for the `collector-update` topic.

Goal
----
Add a Kafka event consumer for the `collector-update` topic that updates collector data using the same payload shape as collector creation.

The update consumer should reuse the existing collector mapping, address enrichment, address upsert, and collector upsert behavior so collector updates remain consistent with collector creation.

Scope
-----

In scope:

- Add an incoming Kafka channel for topic `collector-update`.
- Consume the same payload shape as create collector: `SyncCollectorEvent`.
- Reuse the existing collector address enrichment path.
- Upsert the enriched or fallback address.
- Upsert collector data by `collectorId`, replacing name, userId, address, accepted material ids, and acceptance rate.
- Keep create/sync collector behavior unchanged.
- Add focused tests proving update-topic messages update collector data.
- Update application configuration and README/docs where Kafka topics are documented.

Out of scope:

- Introducing a different update payload format.
- Partial update/patch semantics.
- Collector deletion or deactivation.
- Schema registry changes.
- Cross-service producer changes.
- Authorization or manual REST update endpoints.

Constraints & Assumptions
-------------------------

- The update event payload is identical to the create collector payload and can be represented by `SyncCollectorEvent`.
- `SyncCollectorEvent.address` uses `SyncAddressEvent` and should go through the same enrichment/fallback flow as create/sync.
- Collector persistence already supports `upsert(Collector)`.
- Address persistence already supports `upsert(Address)`.
- Updating collector data should be idempotent for the same event payload.
- `collectorId` is the stable identity key for the collector document.
- Existing consumer code has channel/config naming that should be verified while adding the new channel.

Acceptance Criteria
-------------------

AC-1: The service has a Kafka incoming channel bound to topic `collector-update`.

AC-2: The update consumer accepts `SyncCollectorEvent` with the same fields as create collector events.

AC-3: When address enrichment succeeds, the enriched address is upserted and the collector is upserted using that address.

AC-4: When address enrichment fails, a fallback address with enrichment status `FAILED` is upserted and the collector is still upserted.

AC-5: Collector update upsert replaces collector fields: id, userId, name, address, acceptedMaterialIds, and acceptanceRate.

AC-6: Existing `collector-sync` behavior continues to work.

AC-7: Channel configuration is present for the update topic and aligned with the `@Incoming` channel name.

AC-8: Focused tests cover successful update, fallback update, and that update uses the same mapping as create/sync.

Traceability IDs
----------------

- UWC-001: `collector-update` Kafka topic consumer
- UWC-002: Reuse `SyncCollectorEvent` payload
- UWC-003: Shared collector sync/update processing path
- UWC-004: Address enrichment success path
- UWC-005: Address enrichment fallback path
- UWC-006: Collector upsert by `collectorId`
- UWC-007: Channel configuration alignment
- UWC-008: Existing collector-sync compatibility
- UWC-009: Focused tests and documentation
