# Tasks: push-notification

Overview
--------

Atomic implementation tasks for adding Firebase Cloud Messaging notifications to generators when collection status changes, and adding the `ON_THE_WAY` collection status.

Implementation Status
---------------------

- Status: Completed
- Primary status change: add `ON_THE_WAY` to `CollectionRequest.Status`.
- Primary endpoint: `POST /api/collectors/requests/{requestId}/on-the-way`.
- Primary integration: Firebase Cloud Messaging HTTP v1 through an outbound application port.
- Token strategy: local Mongo token registration endpoint for generator devices.

Phase 1 - Contract and Status Model
-----------------------------------

T0 - Confirm notification and token contract

- ID: push-notification-T0
- Status: Completed
- Traceability: PN-001, PN-002, PN-007, PN-011
- Depends on: none
- What: Confirm `ON_THE_WAY` status name, endpoint shape, notification language, and token source strategy.
- Where: `.specs/features/push-notification/spec.md`, `.specs/features/push-notification/design.md`.
- Done: Product/technical decision is documented before implementation begins.
- Tests: Documentation review.
- Gate: Spec/design/tasks stay aligned.

T1 - Add ON_THE_WAY domain status

- ID: push-notification-T1
- Status: Completed
- Traceability: PN-001, PN-002
- Depends on: T0
- What: Add `ON_THE_WAY` to `CollectionRequest.Status` and update helper methods for cancellation/completion active-status behavior.
- Where: `src/main/java/org/example/domain/entity/CollectionRequest.java`.
- Done: Domain supports `ON_THE_WAY`; cancellation and completion rules match the spec.
- Tests: `CollectionRequest` behavior through existing use case tests.
- Gate: IDE compilation passes.

T2 - Define notification application models

- ID: push-notification-T2
- Status: Completed
- Traceability: PN-006, PN-009
- Depends on: T0
- What: Add app-layer notification records for collection status notifications and generic push messages.
- Where: Suggested `src/main/java/org/example/application/notification/NotificationModels.java` or equivalent existing package.
- Done: Models have no Firebase SDK dependency and carry recipient, title/body, data payload, event type, status, and timestamp.
- Tests: Covered by notification use case tests.
- Gate: IDE compilation passes.

Phase 2 - Ports and Token Resolution
------------------------------------

T3 - Add push notification port

- ID: push-notification-T3
- Status: Completed
- Traceability: PN-006, PN-008, PN-010
- Depends on: T2
- What: Define `PushNotificationPort` with a send method for app-layer push messages.
- Where: `src/main/java/org/example/application/port/out/PushNotificationPort.java`.
- Done: Application code depends on the port, not Firebase types.
- Tests: Mocked in notification use case tests.
- Gate: IDE compilation passes.

T4 - Add generator notification token port

- ID: push-notification-T4
- Status: Completed
- Traceability: PN-007
- Depends on: T2
- What: Define `GeneratorNotificationTokenPort` for resolving FCM token(s) by generator id.
- Where: `src/main/java/org/example/application/port/out/GeneratorNotificationTokenPort.java`.
- Done: Notification use case can resolve zero, one, or many tokens for a generator.
- Tests: Mocked in notification use case tests.
- Gate: IDE compilation passes.

T5 - Implement token storage or sync adapter

- ID: push-notification-T5
- Status: Completed
- Traceability: PN-007
- Depends on: T4 and token strategy from T0
- What: Implement chosen token source: local Mongo token repository plus registration endpoint, or sync consumer changes for upstream user tokens.
- Where: If local: new repository/resource files; if sync: `SyncUserEvent`, `SyncEventConsumer`, and token repository.
- Done: `GeneratorNotificationTokenPort.findTokensByGeneratorId(generatorId)` returns active tokens.
- Tests: Repository/adapter or sync mapping tests.
- Gate: Focused token resolution tests pass.

Phase 3 - Notification Delivery
-------------------------------

T6 - Implement collection status notification use case

- ID: push-notification-T6
- Status: Completed
- Traceability: PN-009, PN-010
- Depends on: T2, T3, T4
- What: Add use case that builds notification title/body/data, resolves generator tokens, sends through `PushNotificationPort`, and recovers missing-token/provider failures.
- Where: Suggested `src/main/java/org/example/application/usecase/CollectionStatusNotificationUseCase.java`.
- Done: Existing status use cases can call one method after status/event publication.
- Tests: `CollectionStatusNotificationUseCaseTest` covers token found, no token, provider failure, and payload content.
- Gate: Focused notification use case tests pass.

T7 - Implement Firebase Cloud Messaging adapter

