# Tasks: update-waste-collector

Overview
--------
Atomic implementation tasks for consuming `collector-update` events and updating collector data with the same payload used for collector creation/sync.

Implementation Status
---------------------

- Status: Completed
- Consumer: added `@Incoming("collector-update")` method in `SyncEventConsumer`.
- Payload: reused `SyncCollectorEvent`, the same payload model used for collector create/sync.
- Processing: extracted shared collector sync/update handler so both channels use the same address enrichment, fallback, address upsert, and collector upsert behavior.
- Deserialization: consumers receive `KafkaRecord<String, String>` and explicitly parse JSON with `ObjectMapper`, matching the configured `StringDeserializer`.
- Configuration: aligned incoming channel names in `application.properties` with `@Incoming` names and added `collector-update` topic configuration.
- Documentation: README documents `collector-update` and payload reuse.
- Verification: `SyncEventConsumerTest` and IDE project build passed.

Phase 1 - Contract and Channel Design
-------------------------------------

T0 - Confirm update event contract

- ID: update-waste-collector-T0
- Status: Completed
- Traceability: UWC-001, UWC-002, UWC-007
- Depends on: none
- What: Confirm `collector-update` topic, `SyncCollectorEvent` payload reuse, and expected upsert semantics.
- Where: `.specs/features/update-waste-collector/spec.md`, `.specs/features/update-waste-collector/design.md`.
- Done: Spec and design agree that update uses the same payload as create/sync and updates via collector upsert.
- Tests: Documentation review.
- Gate: Passed.

T1 - Add Kafka channel configuration

- ID: update-waste-collector-T1
- Status: Completed
- Traceability: UWC-001, UWC-007
- Depends on: T0
- What: Add incoming channel configuration for topic `collector-update`.
- Where: `src/main/resources/application.properties`.
- Done: Configuration includes connector, topic, group id, deserializer, and offset reset for the update channel.
- Tests: IDE build passed.
- Gate: Passed.

T2 - Review collector-sync channel naming

- ID: update-waste-collector-T2
- Status: Completed
- Traceability: UWC-007, UWC-008
- Depends on: T0
- What: Verify existing `@Incoming` channel names match `application.properties`, and fix or document any mismatch without breaking existing behavior.
- Where: `src/main/java/org/example/infrastructure/kafka/SyncEventConsumer.java`, `src/main/resources/application.properties`.
- Done: `addresses-sync`, `collector-sync`, and `collector-update` channel configuration names now match their `@Incoming` names; Kafka topic names remain unchanged.
- Tests: `SyncEventConsumerTest` and IDE build passed.
- Gate: Passed.

Phase 2 - Consumer Implementation
---------------------------------

T3 - Extract shared collector sync handler

- ID: update-waste-collector-T3
- Status: Completed
- Traceability: UWC-003, UWC-004, UWC-005, UWC-006, UWC-008
- Depends on: T0
- What: Extract existing collector consume logic into a private method that accepts `SyncCollectorEvent` and an operation label.
- Where: `src/main/java/org/example/infrastructure/kafka/SyncEventConsumer.java`.
- Done: Existing `consumeCollector` delegates to the shared `syncCollector(...)` handler and behavior remains unchanged.
- Tests: Existing `SyncEventConsumerTest` cases passed.
- Gate: Focused consumer tests passed.

T4 - Add collector update consumer method

- ID: update-waste-collector-T4
- Status: Completed
- Traceability: UWC-001, UWC-002, UWC-003, UWC-006
- Depends on: T1, T3
- What: Add `@Incoming("collector-update")` consumer that receives `KafkaRecord<String, SyncCollectorEvent>` and delegates to the shared handler.
- Where: `src/main/java/org/example/infrastructure/kafka/SyncEventConsumer.java`.
- Done: Update topic messages use the same address enrichment and collector upsert behavior as collector sync.
- Tests: `SyncEventConsumerTest` update-specific tests passed.
- Gate: Focused consumer tests passed.

Phase 3 - Tests
---------------

T5 - Add successful update consumer test

- ID: update-waste-collector-T5
- Status: Completed
- Traceability: UWC-002, UWC-003, UWC-004, UWC-006, UWC-009
- Depends on: T4
- What: Test that `consumeCollectorUpdate` enriches address, upserts address, and upserts collector with updated fields from the payload.
- Where: `src/test/java/org/example/infrastructure/kafka/SyncEventConsumerTest.java`.
- Done: Test asserts id, userId, name, accepted materials, acceptance rate, and enriched address are persisted.
- Tests: `SyncEventConsumerTest` passed.
- Gate: Passed.

T6 - Add update fallback test

- ID: update-waste-collector-T6
- Status: Completed
- Traceability: UWC-005, UWC-006, UWC-009
- Depends on: T4
- What: Test that enrichment failure for update events still upserts fallback address and collector.
- Where: `src/test/java/org/example/infrastructure/kafka/SyncEventConsumerTest.java`.
- Done: Test asserts fallback address has `FAILED` status and collector is still upserted.
- Tests: `SyncEventConsumerTest` passed.
- Gate: Passed.

T7 - Preserve existing sync tests

- ID: update-waste-collector-T7
- Status: Completed
- Traceability: UWC-008, UWC-009
- Depends on: T3, T4
- What: Ensure existing collector sync tests continue to pass after extraction.
- Where: `src/test/java/org/example/infrastructure/kafka/SyncEventConsumerTest.java`.
- Done: Existing sync tests pass without weakened assertions.
- Tests: `SyncEventConsumerTest` passed.
- Gate: Passed.

Phase 4 - Documentation and Verification
----------------------------------------

T8 - Update README or integration docs

- ID: update-waste-collector-T8
- Status: Completed
- Traceability: UWC-001, UWC-002, UWC-007, UWC-009
- Depends on: T1, T4
- What: Document `collector-update` topic and payload reuse wherever Kafka topics are documented.
- Where: `README.md`.
- Done: README documents `collector-update` and that it reuses `SyncCollectorEvent` with create/sync collector payload shape.
- Tests: Documentation review only.
- Gate: Passed.

T9 - Final verification

- ID: update-waste-collector-T9
- Status: Completed
- Traceability: UWC-009
- Depends on: T0 through T8
- What: Run focused consumer tests and project build.
- Where: IDE test runner and project build.
- Done: Focused tests and build passed.
- Tests: `SyncEventConsumerTest` and IDE project build.
- Gate: Build passed with 0 errors and 0 warnings.

Parallelization Notes
---------------------

- T1 and T2 were handled together because both touched messaging configuration.
- T5 and T6 were implemented after the update consumer method.
- T8 was updated after the final channel names were established.
