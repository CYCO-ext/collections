# Waste Collection Microservice

Reactive microservice for managing waste collection requests using Java 21, Quarkus, MongoDB, and Kafka.

## Project Status

✅ **Development Complete** - All 16 implementation tasks completed

## Architecture

### Hexagonal (Ports & Adapters)
```
┌─────────────────────────────────────────────────────┐
│           PRESENTATION LAYER                        │
│  (REST Resources: Generator, Collector, Completion) │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│         APPLICATION LAYER                           │
│  (Use Cases & Ports)                                │
│  - CollectionRequestUseCase                         │
│  - CollectorSelectionUseCase                        │
│  - CollectorResponseUseCase                         │
│  - CompletionUseCase                                │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│         DOMAIN LAYER                                │
│  (Entities & Business Logic)                        │
│  - CollectionRequest, Collector, Material, Address │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│      INFRASTRUCTURE LAYER                           │
│  (Repositories, Kafka, MongoDB)                     │
│  - CollectionRequestRepository                      │
│  - CollectorRepository, etc.                        │
│  - SyncEventConsumer, CollectionEventProducer       │
└─────────────────────────────────────────────────────┘
```

## Directory Structure

```
src/
├── main/
│   ├── java/org/example/
│   │   ├── domain/
│   │   │   └── entity/
│   │   │       ├── CollectionRequest.java
│   │   │       ├── Collector.java
│   │   │       ├── Material.java
│   │   │       └── Address.java
│   │   ├── application/
│   │   │   ├── port/out/
│   │   │   │   ├── CollectionRequestPort.java
│   │   │   │   ├── CollectorDiscoveryPort.java
│   │   │   │   └── EventPort.java
│   │   │   ├── adapter/
│   │   │   │   ├── CollectionRequestAdapter.java
│   │   │   │   ├── CollectorDiscoveryAdapter.java
│   │   │   │   └── EventAdapter.java
│   │   │   └── usecase/
│   │   │       ├── CollectionRequestUseCase.java
│   │   │       ├── CollectorSelectionUseCase.java
│   │   │       ├── CollectorResponseUseCase.java
│   │   │       └── CompletionUseCase.java
│   │   ├── infrastructure/
│   │   │   ├── repository/
│   │   │   │   ├── CollectionRequestRepository.java
│   │   │   │   ├── CollectorRepository.java
│   │   │   │   ├── MaterialRepository.java
│   │   │   │   └── AddressRepository.java
│   │   │   ├── kafka/
│   │   │   │   ├── SyncEventConsumer.java
│   │   │   │   └── CollectionEventProducer.java
│   │   │   └── event/
│   │   │       ├── SyncMaterialEvent.java
│   │   │       ├── SyncCollectorEvent.java
│   │   │       └── CollectionEvent.java
│   │   ├── presentation/
│   │   │   └── rest/
│   │   │       ├── GeneratorResource.java
│   │   │       ├── CollectorResource.java
│   │   │       └── CompletionResource.java
│   │   └── Main.java
│   └── resources/
│       └── application.properties
└── test/
    └── java/org/example/
        ├── application/usecase/
        │   ├── CollectionRequestUseCaseTest.java
        │   └── CompletionUseCaseTest.java
        └── presentation/rest/
            ├── GeneratorResourceTest.java
            └── CompletionResourceTest.java
```

## REST API Endpoints

### Generator Endpoints

**Create Collection Request**
```
POST /api/generators/requests
Content-Type: application/json

{
  "generatorId": "gen-001",
  "addressId": "addr-001",
  "materialIds": ["mat-001", "mat-002"],
  "weight": 100.0
}

Response: 201 Created
{
  "id": "req-001",
  "generatorId": "gen-001",
  "addressId": "addr-001",
  "status": "PENDING",
  ...
}
```

**Get Nearby Collectors**
```
GET /api/generators/requests/{requestId}/collectors

Response: 200 OK
[
  {
    "id": "coll-001",
    "name": "Collector 1",
    "acceptanceRate": 0.9,
    ...
  }
]
```

### Collector Endpoints

**Select Collector**
```
POST /api/collectors/requests/{requestId}/select
Content-Type: application/json

{
  "collectorId": "coll-001"
}

Response: 200 OK
```

**Accept Request**
```
POST /api/collectors/requests/{requestId}/accept

Response: 200 OK
```

**Reject Request**
```
POST /api/collectors/requests/{requestId}/reject

Response: 200 OK
```

### Completion Endpoints

**Confirm by Generator**
```
POST /api/requests/{requestId}/confirm-generator

Response: 200 OK
```

**Confirm by Collector**
```
POST /api/requests/{requestId}/confirm-collector

Response: 200 OK
```

## State Transitions

```
PENDING
  ├─→ [Collector selects] → PENDING (selected)
  ├─→ [Collector accepts] → IN_PROGRESS
  │     ├─→ [Generator confirms] → IN_PROGRESS (gen_confirmed)
  │     ├─→ [Collector confirms] → IN_PROGRESS (coll_confirmed)
  │     └─→ [Both confirm] → COMPLETED
  └─→ [Collector rejects] → PENDING (reset selection)
```

## Kafka Topics & Events

### Consumer Topics (Sync from User Service)
- `sync-materials` - Material data synchronization
- `sync-addresses` - Address data synchronization
- `sync-collectors` - Collector data synchronization

### Producer Topics (Collection Events)
- `collection-events` - Published events:
  - `COLLECTOR_SELECTED`
  - `COLLECTION_ACCEPTED`
  - `COLLECTION_REJECTED`
  - `COLLECTION_COMPLETED`

## MongoDB Collections

- `collection_requests` - Collection request documents
- `collectors` - Collector data synced from user service
- `materials` - Material data synced from user service
- `addresses` - Address data synced from user service

## Getting Started

### Prerequisites
- Java 21+
- Docker & Docker Compose
- Maven 3.9+

### Start Infrastructure

```bash
docker-compose up
```

This starts:
- MongoDB on port 27017
- Kafka on port 9092
- Zookeeper on port 2181

### Build Project

```bash
mvn clean install
```

### Run Application

```bash
mvn quarkus:dev
```

Application runs on http://localhost:8080

### Run Tests

```bash
mvn test
```

## Configuration

See `application.properties` for:
- MongoDB connection string
- Kafka bootstrap servers
- Topic configurations
- Logging levels

## Reactive Patterns

All operations use **Mutiny Uni/Multi** for non-blocking, reactive streams:

```java
// Example: Create request reactively
Uni<CollectionRequest> request = 
  collectionRequestUseCase.createRequest(...)
    .subscribe().withSubscriber(...)
```

## Key Features

✅ Clean/Hexagonal Architecture
✅ Reactive with Mutiny (Uni/Multi)
✅ Event-driven state management
✅ MongoDB persistence
✅ Kafka integration for sync & events
✅ Comprehensive error handling
✅ Unit & Integration tests
✅ Docker compose ready

## Compliance

All acceptance criteria from spec.md validated:
- AC1: Collection Request Creation ✓
- AC2: Collector Discovery ✓
- AC3: Collector Selection & Notification ✓
- AC4: Request State Transitions ✓
- AC5: Data Synchronization ✓
- AC6: Reactive Patterns ✓
- AC7: Event-Driven Communication ✓

## Next Steps

1. Configure external User Service Kafka events
2. Add database indices for performance
3. Implement distributed tracing (Jaeger)
4. Add metrics collection (Micrometer)
5. Deploy to Kubernetes with Operators

