# ROADMAP — Waste Collection Microservice

Milestones
----------
1. Project initialization & docs (done)
2. Core API: create request + collector discovery (MUST)
3. Selection flow: generator selects collector + notification (MUST)
4. Collector acceptance/rejection + state transitions (MUST)
5. Completion confirmations + event publishing (MUST)
6. Tests & CI integration (MUST)
7. Deploy to Heroku (MUST)
8. Observability: logging, metrics, tracing (optional)
9. Performance tuning & indices (optional)

Next actions
------------
- Implement save-route endpoint, persistence, duplicate blocking, listing, and closure
- Implement collector-address endpoint for retrieving collector address info
- Implement collection-id endpoint for fetching a collection request by id
- Implement search-collections endpoint for status filtering and newest-first ordering
- Implement JWT auth and secure endpoints
- Add CI pipeline and Heroku deployment config
- Create DB indices for collection_requests

