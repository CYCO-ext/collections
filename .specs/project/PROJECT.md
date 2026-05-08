# Waste Collection Microservice

Purpose
-------
Manage waste collection requests: generators submit requests specifying materials and address; the service finds nearby collectors, the generator selects one, the collector accepts and completes the collection.

Primary Goals
-------------
- Provide a reliable, low-latency API for creating collection requests and retrieving nearby collectors.
- Ensure correct state transitions (PENDING → IN_PROGRESS → COMPLETED) with event notifications.
- Integrate with MongoDB for persistence and Kafka for sync/events.

Non-goals
---------
- No long-term backup/archival requirements.

SLAs / Constraints
------------------
- Deployment target: Heroku (container-based). Open to alternative suggestions.
- Auth: JWT tokens for API access.
- Expected load: medium (tens to low hundreds reqs/minute).

Stakeholders
------------
- Generators (users creating collection requests)
- Collectors (users who accept and perform collections)
- Platform operators

Key Decisions
-------------
- Java 21 + Quarkus reactive stack
- MongoDB reactive client
- Kafka for inter-service sync and events
- Hexagonal architecture

Docs language: English

