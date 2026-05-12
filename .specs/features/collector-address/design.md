# Design: collector-address

Overview
--------
Add a read-only collector address flow that accepts a collector id, validates it, loads the collector, resolves its address, and returns a stable address response. Missing collectors or missing addresses are returned as HTTP 404.

This feature should reuse the existing collector and address ports rather than adding persistence-specific logic to REST resources.

Primary Flow
------------

1. Client calls `GET /collectors/{collectorId}/address`.
2. REST resource passes `collectorId` to `GetCollectorAddressUseCase`.
3. The use case trims and validates the collector id.
4. The use case calls `CollectorDiscoveryPort.findCollectorById(collectorId)`.
5. If the collector does not exist, the use case fails with a not-found error.
6. The use case resolves the collector address:
   - If the collector has an embedded address with usable fields, return it.
   - If the embedded address only carries an id and requires lookup, call `AddressPort.findById(addressId)`.
7. If no address can be resolved, the use case fails with a not-found error.
8. The endpoint returns HTTP 200 with the address response.

Proposed API
------------

Endpoint:

```text
GET /collectors/{collectorId}/address
```

Runtime URL with the configured REST prefix:

```text
GET /api/collectors/collector-123/address
```

Response shape:

```json
{
  "collectorId": "collector-123",
  "addressId": "address-456",
  "street": "Main St",
  "number": "100",
  "city": "Sao Paulo",
  "state": "SP",
  "zipCode": "01000-000",
  "latitude": -23.5505,
  "longitude": -46.6333,
  "enrichmentStatus": "ENRICHED",
  "enrichmentSource": "nominatim"
}
```

Error responses:

```text
HTTP 400
collector id is required
```

```text
HTTP 404
Collector not found: collector-123
```

```text
HTTP 404
Collector address not found: collector-123
```

Architecture
------------

Application layer:

- `GetCollectorAddressUseCase`
  - Accepts a raw collector id string.
  - Trims and validates collector id.
  - Calls `CollectorDiscoveryPort.findCollectorById(collectorId)`.
  - Resolves address from the collector's embedded address or `AddressPort.findById(addressId)`.
  - Maps `Address` to a stable response model.
- `CollectorAddressResult`
  - Contains `collectorId`, `addressId`, street, number, city, state, zipCode, coordinates, and enrichment metadata.
- Not-found exceptions
  - Reuse a small application exception pattern if one already exists, or add collector-address-specific not-found exceptions.

Port layer:

- Reuse existing methods:

```java
Uni<Collector> findCollectorById(String collectorId);
Uni<Address> findById(String id);
```

No new persistence methods are required for the MVP.

Infrastructure layer:

- No repository changes are expected for the MVP.

Presentation layer:

- Add `GET /{collectorId}/address` to `CollectorResource`, or create a dedicated `CollectorAddressResource` if `CollectorResource` becomes too command-heavy.
- REST maps:
  - `IllegalArgumentException` -> HTTP 400.
  - collector/address not-found exception -> HTTP 404.
  - unexpected failures -> HTTP 500.

Address Resolution Strategy
---------------------------

The current `Collector` entity embeds `Address`. The use case should prefer deterministic behavior:

1. If `collector.getAddress()` is null, return not found.
2. If the embedded address has an id and complete enough fields for the response, map it directly.
3. If the embedded address has only an id and important fields are missing, call `AddressPort.findById(addressId)`.
4. If address lookup returns null, return not found.

"Complete enough" means at least one user-facing address field or coordinate exists beyond id. The implementation can keep this simple by returning the embedded address unless it is null, then add address-port refresh only if tests reveal collectors are stored with id-only addresses.

Testing Strategy
----------------

Unit tests:

- `GetCollectorAddressUseCaseTest`
  - Existing collector with embedded address returns mapped response.
  - Blank collector id fails validation before port calls.
  - Missing collector fails with not-found.
  - Collector with null address fails with not-found.
  - Address lookup path, if implemented, handles address found and missing address.
  - Successful lookup does not call mutation paths.

REST tests:

- `GET /collectors/{collectorId}/address` returns HTTP 200 with address response.
- Validation error maps to HTTP 400.
- Collector/address not found maps to HTTP 404.

Implementation Notes
--------------------

- Keep this endpoint read-only. Do not call update methods or event publishers.
- Keep address response focused on address fields, not full collector profile details.
- Use the `/api` prefix only in external docs/tests that exercise the running HTTP stack; resource-level code stays under `/collectors`.
