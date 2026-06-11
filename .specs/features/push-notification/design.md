# Design: push-notification

Overview
--------

Add generator push notifications for collection status updates using Firebase Cloud Messaging, and add an explicit
`ON_THE_WAY` status for collectors travelling to the pickup address.

The design keeps status updates in application use cases, keeps Kafka event publication intact, and adds push delivery
behind outbound ports. Firebase remains an infrastructure concern.

Status Model
------------

Current statuses:

- `PENDING`
- `IN_PROGRESS`
- `COMPLETED`
- `REJECTED`
- `CANCELLED`

New status:

- `ON_THE_WAY`

Suggested transition rules:

- `PENDING -> IN_PROGRESS`: collector accepts request.
- `IN_PROGRESS -> ON_THE_WAY`: collector marks that they are travelling to the generator.
- `IN_PROGRESS -> COMPLETED`: allowed for backward compatibility if both confirmations are already present.
- `ON_THE_WAY -> COMPLETED`: allowed when both generator and collector confirmations are present.
- `PENDING | IN_PROGRESS | ON_THE_WAY -> CANCELLED`: allowed by existing actor rules unless already completed/cancelled.
- `PENDING -> REJECTED` or collector rejection behavior remains as currently implemented, noting the current code clears
  selected collector without setting `REJECTED`.

Primary On-The-Way Flow
-----------------------

1. Client calls `POST /collectors/requests/{requestId}/on-the-way` with collector id in the request body.
2. REST maps the request to `MarkCollectorOnTheWayUseCase`.
3. Use case loads the collection request by id.
4. Use case validates the request exists, selected collector matches the actor, and status is `IN_PROGRESS`.
5. Use case sets status to `ON_THE_WAY` and updates persistence.
6. Use case publishes Kafka `COLLECTION_ON_THE_WAY` event.
7. Use case calls notification publisher for the generator.
8. Notification failure is logged and swallowed after status/event work succeeds.
9. Endpoint returns HTTP 200 or 204.

Notification Flow
-----------------

1. A status-changing use case updates a collection status.
2. The use case publishes the existing Kafka event through `EventPort`.
3. The use case invokes `CollectionStatusNotificationUseCase` or a shared notification publisher with the updated
   request and event type.
4. Notification publisher resolves generator FCM token(s) through `GeneratorNotificationTokenPort`.
5. If no token exists, it logs and returns success/no-op.
6. If token(s) exist, it builds a `PushNotificationMessage` and calls `PushNotificationPort`.
7. Firebase adapter sends the message through FCM.
8. Adapter failures are logged and recovered so collection status changes are not rolled back.

Architecture
------------

Domain:

- `CollectionRequest.Status`
    - Add `ON_THE_WAY`.
    - Update active-status helpers as needed.
    - `canCancel()` should include `ON_THE_WAY` if cancellation remains allowed while the collector is travelling.
    - `canMarkCompleted()` should include `ON_THE_WAY`; optionally keep `IN_PROGRESS` for backward compatibility.

Application ports:

- `PushNotificationPort`
    - Sends push notification messages.
    - Suggested method: `Uni<Void> send(PushNotificationMessage message)`.
- `GeneratorNotificationTokenPort`
    - Resolves FCM token(s) for a generator id.
    - Suggested method: `Uni<List<String>> findTokensByGeneratorId(String generatorId)`.

Application models:

- `PushNotificationMessage`
    - `recipientUserId`, `tokens`, `title`, `body`, `data`.
- `CollectionStatusNotificationCommand`
    - `eventType`, `requestId`, `generatorId`, `collectorId`, `status`, `timestamp`.

Application use cases:

- `MarkCollectorOnTheWayUseCase`
    - Validates transition and actor.
    - Updates status.
    - Publishes Kafka event.
    - Publishes generator notification.
- `CollectionStatusNotificationUseCase`
    - Encapsulates notification payload creation, token lookup, no-token behavior, and failure recovery.
- Existing status-changing use cases to integrate:
    - `CollectorResponseUseCase.acceptRequest` for `COLLECTION_ACCEPTED`.
    - `CompletionUseCase.tryMarkCompleted` for `COLLECTION_COMPLETED`.
    - `CancelCollectionRequestUseCase.cancel` for `COLLECTION_CANCELLED`.
    - `CollectorResponseUseCase.rejectRequest` for rejection notification if product wants generator notified on
      rejection.

Infrastructure:

