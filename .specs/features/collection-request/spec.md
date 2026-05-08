# Feature: Collection Request Flow

Overview
--------
Generators submit a collection request (materials, weight, address). The service returns a list of nearby collectors who can collect the requested materials. Generator selects a collector; the collector accepts the request and performs collection. State transitions and events must be published to Kafka.

Acceptance Criteria (brief)
---------------------------
AC1 — Create Collection Request: POST /api/generators/requests returns 201 with request id and PENDING status.
AC2 — Nearby Collectors: GET /api/generators/requests/{id}/collectors returns list ordered by suitability.
AC3 — Select Collector: POST /api/collectors/requests/{id}/select accepts collectorId and notifies the chosen collector.
AC4 — Collector Accepts: POST /api/collectors/requests/{id}/accept transitions to IN_PROGRESS and publishes COLLECTION_ACCEPTED event.
AC5 — Completion: Both parties confirm; final state COMPLETED and COLLECTION_COMPLETED event published.

Traceability IDs
----------------
- CR-001: Create request
- CR-002: Discover collectors
- CR-003: Select collector
- CR-004: Collector accept/reject
- CR-005: Completion confirmations

Notes
-----
- Medium scope: brief spec, design and tasks will be handled inline during Execute.
- Use JWT for securing endpoints; assume user management / JWT issuance is handled externally (token validation only).

