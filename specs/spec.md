# Waste Collection Microservice Specification

## Overview
Reactive microservice for managing waste collection requests between Generators (waste producers) and Waste Collectors using Java 21 and Quarkus.

---

## Requirements

### R1: Collection Request Management
- Generator creates collection request with address, materials, and weight
- Request stored in MongoDB
- Request contains unique ID and timestamp

### R2: Collector Discovery
- System finds nearby collectors accepting specified materials
- Returns list of available collectors to generator
- Data synced from User service via Kafka events

### R3: Collector Selection Flow
- Generator selects a collector from available list
- Collector receives notification and can accept/reject
- If rejected, generator notified to choose another

### R4: Collection Execution
- Upon acceptance, collection status changes to "IN_PROGRESS"
- Generator and collector can confirm completion
- Both confirmations required to mark as "COMPLETED"

### R5: External Service Integration
- Sync users, collectors, materials, addresses from User service via Kafka
- Store synced data in MongoDB for fast queries
- Publish collection events to Kafka for external system notification

### R6: Architecture & Patterns
- Clean/Hexagonal architecture (ports & adapters)
- Reactive patterns using Uni/Multi (Mutiny)
- Minimal, focused implementation
- Java 21 + Quarkus Reactive + MongoDB + Kafka

---

## Acceptance Criteria

### AC1: Collection Request Creation
- [ ] Generator submits request with address ID, material IDs, weight
- [ ] Request persisted to MongoDB with unique ID and created_at timestamp
- [ ] Request status defaults to "PENDING"

### AC2: Collector Discovery
- [ ] System queries MongoDB for collectors accepting all requested materials
- [ ] System filters collectors by geographic proximity (address-based)
- [ ] Returns list of available collectors with acceptance rate

### AC3: Collector Selection & Notification
- [ ] Generator can select collector from list
- [ ] Selection event published to Kafka
- [ ] Collector receives notification via event stream
- [ ] Collector can respond with accept/reject

### AC4: Request State Transitions
- [ ] PENDING → IN_PROGRESS (on collector accept)
- [ ] IN_PROGRESS → COMPLETED (on both parties confirm)
- [ ] PENDING → PENDING (on collector reject, generator notified)
- [ ] History recorded for state changes

### AC5: Data Synchronization
- [ ] Kafka consumer receives user service events (collectors, materials, addresses)
- [ ] Data upserted into MongoDB collections
- [ ] Sync events trigger on startup and continuously

### AC6: Reactive Patterns
- [ ] All I/O operations use Uni/Multi (Mutiny)
- [ ] No blocking calls in request path
- [ ] Concurrent operations handled reactively

### AC7: Event-Driven Communication
- [ ] Collection events published to Kafka topics
- [ ] Events include request context and state
- [ ] Consumers can react to state changes

---

# Kafka UI Addition Specification

## Overview
Add Kafka UI service to docker-compose.yml for development and monitoring of Kafka topics.

---

## Requirements

### R1: Kafka UI Service
- Add Kafka UI container to docker-compose.yml
- Configure connection to existing Kafka broker
- Expose UI on port 8081

### R2: Configuration
- Connect to Kafka cluster named 'local'
- Use bootstrap servers: kafka:29092
- Depend on Kafka service startup

---

## Acceptance Criteria

### AC1: Service Added
- [x] kafka-ui service present in docker-compose.yml
- [x] Uses provectuslabs/kafka-ui:latest image
- [x] Port 8081:8080 mapped

### AC2: Configuration Correct
- [x] KAFKA_CLUSTERS_0_NAME set to 'local'
- [x] KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS set to 'kafka:29092'
- [x] depends_on kafka service

### AC3: Network Integration
- [x] Connected to collections-network
- [x] Accessible at http://localhost:8081
