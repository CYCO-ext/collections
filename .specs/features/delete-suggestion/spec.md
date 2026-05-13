# Feature: delete-suggestion

Problem
-------
Collectors can save route suggestions and list saved routes, but there is no way to remove a saved suggestion that is no longer useful or was saved by mistake.

Goal
----
Add an endpoint to delete a saved route suggestion by id.

The delete operation should remove the saved route document from MongoDB, return success when the suggestion exists, and return a clear not-found response when the id does not match an existing saved route.

Scope
-----

In scope:

- REST endpoint to delete a saved route suggestion by id.
- Application use case for delete behavior.
- Port and repository support for deleting from `saved_routes`.
- Id validation for missing or blank saved route ids.
- HTTP 404 when no saved route suggestion exists for the id.
- Tests for use case success, validation, not found, REST success, and REST error mapping.
- README/API documentation.

Out of scope:

- Bulk deletion.
- Soft-delete or archive status.
- Authorization/JWT ownership checks beyond the service's existing approach.
- Deleting collection requests assigned to a route.
- Reopening or changing collection request status.
- Changing duplicate fingerprint behavior except as naturally affected by physical deletion.

Constraints & Assumptions
-------------------------

- Saved route suggestions are stored in MongoDB collection `saved_routes`.
- Saved route ids are stored as Mongo `_id` values and exposed as `SavedRouteResult.id`.
- The current saved route API path prefix is `/api/collectors/routes`.
- Physical deletion is acceptable for this first version.
- Deleting a saved route should be idempotent only for successful existing deletion; unknown ids should return 404 so clients can detect stale state.

Acceptance Criteria
-------------------

AC-1: A client can call `DELETE /api/collectors/routes/saved/{savedRouteId}`.

AC-2: When the saved route exists, the endpoint deletes it and returns HTTP 204 No Content.

AC-3: Blank or whitespace-only saved route ids fail validation with HTTP 400 and a clear message.

AC-4: Unknown saved route ids return HTTP 404 and a clear message.

AC-5: The delete use case delegates to `SavedRoutePort.deleteById(savedRouteId)` and does not mutate collection requests.

AC-6: The repository deletes from the `saved_routes` collection by `_id`.

AC-7: Focused use case and REST tests cover success, validation, not found, and unexpected repository failure behavior.

Traceability IDs
----------------

- DSUG-001: Delete saved route endpoint
- DSUG-002: Saved route id validation
- DSUG-003: Saved route port delete operation
- DSUG-004: MongoDB delete from `saved_routes`
- DSUG-005: Not-found HTTP 404 behavior
- DSUG-006: No collection request mutation
- DSUG-007: Focused tests and documentation
