# Design: search-collections

Overview
--------
Add a read-only collection request search flow that accepts optional status, collector, and generator filters, validates status against the domain enum, queries MongoDB with descending date order, and returns collection request summaries.

The design keeps query logic behind the existing `CollectionRequestPort` so REST resources do not depend on MongoDB-specific APIs.

Primary Flow
------------

1. Client calls `GET /collections/search` with optional `status`, `collectorId`, and `generatorId` query parameters.
2. REST resource maps query parameters into `SearchCollectionsQuery`.
3. `SearchCollectionsUseCase` validates and normalizes the status filter and trims optional IDs.
4. The use case calls `CollectionRequestPort.search(query)`.
5. `CollectionRequestRepository` builds a MongoDB query with optional status, collector, and generator filters plus `createdAt` descending sort.
6. The use case maps domain records to response summaries.
7. The endpoint returns HTTP 200 with the ordered list.

Proposed API
------------

Endpoint:

```text
GET /collections/search?status=IN_PROGRESS&collectorId=collector-7&generatorId=generator-1
```

Query parameters:

- `status` optional. Allowed values: `PENDING`, `IN_PROGRESS`, `COMPLETED`, `REJECTED`.
- `collectorId` optional. Matches `CollectionRequest.selectedCollectorId`.
- `generatorId` optional. Matches `CollectionRequest.generatorId`.
- When more than one filter is provided, all filters must match.

Response shape:

```json
[
  {
    "id": "request-123",
    "generatorId": "generator-1",
    "addressId": "address-1",
    "materialIds": ["paper", "plastic"],
    "weight": 12.5,
    "status": "IN_PROGRESS",
    "selectedCollectorId": "collector-7",
    "generatorConfirmed": false,
    "collectorConfirmed": false,
    "createdAt": "2026-05-09T16:00:00",
    "updatedAt": "2026-05-09T16:10:00"
  }
]
```

Error response:

```text
HTTP 400
Invalid collection request status: ARCHIVED
```

Architecture
------------

Application layer:

- `SearchCollectionsUseCase`
  - Accepts nullable status, collectorId, and generatorId query strings.
  - Parses status into `CollectionRequest.Status`.
  - Trims blank optional IDs to `null`.
  - Calls the collection request port with a typed `CollectionSearchQuery`.
  - Returns read-only result summaries.
- `SearchCollectionsQuery`
  - Optional application model if the implementation prefers a typed query object.
- `CollectionSearchResult` or DTO mapper
  - Keeps REST response stable and avoids leaking persistence details if the domain model should not be returned directly.

Port layer:

- Extend `CollectionRequestPort` with a search method, for example:

```java
Uni<List<CollectionRequest>> search(CollectionSearchQuery query);
```

Null query fields mean no filter for that field.

Infrastructure layer:

- Extend `CollectionRequestAdapter` to delegate the search method.
- Extend `CollectionRequestRepository` with a Mongo-backed search query:
  - If status is present: add `Filters.eq("status", status.name())`.
  - If collectorId is present: add `Filters.eq("selectedCollectorId", collectorId)`.
  - If generatorId is present: add `Filters.eq("generatorId", generatorId)`.
  - If multiple filters are present: combine them with `Filters.and(...)`.
  - Always sort by `createdAt` descending.

Presentation layer:

- Add `CollectionSearchResource`, or add the endpoint to an existing resource only if it stays cohesive.
- Preferred path is actor-neutral because search is not inherently generator-only or collector-only:

```text
@Path("/collections")
GET /search
```

Validation
----------

- Missing `status`: allowed.
- Blank `status`: treat as missing only if existing API convention accepts blank optional query parameters; otherwise return HTTP 400.
- Unknown `status`: HTTP 400.
- Status matching should use enum names. A conservative implementation can require uppercase exact values; a more ergonomic implementation can uppercase before parsing.

Ordering Strategy
-----------------

Default order:

```text
createdAt descending
```

Rationale:

- The user asked for date most recent first.
- `createdAt` represents when the collection entered the system.
- `updatedAt` changes during status transitions and confirmations, which can make old requests appear new for unrelated workflow events.

Future extension:

- Add `sortBy=createdAt|updatedAt` only when a product need appears.

MongoDB Query Notes
-------------------

Repository implementation should sort in MongoDB, not after collecting the list.

Suggested indexes:

```text
collection_requests: { createdAt: -1 }
collection_requests: { status: 1, createdAt: -1 }
```

These can be created through deployment migration/manual setup depending on how the project manages MongoDB indexes.

Testing Strategy
----------------

Unit tests:

- `SearchCollectionsUseCaseTest`
  - No status delegates to all-status search.
  - Valid status delegates with parsed enum.
  - Invalid status fails with validation error.

Repository/adapter tests:

- Search without status returns newest first.
- Search with status returns only matching status and newest first.

REST tests:

- `GET /collections/search` returns HTTP 200 and expected response shape.
- `GET /collections/search?status=PENDING` returns filtered records.
- `GET /collections/search?status=ARCHIVED` returns HTTP 400.

Implementation Notes
--------------------

- Keep this endpoint read-only. Do not call `update`, setters, workflow use cases, or event publishers.
- Prefer a dedicated search use case instead of expanding workflow-specific use cases. Search is a query concern, while create/select/accept/reject are workflow commands.
- Avoid reusing `findByStatus(String status)` as the final implementation path unless it is updated to sort in MongoDB. The feature requires ordering, not just filtering.
