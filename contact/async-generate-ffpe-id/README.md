# async-generate-ffpe-id

Kafka consumer service that processes `GENERATE_EXTERNAL_IDENTIFIERS` pending actions.

## Overview

This service listens to the `tracking.contact.ids` Kafka topic and generates external identifiers (NEW_NATIONS alternate IDs) for newly created contacts.

## Port

| Protocol | Port | Description |
|----------|------|-------------|
| HTTP | 9010 | Health endpoints |

## Architecture

```mermaid
flowchart LR
    subgraph Kafka
        topic["tracking.contact.ids"]
    end

    subgraph consumer-ids["async-generate-ffpe-id"]
        listener["GenerateFeistelFpeIdConsumer"]
        service["GenerateFeistelFpeIdService"]
        listener --> service
    end

    subgraph Database["PostgreSQL"]
        contacts[(contacts)]
        alternateIds[(contact_alternate_ids)]
        pendingActions[(contact_pending_actions)]
    end

    topic -->|"consume contactId"| listener
    service -->|"read contact"| contacts
    service -->|"insert alternate ID"| alternateIds
    service -->|"delete pending action"| pendingActions
```

## Processing Flow

```mermaid
sequenceDiagram
    participant Kafka
    participant Consumer as GenerateFeistelFpeIdConsumer
    participant Service as GenerateFeistelFpeIdService
    participant DB as PostgreSQL

    Kafka->>Consumer: Receive contactId
    Consumer->>Service: processGenerateExternalIds(contactId)

    Service->>DB: Find pending action
    alt Pending action not found
        Service-->>Consumer: Skip (already processed)
    else Pending action exists
        Service->>DB: Find contact
        Service->>DB: Check for existing NEW_NATIONS ID
        alt No existing ID
            Service->>Service: Generate external ID (FPE)
            Service->>DB: Save ContactAlternateId
        end
        Service->>DB: Delete pending action
        Service-->>Consumer: Success
    end
```

## Processing Logic

1. **Receive message** from `tracking.contact.ids` topic
2. **Check pending action** exists for the contact
3. **Verify contact** exists in database
4. **Check for existing** NEW_NATIONS alternate ID
5. **Generate ID** using format-preserving encryption (if needed)
6. **Save alternate ID** to `contact_alternate_ids` table
7. **Remove pending action** from `contact_pending_actions` table

## Configuration

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: ${KAFKA_CONSUMER_GROUP_ID:async-generate-ffpe-id}
      auto-offset-reset: earliest
      enable-auto-commit: false
    listener:
      ack-mode: record

contact:
  external-id:
    encryption-key: ${CONTACT_EXTERNAL_ID_KEY}
  pending-actions:
    topics:
      generate-external-identifiers: ${PENDING_ACTION_TOPIC_GENERATE_IDS:tracking.contact.ids}
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker addresses | `localhost:9092` |
| `KAFKA_CONSUMER_GROUP_ID` | Consumer group ID | `async-generate-ffpe-id` |
| `PENDING_ACTION_TOPIC_GENERATE_IDS` | Topic to consume | `tracking.contact.ids` |
| `CONTACT_EXTERNAL_ID_KEY` | Encryption key for ID generation | Required |
| `DB_HOST` | Database host | `192.168.1.17` |
| `DB_PORT` | Database port | `5432` |
| `DB_NAME` | Database name | `contact` |
| `DB_USERNAME` | Database username | `bob` |
| `DB_PASSWORD` | Database password | Required |

## Building

```bash
# From project root
mvn clean package -DskipTests -pl contact-common,async-generate-ffpe-id -am

# Build Docker image
docker build -f async-generate-ffpe-id/Dockerfile -t async-generate-ffpe-id:latest .
```

## Running

```bash
# With Maven
cd async-generate-ffpe-id
mvn spring-boot:run

# With Java
java -jar target/async-generate-ffpe-id-0.0.1-SNAPSHOT.jar

# With Docker
docker run -p 9010:9010 \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e CONTACT_EXTERNAL_ID_KEY=your-key \
  -e DB_PASSWORD=your-password \
  async-generate-ffpe-id:latest
```

## Health Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Overall health status |
| `/actuator/health/liveness` | Kubernetes liveness probe |
| `/actuator/health/readiness` | Kubernetes readiness probe |

## Scaling

- Default: 2 replicas in Kubernetes
- Kafka partitions determine max parallelism
- Each replica joins the same consumer group for load balancing

## Transaction & Acknowledgment Flow

```mermaid
flowchart TD
    A[Kafka Message Received] --> B["@Transactional Service Method"]
    B --> C{Transaction Success?}

    C -->|Yes| D[acknowledgment.acknowledge]
    D --> E[Kafka offset committed]
    E --> F[Message removed from topic]

    C -->|No| G[Transaction rolled back]
    G --> H[Pending action NOT deleted]
    H --> I[No acknowledgment sent]
    I --> J[Message redelivered]
```

The pending action deletion occurs within the `@Transactional` boundary:

1. **Success path**: All DB changes (including pending action deletion) commit atomically, then Kafka offset is acknowledged
2. **Failure path**: Transaction rolls back (pending action remains), message is not acknowledged and will be redelivered

## Error Handling

- Failed messages are retried automatically by Spring Kafka
- Persistent failures should be routed to a dead-letter topic (DLQ)
- Idempotent processing: checks for existing alternate ID before creating
