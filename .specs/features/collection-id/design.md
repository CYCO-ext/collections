# Design: collection-id

Overview
--------
Add a read-only collection detail flow that accepts a collection request id in the URL, validates it, loads the request through the existing collection request port, maps it to a stable response summary, and returns 404 when not found.

This feature should share response semantics with `search-collections` while keeping single-record lookup separate from list filtering.

Primary Flow
------------

1. Client calls `GET /collections/{id}`.
2. REST resource passes the path id to `GetCollectionByIdUseCase` or an equivalent query use case.
3. The use case trims and validates the id.
4. The use case calls `CollectionRequestPort.findById(id)`.
5. If no request is found, the use case fails with a not-found error.
6. If found, the use case maps the domain entity into a collection summary response.
7. The endpoint returns HTTP 200 with the summary.

Not Found Flow
--------------

1. Client calls `GET /collections/{id}` with an id that is not stored.
2. `CollectionRequestPort.findById(id)` returns `null`.
3. The use case raises a not-found application exception.
4. REST maps the exception to HTTP 404.

Proposed API
------------

Endpoint:

```text
GET /collections/{id}
```

Runtime URL with the configured REST prefix:

```text
GET /api/collections/request-123
```

Response shape:

```json
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
```

Error responses:

```text
HTTP 400
collection id is required
```

```text
HTTP 404
Collection request not found: request-123
```

Architecture
------------

Application layer:

- `GetCollectionByIdUseCase`
  - Accepts a raw id string.
  - Trims and validates id.
  - Calls `CollectionRequestPort.findById(id)`.
  - Converts a missing result into a not-found exception.
  - Maps the entity to a response summary.
- `CollectionByIdResult` or reused `CollectionSearchResult`
  - Stable output model with the same field set as collection search.

Port layer:

- Reuse existing method:

```java
Uni<CollectionRequest> findById(String id);
```

No new persistence method is required.

Infrastructure layer:

- No repository changes are expected because `CollectionRequestRepository.findById(id)` already exists.

Presentation layer:

- Prefer adding `GET /{id}` to the existing `CollectionSearchResource` if it remains cohesive as a collection query resource.
- Alternative: create `CollectionResource` if search and id lookup should be grouped under a broader collection query resource.

Validation
----------

- Blank or whitespace-only id is invalid and should fail with HTTP 400.
- Nonexistent id is valid input but should return HTTP 404.
- Id format validation is not required unless the project establishes a specific id format later.

Error Mapping
-------------

Use a small application exception for not-found behavior, for example:

```java
CollectionNotFoundException extends RuntimeException
```

REST should map:

- `IllegalArgumentException` -> HTTP 400.
- `CollectionNotFoundException` -> HTTP 404.
- Unexpected failures -> HTTP 500.

Testing Strategy
----------------

Unit tests:

- `GetCollectionByIdUseCaseTest`
  - Existing id returns mapped summary.
  - Blank id fails validation before repository call.
  - Missing id fails with not-found exception.
  - Successful lookup does not call update.

REST tests:

- `GET /collections/{id}` returns HTTP 200 with summary when use case returns a result.
- Missing id from use case maps to HTTP 404.
- Validation error maps to HTTP 400.

Implementation Notes
--------------------

- Keep this endpoint read-only. Do not call `update`, status transition use cases, or event publishers.
- Keep response fields aligned with `search-collections` so list/detail clients can share models.
- Do not return `null` with HTTP 200 for missing records. Missing records must be explicit 404s.