- `FirebasePushNotificationAdapter`
    - Implements `PushNotificationPort`.
    - Initializes Firebase app from configured credentials.
    - Sends FCM messages to one or many tokens.
- `GeneratorNotificationTokenRepository` or equivalent token adapter.
    - Stores or reads generator FCM tokens.
    - If upstream user service owns tokens, implement sync consumer changes instead of local registration.

Token Strategy Options
----------------------

Option A: Local token registration endpoint

- Add collection-service endpoint such as `PUT /api/generators/{generatorId}/notification-token`.
- Store generator id, token, platform, createdAt, updatedAt, and enabled flag in MongoDB.
- Good when the mobile app can call this service directly after login.

Option B: User-service sync

- Extend user sync events to include FCM token(s).
- Store tokens from Kafka sync events.
- Good when user profile/auth service owns notification device state.

Recommendation for MVP:

- Use Option A if no upstream token sync contract exists yet.
- Keep `GeneratorNotificationTokenPort` so Option A can be replaced by Option B later without changing status use cases.

Proposed API
------------

On-the-way endpoint:

```text
POST /collectors/requests/{requestId}/on-the-way
```

Runtime URL:

```text
POST /api/collectors/requests/{requestId}/on-the-way
```

Request body:

```json
{
  "collectorId": "collector-123"
}
```

Success:

```text
HTTP 200
```

Invalid status:

```text
HTTP 409
Request cannot be marked on the way from status: PENDING
```

Notification registration endpoint if local token strategy is chosen:

```text
PUT /api/generators/{generatorId}/notification-token
```

Request body:

```json
{
  "token": "fcm-token",
  "platform": "ANDROID"
}
```

Firebase Configuration
----------------------

Suggested properties/environment:

- `firebase.enabled=true|false`
- `firebase.project-id`
- `firebase.credentials-path`
- `firebase.dry-run=false`

Implementation should avoid committing credential files. Heroku/container deployment should provide the credentials file
as a mounted secret and set `FIREBASE_CREDENTIALS_PATH` to that file path.

Notification Payload
--------------------

Suggested data payload:

```json
{
  "eventType": "COLLECTION_ON_THE_WAY",
  "requestId": "request-123",
  "generatorId": "generator-123",
  "collectorId": "collector-123",
  "status": "ON_THE_WAY",
  "timestamp": "2026-06-11T10:00:00Z"
}
```

Suggested visible message examples:

- `IN_PROGRESS`: `Your collection was accepted.`
- `ON_THE_WAY`: `The collector is on the way.`
- `COMPLETED`: `Your collection was completed.`
- `CANCELLED`: `Your collection was cancelled.`
- `REJECTED`: `A collector rejected your collection request.`

Failure Handling
----------------

- Status update failure stops the flow and returns error as today.
- Kafka event failure should follow existing behavior unless product changes event durability semantics.
- Missing token returns success/no-op from notification flow.
- Firebase send failure is logged and recovered after status update and Kafka event success.
- Invalid token cleanup can be deferred unless Firebase error classification is implemented in MVP.

Testing Strategy
----------------

Unit tests:

- `MarkCollectorOnTheWayUseCaseTest`
    - Success from `IN_PROGRESS`.
    - Reject missing request.
    - Reject collector mismatch.
    - Reject invalid status.
    - Publishes Kafka event and notification.
- `CollectionStatusNotificationUseCaseTest`
    - Sends when token exists.
    - No-op when no token exists.
    - Recovers/logs push failures.
    - Builds expected data payload.
- Existing use case tests:
    - Accept publishes notification.
    - Completion publishes notification when status becomes `COMPLETED`.
    - Cancel publishes notification.
    - Completion rules accept `ON_THE_WAY`.
- REST tests:
    - `POST /collectors/requests/{requestId}/on-the-way` maps success and errors.

Integration/adapter tests:

- Firebase adapter should be unit-tested with a wrapper/fake where possible; avoid real Firebase calls in CI.
- Token repository mapping tests if a local Mongo token collection is implemented.

Risks
-----

- Token ownership is currently undefined; implementation should not hardcode assumptions without confirming the chosen
  strategy.
- Firebase Admin SDK initialization can block or fail on bad credentials; initialize lazily or fail clearly at startup
  depending on deployment preference.
- Push notification failures should not make collection status updates flaky.
- Adding `ON_THE_WAY` changes status filters/search behavior and any clients that assume a fixed status enum.