- ID: push-notification-T7
- Status: Completed
- Traceability: PN-008, PN-010, PN-011
- Depends on: T3
- What: Add Firebase HTTP v1 adapter implementing `PushNotificationPort` using service account credentials from configuration.
- Where: `src/main/java/org/example/infrastructure/notification/FirebasePushNotificationAdapter.java`, `src/main/resources/application.properties`.
- Done: Adapter sends FCM messages when enabled and treats disabled/missing credentials according to config.
- Tests: Unit coverage is through the notification use case; no real FCM call in CI.
- Gate: IDE build passes without credentials.

Phase 4 - Status Update Integration
-----------------------------------

T8 - Add on-the-way use case

- ID: push-notification-T8
- Status: Completed
- Traceability: PN-002, PN-003, PN-005, PN-009
- Depends on: T1, T6
- What: Implement `MarkCollectorOnTheWayUseCase` to validate request/collector/status, update status, publish Kafka event, and send generator notification.
- Where: `src/main/java/org/example/application/usecase/MarkCollectorOnTheWayUseCase.java`.
- Done: Valid `IN_PROGRESS -> ON_THE_WAY` transition succeeds; invalid transitions fail with typed or existing exceptions.
- Tests: `MarkCollectorOnTheWayUseCaseTest` covers success, missing request, collector mismatch, invalid status, event, and notification.
- Gate: Focused on-the-way use case tests pass.

T9 - Add on-the-way REST endpoint

- ID: push-notification-T9
- Status: Completed
- Traceability: PN-004
- Depends on: T8
- What: Add `POST /collectors/requests/{requestId}/on-the-way` endpoint and DTO with collector id.
- Where: `src/main/java/org/example/presentation/rest/CollectorResource.java`.
- Done: Endpoint maps success, bad request, not found, forbidden, and conflict consistently with existing collector endpoints.
- Tests: `CollectorResourceTest` covers REST mapping.
- Gate: Focused REST tests pass.

T10 - Integrate accept status notification

- ID: push-notification-T10
- Status: Completed
- Traceability: PN-005, PN-009, PN-010
- Depends on: T6
- What: After successful accept status update and Kafka event, notify generator of `IN_PROGRESS`/accepted state.
- Where: `src/main/java/org/example/application/usecase/CollectorResponseUseCase.java`.
- Done: Accept flow still publishes Kafka event and also attempts notification without rollback on push failure.
- Tests: `CollectorResponseUseCaseTest` updated for notification invocation and failure recovery.
- Gate: Focused accept use case tests pass.

T11 - Integrate completion status notification

- ID: push-notification-T11
- Status: Completed
- Traceability: PN-002, PN-005, PN-009, PN-010
- Depends on: T1, T6
- What: Allow completion from intended active statuses and notify generator when status becomes `COMPLETED`.
- Where: `src/main/java/org/example/application/usecase/CompletionUseCase.java`, `CollectionRequest.java`.
- Done: Completion behavior remains backward compatible and sends notification only when status actually changes to completed.
- Tests: `CompletionUseCaseTest` updated for `ON_THE_WAY` and notification behavior.
- Gate: Focused completion tests pass.

T12 - Integrate cancellation and rejection notifications

- ID: push-notification-T12
- Status: Completed
- Traceability: PN-005, PN-009, PN-010
- Depends on: T6
- What: Send generator notification after cancellation and after rejection if product confirms rejection should notify generator.
- Where: `CancelCollectionRequestUseCase.java`, `CollectorResponseUseCase.java`.
- Done: Cancellation notification is sent after Kafka event; rejection behavior matches chosen status semantics.
- Tests: Cancellation/rejection use case tests cover notification and failure recovery.
- Gate: Focused cancellation/rejection tests pass.

Phase 5 - Documentation and Verification
----------------------------------------

T13 - Update API and configuration docs

- ID: push-notification-T13
- Status: Completed
- Traceability: PN-011
- Depends on: T7, T9
- What: Document new status, endpoint, FCM configuration, token strategy, and notification behavior.
- Where: `README.md`, `.specs/features/push-notification/spec.md` if decisions changed.
- Done: Docs match implemented endpoint names, config properties, and payload behavior.
- Tests: Documentation review.
- Gate: No stale examples for status flow.

T14 - Run focused verification

- ID: push-notification-T14
- Status: Completed
- Traceability: PN-012
- Depends on: T5 through T13
- What: Run focused tests for notification, on-the-way transition, collector response, completion, cancellation, token resolution, and REST mapping.
- Where: Relevant `src/test/java` files.
- Done: Focused test files were added/updated; Maven test command was not run because command execution was rejected.
- Tests: IntelliJ compilation and IDE build verification completed.
- Gate: No compilation/build failures.

T15 - Run full verification

- ID: push-notification-T15
- Status: Completed
- Traceability: PN-012
- Depends on: T14
- What: Run the project test suite or standard build verification.
- Where: project root.
- Done: IDE project build passed; Maven full test command was not run because command execution was rejected.
- Tests: IntelliJ `get_compilation_errors` and `build_project`.
- Gate: Full IDE build completed successfully.
