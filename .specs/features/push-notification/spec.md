# Feature: push-notification

Problem
-------

Generators currently rely on API polling or external event consumers to learn when a collection changes status. The service publishes Kafka collection events, but it does not directly notify the generator's mobile device when the collector accepts, starts travelling, completes, rejects, or cancels a collection.

The current status model also jumps from `IN_PROGRESS` to completion confirmation. There is no explicit state for "collector is on the way", so generator clients cannot distinguish an accepted collection from a collector actively heading to the pickup address.

Goal
----

Publish Firebase Cloud Messaging notifications to the generator when a collection status is updated, and add a new collection status representing that the collector is on the way.

The service should preserve existing Kafka event publishing while adding an application-level notification flow behind a port, so Firebase-specific code stays in infrastructure.

Scope
-----

In scope:

- Add a new collection status for collector travel, suggested enum value `ON_THE_WAY`.
- Add a status transition endpoint/use case for a collector to mark a selected/in-progress collection as `ON_THE_WAY`.
- Publish a generator-targeted FCM notification whenever a collection status changes through supported flows.
- Cover status updates from accept, on-the-way, complete, reject, and cancel flows where a generator exists.
- Add an application notification port so use cases do not depend on Firebase SDK types.
- Add Firebase Cloud Messaging infrastructure adapter and configuration.
- Add a way to resolve generator notification tokens.
- Add tests for transition rules, notification invocation, event preservation, and REST mapping.
- Update README/API docs and application configuration documentation.

Out of scope for the first implementation:

- Rich notification preferences or opt-in categories.
- Notification localization beyond stable English/Portuguese message templates chosen during implementation.
- Push notification retries beyond provider/client-level retry behavior.
- Notification history persistence.
- Sending notifications to collectors.
- Topic-based broadcast notifications.
- Web push, APNs direct integration, SMS, or email.
- Full auth/authorization enforcement beyond existing project patterns.

Constraints & Assumptions
-------------------------

- The service is Java 21 with Quarkus, Mutiny, MongoDB, Kafka, and hexagonal architecture.
- Runtime routes include the global `/api` prefix from `quarkus.rest.path=/api`.
- Existing collection status values are `PENDING`, `IN_PROGRESS`, `COMPLETED`, `REJECTED`, and `CANCELLED`.
- Suggested new status value: `ON_THE_WAY`.
- `IN_PROGRESS` remains the accepted/active collection state created when a collector accepts a request.
- `ON_THE_WAY` represents the collector actively going to the generator address.
- Completion confirmation should accept `ON_THE_WAY` as an active status, or transition rules must define whether collectors can complete directly from `IN_PROGRESS` for backward compatibility.
- Current sync user events contain only `userId`, `name`, and `email`; no device token source exists in the codebase.
- MVP token strategy should be one of:
  - Sync FCM tokens from the user service through user sync events.
  - Add a local endpoint for users/generators to register/update/delete an FCM token.
- FCM send failures should be logged but should not roll back successful collection status updates.
- Missing generator tokens should be treated as a no-op with an info/debug log, not as a failed collection update.
- Firebase credentials must come from configuration/environment, not hardcoded files.

Acceptance Criteria
-------------------

AC-1: The domain model supports a new `ON_THE_WAY` collection status.

AC-2: A collector can mark a valid selected collection as `ON_THE_WAY` through a REST endpoint.

AC-3: `ON_THE_WAY` transition validates request existence, collector ownership, and allowed source status.

AC-4: Existing completion flow supports the intended active statuses after adding `ON_THE_WAY`.

AC-5: When a collection status changes to `IN_PROGRESS`, `ON_THE_WAY`, `COMPLETED`, `REJECTED`, or `CANCELLED`, the service attempts to send an FCM notification to the generator.

AC-6: Kafka collection events continue to publish for the same status changes as before, and new `ON_THE_WAY` events are published if this service owns the transition.

AC-7: Notification delivery is done through an application port; use cases do not import Firebase SDK classes.

AC-8: Missing FCM token results in no push send and no collection status rollback.

AC-9: FCM provider failure is logged and does not roll back the status update or Kafka event publication.

AC-10: Notification payload includes at minimum collection request id, generator id, collector id when available, new status, event type, and timestamp.

AC-11: Tests cover successful on-the-way transition, invalid transition, unauthorized collector, notification success/no-token/failure behavior, and REST response mapping.

AC-12: README documents the new endpoint, status, required Firebase configuration, and token strategy.

Traceability IDs
----------------

- PN-001: Add `ON_THE_WAY` status.
- PN-002: Define status transition rules.
- PN-003: Add collector on-the-way use case.
- PN-004: Add collector on-the-way REST endpoint.
- PN-005: Preserve Kafka status events.
- PN-006: Add notification application port and models.
- PN-007: Resolve generator FCM token(s).
- PN-008: Add Firebase Cloud Messaging adapter.
- PN-009: Send generator notification after status updates.
- PN-010: Do not roll back status updates on notification failure.
- PN-011: Add configuration and secrets documentation.
- PN-012: Add focused unit and REST test coverage.

Notes
-----

- Suggested endpoint: `POST /collectors/requests/{requestId}/on-the-way` at resource level, exposed as `POST /api/collectors/requests/{requestId}/on-the-way`.
- Suggested event type: `COLLECTION_ON_THE_WAY`.
- Suggested notification event types mirror existing Kafka events: `COLLECTION_ACCEPTED`, `COLLECTION_ON_THE_WAY`, `COLLECTION_COMPLETED`, `COLLECTION_REJECTED`, `COLLECTION_CANCELLED`.
- Product decision needed before implementation: token source should be confirmed as local registration endpoint or upstream user-service sync.
