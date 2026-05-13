# Design: delete-suggestion

Overview
--------
Add a read-write saved route operation that deletes one saved route suggestion by id. The endpoint lives beside existing saved route APIs under `/collectors/routes`.

The operation physically removes the saved route from MongoDB. This keeps duplicate fingerprint behavior simple: after deletion, the same route suggestion may be saved again because the previous document and fingerprint are gone.

API Contract
------------

Endpoint:

```text
DELETE /collectors/routes/saved/{savedRouteId}
```

Runtime URL:

```text
DELETE /api/collectors/routes/saved/saved-1
```

Success:

```text
HTTP 204 No Content
```

Validation error:

```text
HTTP 400
saved route id is required
```

Not found:

```text
HTTP 404
Saved route suggestion not found: saved-1
```

Application Design
------------------

Add `DeleteSavedRouteSuggestionUseCase`:

```java
Uni<Void> delete(String savedRouteId)
```

Flow:

1. Validate `savedRouteId` is not null, blank, or whitespace-only.
2. Trim the id.
3. Call `SavedRoutePort.deleteById(normalizedId)`.
4. If the port reports no document was deleted, fail with `SavedRouteSuggestionNotFoundException`.
5. Return `Void` on success.

Exception:

```java
SavedRouteSuggestionNotFoundException extends RuntimeException
```

Port Design
-----------

Extend `SavedRoutePort`:

```java
Uni<Boolean> deleteById(String savedRouteId);
```

Return value:

- `true`: a saved route document was deleted.
- `false`: no document matched the id.

Adapter Design
--------------

Extend `SavedRouteAdapter` to delegate to `SavedRouteRepository.deleteById(savedRouteId)`.

Repository Design
-----------------

Add `SavedRouteRepository.deleteById(String savedRouteId)`:

- Execute MongoDB `deleteOne(Filters.eq("_id", savedRouteId))`.
- Return `true` when `deletedCount > 0`.
- Return `false` when `deletedCount == 0`.

REST Design
-----------

Update `CollectorRouteResource`:

- Inject `DeleteSavedRouteSuggestionUseCase`.
- Add `@DELETE @Path("/saved/{savedRouteId}")`.
- Return HTTP 204 on success.
- Map `IllegalArgumentException` to HTTP 400.
- Map `SavedRouteSuggestionNotFoundException` to HTTP 404.
- Map unexpected errors to HTTP 500.

Testing Strategy
----------------

Use case tests:

- Existing route id returns successfully and delegates to the port with trimmed id.
- Blank id fails before port call.
- Unknown id fails with not-found exception.
- Repository failure propagates as failure.

REST tests:

- Delete success returns HTTP 204 and delegates to use case.
- Validation error maps to HTTP 400.
- Not found maps to HTTP 404.
- Unexpected error maps to HTTP 500.

Repository/adapter tests are optional unless the project already has focused repository tests. Build verification is required.

Documentation
-------------

Update README under collector route endpoints with:

- `DELETE /api/collectors/routes/saved/{savedRouteId}`.
- HTTP 204 success behavior.
- HTTP 400 and 404 error behavior.
