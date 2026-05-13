# Design: cancel-request

Overview
--------
Add explicit cancellation to the collection request lifecycle. Both generators and collectors can cancel a request they are allowed to act on before the request reaches a completed terminal state.

The implementation should use one application use case for the transition rules and expose two REST entry points that match the existing generator and collector resource organization.

Lifecycle Design
----------------

Current statuses:

```text
PENDING -> IN_PROGRESS -> COMPLETED
PENDING -> PENDING after collector rejection
```

Target statuses:

```text
PENDING -> CANCELLED
IN_PROGRESS -> CANCELLED
COMPLETED -> terminal, cannot cancel
CANCELLED -> terminal, cannot cancel again
```

`REJECTED` remains distinct from cancellation. It represents collector rejection of an assignment/opportunity, not a user canceling the collection request.

API Design
----------

Generator cancellation:

```text
POST /api/generators/requests/{requestId}/cancel
Content-Type: application/json

{
  "generatorId": "generator-1"
}
```

Collector cancellation:

```text
POST /api/collectors/requests/{requestId}/cancel
Content-Type: application/json

{
  "collectorId": "collector-1"
}
```

Success response:

```text
HTTP 200
```

Error responses:

```text
HTTP 400
request id is required
```

```text
HTTP 400
actor id is required
```

```text
HTTP 403
Generator cannot cancel this request
```

```text
HTTP 403
Collector cannot cancel this request
```

```text
HTTP 404
Request not found: request-1
```

```text
HTTP 409
Request is already completed
```

```text
HTTP 409
Request is already cancelled
```

Application Design
------------------

Add `CancelCollectionRequestUseCase` with methods:

```java
Uni<Void> cancelByGenerator(String requestId, String generatorId)
Uni<Void> cancelByCollector(String requestId, String collectorId)
```

The use case should:

1. Normalize and validate request id and actor id.
2. Load the request with `CollectionRequestPort.findById(requestId)`.
3. Fail not found when no request exists.
4. Validate actor ownership:
   - generator actor must match `request.getGeneratorId()`.
   - collector actor must match `request.getSelectedCollectorId()`.
5. Validate transition:
   - `PENDING` and `IN_PROGRESS` can transition to `CANCELLED`.
   - `COMPLETED` fails with conflict.
   - `CANCELLED` fails with conflict.
6. Set status to `CANCELLED`.
7. Persist with `CollectionRequestPort.update(request)`.
8. Publish `COLLECTION_CANCELLED` event.

Domain Design
-------------

Update `CollectionRequest.Status`:

```java
PENDING, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED
```

Optional domain helper:

```java
boolean canCancel() {
    return Status.PENDING.equals(status) || Status.IN_PROGRESS.equals(status);
}
```

REST Design
-----------

Generator resource:

- Inject `CancelCollectionRequestUseCase`.
- Add `POST /requests/{requestId}/cancel` under `@Path("/generators")`.
- Request DTO contains `generatorId`.

Collector resource:

- Inject `CancelCollectionRequestUseCase`.
- Add `POST /requests/{requestId}/cancel` under `@Path("/collectors")`.
- Request DTO contains `collectorId`.

Error mapping should distinguish:

- validation errors -> HTTP 400
- not found -> HTTP 404
- actor ownership failures -> HTTP 403
- terminal status conflicts -> HTTP 409
- unexpected errors -> HTTP 500

Exception Design
----------------

Prefer small application exceptions to avoid mapping everything as HTTP 400:

- `CollectionRequestNotFoundException`
- `CollectionCancellationForbiddenException`
- `CollectionCancellationConflictException`

If the project already has a reusable not-found exception for collection requests, reuse it instead of adding a duplicate.

Event Design
------------

Existing `CollectionEvent` contains eventType, requestId, generatorId, collectorId, status, and timestamp.

For cancellation actor context, prefer extending the event with optional fields:

- `actorType`: `GENERATOR` or `COLLECTOR`
- `actorId`: supplied actor id

The event should be backward-compatible for existing consumers because new JSON fields are additive.

Search and Route Compatibility
------------------------------

Search:

- `SearchCollectionsUseCase` parses statuses from `CollectionRequest.Status`, so adding `CANCELLED` makes `status=CANCELLED` available automatically.
- Update README docs to include `CANCELLED` in supported status values.

Route suggestions:

- Current route suggestion eligibility requires `IN_PROGRESS`; canceled requests should remain excluded by that rule.
- Add or update a test only if existing coverage does not prove non-`IN_PROGRESS` requests are rejected.

Saved routes:

- Canceling a request may leave saved route suggestions containing canceled collections.
- This feature records compatibility review only. Automatic saved-route closure on cancellation can be a follow-up unless required during implementation.

Testing Strategy
----------------

Use case tests:

- Generator cancels own `PENDING` request.
- Generator cancels own `IN_PROGRESS` request.
- Collector cancels assigned `PENDING` request.
- Collector cancels assigned `IN_PROGRESS` request.
- Completed request fails with conflict.
- Already canceled request fails with conflict.
- Wrong generator fails forbidden.
- Wrong collector or no selected collector fails forbidden.
- Missing request fails not found.
- Blank ids fail validation before repository call.
- Event is published with cancellation payload.

REST tests:

- Generator cancel success.
- Collector cancel success.
- Validation maps to HTTP 400.
- Not found maps to HTTP 404.
- Forbidden maps to HTTP 403.
- Conflict maps to HTTP 409.

Compatibility tests:

- Search status parser accepts `CANCELLED`.
- Existing route suggestion tests continue to reject non-`IN_PROGRESS` candidates.

Documentation
-------------

Update README with:

- New generator cancellation endpoint.
- New collector cancellation endpoint.
- `CANCELLED` status in collection search docs.
- Error behavior for 400, 403, 404, and 409.
