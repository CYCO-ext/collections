# Design: collection-address

Overview
--------
Enhance collection query responses by resolving each collection request's `addressId` through the existing `AddressPort` and embedding the full address payload in the response.

This is response enrichment only. Collection search, collection-by-id, and persistence rules remain otherwise unchanged.

Current Behavior
----------------

`SearchCollectionsUseCase.CollectionSearchResult` currently contains:

- collection identifiers and status fields
- `addressId`
- material ids
- selected collector id
- confirmation flags
- createdAt and updatedAt

It does not include the full address object.

Target Behavior
---------------

Each collection search result should include:

- existing collection fields
- existing `addressId`
- new `address` object with the full address fields

The collection-by-id use case returns the same result model, so it should receive the address object once the shared result is enriched.

API Contract
------------

Endpoint:

```text
GET /api/collections/search?status=IN_PROGRESS&collectorId=collector-1&generatorId=generator-1
```

Example response:

```json
[
  {
    "id": "request-1",
    "generatorId": "generator-1",
    "addressId": "address-1",
    "address": {
      "id": "address-1",
      "street": "Main St",
      "number": "100",
      "city": "Sao Paulo",
      "state": "SP",
      "zipCode": "01000-000",
      "latitude": -23.5505,
      "longitude": -46.6333,
      "enrichmentStatus": "ENRICHED",
      "enrichmentSource": "nominatim"
    },
    "materialIds": ["paper"],
    "weight": 10.0,
    "status": "IN_PROGRESS",
    "selectedCollectorId": "collector-1",
    "generatorConfirmed": false,
    "collectorConfirmed": false,
    "createdAt": "2026-05-09T16:00:00",
    "updatedAt": "2026-05-09T16:10:00"
  }
]
```

Error response for missing address:

```text
HTTP 404
Collection address not found: address-1
```

Application Design
------------------

`SearchCollectionsUseCase`:

- Inject `AddressPort`.
- Keep existing query parsing and `CollectionRequestPort.search(query)` behavior.
- After collection requests are returned, resolve addresses for each result.
- Map each request and address into an enriched `CollectionSearchResult`.
- Preserve the list order returned by `CollectionRequestPort.search(query)`.

`GetCollectionByIdUseCase`:

- Because it currently returns `CollectionSearchResult`, it should use the same mapping/enrichment helper as search.
- Prefer extracting a small mapper/helper inside the application layer if both use cases need the same address resolution behavior.

New result record:

```java
record CollectionAddressResult(
    String id,
    String street,
    String number,
    String city,
    String state,
    String zipCode,
    Double latitude,
    Double longitude,
    String enrichmentStatus,
    String enrichmentSource
) {}
```

Updated collection result:

```java
record CollectionSearchResult(
    String id,
    String generatorId,
    String addressId,
    CollectionAddressResult address,
    List<String> materialIds,
    Double weight,
    CollectionRequest.Status status,
    String selectedCollectorId,
    Boolean generatorConfirmed,
    Boolean collectorConfirmed,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
```

Port and Infrastructure Design
------------------------------

Use existing port:

```java
Uni<Address> findById(String id);
```

No repository change is required for the initial implementation.

A future optimization can add `AddressPort.findByIds(List<String> ids)` to avoid one lookup per collection result, but this feature should start with the existing port unless performance testing proves it necessary.

Missing Address Behavior
------------------------

If `AddressPort.findById(addressId)` returns `null`, fail with a dedicated application exception, for example:

```java
CollectionAddressNotFoundException extends RuntimeException
```

REST should map it to HTTP 404.

Testing Strategy
----------------

Use case tests:

- Search returns collection results with full address data.
- Search preserves status, collectorId, and generatorId filters.
- Search preserves newest-first order from the collection port.
- Missing address fails with `CollectionAddressNotFoundException`.
- Collection-by-id returns the enriched address object.
- Read-only behavior does not call collection update or address mutation paths.

REST tests:

- `GET /collections/search` returns HTTP 200 with `address` in each item.
- Missing address maps to HTTP 404.
- Existing invalid status behavior remains HTTP 400.
- `GET /collections/{id}` returns the same enriched response shape.

Implementation Notes
--------------------

- Keep `addressId` in the payload to avoid breaking existing clients.
- Keep response ordering stable after asynchronous address lookups.
- Avoid duplicating address DTOs if the collector-address feature already has a compatible result shape, but do not include `collectorId` in collection address payloads.
- Do not fetch collector data for this feature.
