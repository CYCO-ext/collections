# Feature: cancel-request

Problem
-------
Users cannot currently cancel a collection request once it has been created. The lifecycle supports creation, collector selection, collector acceptance/rejection, and completion, but there is no explicit cancellation path for either side.

Goal
----
Allow both generators and collectors to cancel a collection request at any moment before it reaches a terminal completed state.

Cancellation should be represented as an explicit collection status so search, by-id lookup, route planning, saved route behavior, and event consumers can distinguish canceled collections from rejected or completed collections.

Scope
-----

In scope:

- Add a `CANCELLED` collection request status.
- Add generator cancellation endpoint.
- Add collector cancellation endpoint.
- Implement a shared cancellation use case that validates actor type, actor id, request id, current status, and actor authorization against the collection request.
- Allow cancellation from `PENDING` and `IN_PROGRESS`.
- Treat `COMPLETED` and already `CANCELLED` requests as terminal for cancellation.
- Persist the status change and update timestamp.
- Publish a cancellation event with actor type and actor id context where supported.
- Ensure search status filtering accepts `CANCELLED` through the enum-based status parser.
- Ensure route suggestion candidate selection excludes canceled requests by relying on the existing `IN_PROGRESS` eligibility rule.
- Add focused unit and REST tests.
- Update README/API documentation.

Out of scope:

- Refunds, payment changes, or penalties.
- Cancel reason collection unless added by a later feature.
- Notification delivery beyond the existing collection event publication path.
- Deleting collection requests.
- Canceling saved routes directly; saved route closure/invalidation can be handled by a later route lifecycle feature if needed.
- Authentication/JWT authorization beyond validating supplied actor ids against request fields.

Constraints & Assumptions
-------------------------

- The current collection statuses are `PENDING`, `IN_PROGRESS`, `COMPLETED`, and `REJECTED`.
- `REJECTED` currently means a collector rejected a pending request and the request remains available; it is not a terminal request cancellation state.
- The service currently uses path/body ids rather than authenticated principals.
- A generator can cancel only its own request: `request.generatorId == generatorId`.
- A collector can cancel only a request assigned to that collector: `request.selectedCollectorId == collectorId`.
- A `PENDING` request can be canceled even before a collector is selected.
- An `IN_PROGRESS` request can be canceled by either the owning generator or selected collector.
- `COMPLETED` cannot be canceled.
- Already `CANCELLED` requests should return a clear conflict-style error rather than silently succeeding.

Acceptance Criteria
-------------------

AC-1: A generator can cancel its own `PENDING` collection request.

AC-2: A generator can cancel its own `IN_PROGRESS` collection request.

AC-3: A collector can cancel an assigned `PENDING` or `IN_PROGRESS` collection request.

AC-4: Cancellation changes the request status to `CANCELLED` and persists it through `CollectionRequestPort.update`.

AC-5: Cancellation publishes a collection event with event type `COLLECTION_CANCELLED`, request id, generator id, collector id when available, status `CANCELLED`, timestamp, actor type, and actor id if the event model supports those fields.

AC-6: A request with status `COMPLETED` cannot be canceled.

AC-7: A request already `CANCELLED` cannot be canceled again and returns a clear error.

AC-8: A generator cannot cancel another generator's request.

AC-9: A collector cannot cancel a request assigned to another collector or a request with no selected collector.

AC-10: Missing request ids and actor ids fail validation with HTTP 400.

AC-11: Unknown request ids return HTTP 404 or the project's closest existing not-found behavior.

AC-12: `GET /api/collections/search?status=CANCELLED` returns canceled requests after the status is added.

AC-13: Focused tests cover generator cancellation, collector cancellation, invalid actors, terminal statuses, event publication, REST mappings, and status search compatibility.

Traceability IDs
----------------

- CAN-001: `CANCELLED` collection status
- CAN-002: Generator cancellation endpoint
- CAN-003: Collector cancellation endpoint
- CAN-004: Shared cancellation use case
- CAN-005: Actor id and ownership validation
- CAN-006: Status transition rules
- CAN-007: Persistence and timestamp update
- CAN-008: Cancellation event publication
- CAN-009: REST error mapping
- CAN-010: Search status compatibility
- CAN-011: Route/saved-route compatibility review
- CAN-012: Focused tests and documentation
