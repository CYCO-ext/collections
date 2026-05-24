# Design: update-waste-collector

Overview
--------
Add a Kafka consumer for collector update events. The update event payload is the same as the collector create/sync payload, so the implementation should avoid duplicating mapping logic and route both create/sync and update messages through a shared private handler.

Current Behavior
----------------

`SyncEventConsumer` currently has a collector consumer that:

1. Receives `SyncCollectorEvent`.
2. Enriches `event.address()` through `AddressEnrichmentPort`.
3. Upserts the address into `AddressRepository`.
4. Maps the event plus address to `Collector`.
5. Upserts the collector into `CollectorRepository`.
6. On enrichment failure, creates a fallback address with status `FAILED`, upserts it, and still upserts the collector.

Target Behavior
---------------

Add a second incoming consumer for update events:

```java
@Incoming("collector-update")
@Blocking
public Uni<Void> consumeCollectorUpdate(KafkaRecord<String, SyncCollectorEvent> record)
```

The method should delegate to the same internal handler used by the existing collector sync consumer:

```java
private Uni<Void> syncCollector(SyncCollectorEvent event, String operation)
```

Kafka Configuration
-------------------

Add incoming channel configuration for the update topic:

```properties
mp.messaging.incoming.collector-update.connector=smallrye-kafka
mp.messaging.incoming.collector-update.topic=collector-update
mp.messaging.incoming.collector-update.group.id=collections-service
mp.messaging.incoming.collector-update.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
mp.messaging.incoming.collector-update.auto.offset.reset=earliest
```

The existing collector channel should also be reviewed for consistency between `@Incoming` names and `application.properties` channel names. The implementation should not break existing `collector-sync` consumption.

Payload Contract
----------------

Use existing `SyncCollectorEvent`:

```json
{
  "eventType": "COLLECTOR_UPDATED",
  "collectorId": "collector-1",
  "userId": "user-1",
  "name": "Collector One Updated",
  "address": {
    "id": "address-1",
    "street": "Main St",
    "city": "Sao Paulo",
    "zipCode": "01000-000",
    "number": "100",
    "latitude": -23.5505,
    "longitude": -46.6333
  },
  "acceptedMaterialIds": ["paper", "plastic"],
  "acceptanceRate": 0.97
}
```

Persistence Behavior
--------------------

Collector update uses `CollectorRepository.upsert(collector)`. This means:

- Existing collector documents are replaced with the latest payload data.
- Missing collector documents are inserted, making out-of-order create/update events tolerant.
- `collectorId` remains the `_id` key.

Address behavior remains the same as create/sync:

- Enriched address is upserted when enrichment succeeds.
- Fallback `FAILED` address is upserted when enrichment fails.
- Collector stores the address object used in the address upsert.

Error Handling
--------------

- Enrichment failures are recovered with fallback address behavior, matching current collector sync.
- Persistence failures should fail the returned `Uni<Void>` so the reactive messaging connector can apply its configured retry/failure behavior.
- Invalid payload validation is not introduced in this feature unless existing sync behavior already has it.

Testing Strategy
----------------

Update `SyncEventConsumerTest` or add focused tests to cover:

- `consumeCollectorUpdate` enriches address, upserts address, and upserts collector with updated fields.
- `consumeCollectorUpdate` recovers from enrichment failure and upserts fallback address plus collector.
- Existing `consumeCollector` behavior still passes.
- Both create/sync and update methods share behavior by asserting identical repository interactions for equivalent payloads.

Implementation Notes
--------------------

- Prefer extracting a private handler from existing `consumeCollector` instead of copying the method body.
- Keep logging specific enough to distinguish sync/create vs update events.
- Do not add a new event DTO because the payload is explicitly the same as create collector.
- If channel naming is normalized, update tests/docs to reflect the final channel names.
