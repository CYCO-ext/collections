# Implementation Tasks

## Phase 1: Domain & Infrastructure

- [x] **T1**: Create domain model (CollectionRequest, Collector, Material, Address entities)
- [x] **T2**: Create MongoDB repositories (CollectionRequestRepository, CollectorRepository, etc.)
- [x] **T3**: Create Kafka consumer for syncing external data (users, collectors, materials, addresses)
- [x] **T4**: Create Kafka producer for publishing collection events

## Phase 2: Core API & Use Cases

- [x] **T5**: Create application ports (CollectionRequestPort, CollectorDiscoveryPort, EventPort)
- [x] **T6**: Implement CollectionRequestUseCase (create request, store to MongoDB, find collectors)
- [x] **T7**: Implement CollectorSelectionUseCase (generator selects collector, publish event)
- [x] **T8**: Implement CollectorResponseUseCase (collector accepts/rejects, update request state)
- [x] **T9**: Implement CompletionUseCase (both parties confirm, mark as COMPLETED)

## Phase 3: REST API & Adapters

- [x] **T10**: Create REST endpoints for generators (POST /requests, GET /requests/{id}/collectors)
- [x] **T11**: Create REST endpoints for collectors (POST /requests/{id}/select, POST /requests/{id}/accept, POST /requests/{id}/reject)
- [x] **T12**: Create REST endpoints for completion (POST /requests/{id}/confirm-generator, POST /requests/{id}/confirm-collector)
- [x] **T13**: Create Kafka event adapters (consumers, producers)

## Phase 4: Testing & Validation

- [x] **T14**: Create unit tests for use cases
- [x] **T15**: Create integration tests for API endpoints
- [x] **T16**: Validate all acceptance criteria

---

## Dependencies Added

- quarkus-mongodb-reactive ✓
- quarkus-kafka-client ✓
- quarkus-rest ✓
- quarkus-smallrye-mutiny ✓
- quarkus-rest-jackson ✓
- quarkus-hibernate-validator ✓
- mockito-core (test) ✓
- mockito-junit-jupiter (test) ✓

---

## Implementation Notes

### Architecture
- Clean/Hexagonal architecture with clear separation:
  - Domain: Entities (CollectionRequest, Collector, Material, Address)
  - Application: Ports (interfaces), Use Cases (business logic), Adapters
  - Infrastructure: Repositories, Kafka events, Configuration
  - Presentation: REST Resources

### Reactive Patterns
- All I/O operations use Mutiny Uni/Multi
- Non-blocking operations throughout request paths
- Reactive Kafka consumers and producers

### Database
- MongoDB for data persistence
- Collections: collection_requests, collectors, materials, addresses
- Async repository operations with Uni

### Messaging
- Kafka topics: sync-materials, sync-addresses, sync-collectors, collection-events
- Event-driven state transitions
- Sync consumers for data synchronization from external service

### REST Endpoints
- Generators: POST /api/generators/requests, GET /api/generators/requests/{id}/collectors
- Collectors: POST /api/collectors/requests/{id}/select, accept, reject
- Completion: POST /api/requests/{id}/confirm-generator/collector

### State Machine
- PENDING → IN_PROGRESS (collector accept)
- IN_PROGRESS → COMPLETED (both confirm)
- PENDING ← PENDING (collector reject)
- REJECTED (optional final state)

---

## Kafka UI Addition Tasks

- [x] **T17**: Add kafka-ui service to docker-compose.yml
- [x] **T18**: Configure environment variables for Kafka connection
- [x] **T19**: Set dependencies and network for kafka-ui service
