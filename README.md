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
- `addresses-sync` - Address data synchronization
- `collector-sync` - Collector data synchronization

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
- `addresses` - Address data synced from user service with enrichment metadata
- `address_cache` - Cache for geocoded addresses with TTL expiration

## Address Enrichment Flow

The service automatically enriches address data via multiple external services:

### Enrichment Process

```
SyncAddressEvent / SyncCollectorEvent.address → AddressEnrichmentAdapter
  │
  ├─ Step 1: Check if coordinates already provided
  │   └─ YES → Return address as PROVIDED
  │
  ├─ Step 2: Generate ID
  │   └─ ID = SHA-256(cep+street+number+city) if CEP present, else UUID
  │
  ├─ Step 3: Check for duplicate addresses
  │   └─ Query by (zipCode, street, number, city, state)
  │   └─ If found → Reuse ID, skip enrichment
  │
  ├─ Step 4: ViaCEP enrichment (if enabled)
  │   ├─ Lookup postal code → Extract city, state, street
  │   └─ Enrich missing fields
  │
  ├─ Step 5: Check address cache
  │   └─ Query cache by (cep+number+street+city)
  │   └─ If found → Use cached coordinates
  │
  ├─ Step 6: Nominatim geocoding
  │   ├─ Build query from enriched address
  │   └─ Lookup coordinates (latitude, longitude)
  │
  ├─ Step 7: Cache result
  │   ├─ Store in address_cache collection
  │   ├─ Add source metadata (viacep/nominatim/cache)
  │   └─ Set TTL for automatic expiration
  │
  └─ Step 8: Set enrichment status
      ├─ ENRICHED → Coordinates found
      ├─ ADDRESS_UNVERIFIED → No coordinates found
      ├─ PROVIDED → Coordinates in input
      └─ FAILED → Enrichment error
```

### Address Entity Fields

```java
public class Address {
    // Existing fields
    private String id;              // Generated or provided address ID
    private String street;          // Street name
    private String city;            // City name
    private String zipCode;         // Postal code (CEP in Brazil)
    private Double latitude;        // Geocoded latitude
    private Double longitude;       // Geocoded longitude
    
    // New enrichment fields
    private String number;          // Street number (enriched)
    private String state;           // State/province (from ViaCEP)
    private String enrichmentStatus; // PENDING, ENRICHED, ADDRESS_UNVERIFIED, PROVIDED, FAILED, SKIPPED
    private String enrichmentSource; // viacep, nominatim, cache, provided
}
```

### Configuration

```properties
# Enable/disable enrichment processing
enrichment.enabled=true

# ViaCEP configuration (Brazilian postal code service)
viacep.enabled=true
viacep.endpoint=https://viacep.com.br/ws

# Nominatim configuration (OpenStreetMap geocoding)
nominatim.endpoint=https://nominatim.openstreetmap.org

# Cache and timeout settings
enrichment.cache.ttl=86400                    # 24 hours
enrichment.timeout.ms=5000                   # 5 seconds
enrichment.user-agent=collections-service/1.0
```

### Service Integrations

1. **ViaCEP** (https://viacep.com.br/)
   - Enriches CEP with street, city, state
   - Brazilian postal code service
   - No rate limiting for integration use

2. **Nominatim** (https://nominatim.openstreetmap.org/)
   - Geocodes addresses to coordinates
   - OpenStreetMap reverse geocoding
   - Respects rate limits (1 request/sec, user-agent required)

### MongoDB Indexes

Run `db-migration.js` to create required indexes:

```javascript
// Unique compound index for duplicate detection
db.addresses.createIndex({
    zipCode: 1, street: 1, number: 1, city: 1, state: 1
}, { unique: true, sparse: true });

// TTL index for cache expiration
db.address_cache.createIndex(
    { createdAt: 1 },
    { expireAfterSeconds: 86400 }
);
```

### Kafka Topics & Events

#### Consumer Topics (Sync from User Service)
- `addresses-sync` - **Enriched** with coordinates and enrichment metadata
- `collector-sync` - **Enriched** through the same address flow; collector records store the returned address ID/reference

#### Error Handling
- Enrichment failures are logged but address is persisted
- Fallback to Nominatim if ViaCEP fails
- Graceful degradation: returns address without coordinates if all enrichment fails
- Circuit breaker for external service timeouts

## MongoDB Collections

- `collection_requests` - Collection request documents
- `collectors` - Collector data synced from user service
- `materials` - Material data synced from user service
- `addresses` - Address data synced from user service with enrichment metadata
- `address_cache` - Cache for geocoded addresses with TTL expiration

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

```
// Example: Create request reactively
Uni<CollectionRequest> request = collectionRequestUseCase.createRequest(/* params */);
request.subscribe().withSubscriber(/* subscriber */);
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

