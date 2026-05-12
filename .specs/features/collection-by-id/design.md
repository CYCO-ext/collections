# Design: collection-by-id

Overview
--------
`collection-by-id` adds a read-only query flow for loading one collection request by URL id. The flow validates the id, queries the existing collection request persistence port, maps the entity to the same summary shape used by search, and returns explicit HTTP errors for invalid or missing ids.

This design intentionally aligns with the existing `collection-id` implementation. If that implementation is present, the execution work should verify and document it under this feature name instead of creating another route.

Primary Flow
------------

1. Client calls `GET /collections/{id}`.
2. REST receives the path id and calls the collection-by-id use case.
3. The use case trims and validates the id.
4. The use case calls `CollectionRequestPort.findById(id)`.
5. The use case maps the found collection request to a summary response.
6. REST returns HTTP 200 with the summary payload.

Validation and Not Found Flows
------------------------------

Blank id:

1. The use case receives a null, blank, or whitespace-only id.
2. The use case fails with a validation error before calling persistence.
3. REST maps the error to HTTP 400.

Missing collection:

1. The use case receives a nonblank id.
2. `CollectionRequestPort.findById(id)` returns `null`.
3. The use case raises a collection not-found exception.
4. REST maps the error to HTTP 404.

API Contract
------------

Resource path:

```text
GET /collections/{id}
```

Runtime path:

```text
GET /api/collections/{id}
```

Example response:

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

- Use `GetCollectionByIdUseCase` or an equivalent single-record query use case.
- Reuse the search collection result shape when possible so list and detail responses stay consistent.
- Keep validation and not-found conversion inside the application boundary.

Port layer:

- Reuse `CollectionRequestPort.findById(String id)`.
- No new persistence method is required.

Infrastructure layer:

- Reuse the existing collection request repository path.
- No MongoDB collection or index changes are required for the first version because lookup by document id should already be supported.

Presentation layer:

- Add or verify `GET /{id}` under the collection query REST resource.
- Keep `/collections/search` explicitly defined and tested so it is not treated as an id lookup.

Error Mapping
-------------

REST should map:

- `IllegalArgumentException` to HTTP 400.
- Collection not found application exception to HTTP 404.
- Unexpected exceptions to the service's existing generic error handling path.

Testing Strategy
----------------

Use case tests:

- Existing id returns the mapped summary.
- Blank id fails before persistence is called.
- Missing id fails with the not-found exception.
- Successful lookup does not call update or publishing paths.

REST tests:

- `GET /collections/{id}` returns HTTP 200 and the expected summary shape.
- Validation error maps to HTTP 400.
- Not found maps to HTTP 404.
- `GET /collections/search` continues to execute list search.

Implementation Notes
--------------------

- Prefer consolidating this feature with the existing `collection-id` code and docs if both feature folders exist.
- Do not introduce an alternate path such as `/collections/by-id/{id}` unless a future API versioning decision requires it.
- Keep this feature read-only and side-effect free.
