# member-api

The main API service providing gRPC and REST endpoints for member operations.

## Overview

This module exposes member management functionality through gRPC (primary) and REST APIs. It handles member creation, search, and contact information management.

## Ports

| Protocol | Port | Description |
|----------|------|-------------|
| gRPC | 9001 | Primary API interface |
| REST | 9002 | HTTP API and health endpoints |

## gRPC Endpoints

```mermaid
graph LR
    subgraph MemberService
        A[CreateMember]
        B[GetMemberById]
        C[GetMemberByAlternateId]
        D[SearchMembers]
        E[SearchMembersByPhone]
        F[HasPendingAction]
    end

    subgraph AddressOperations
        G[AddAddress]
        H[UpdateAddress]
        I[GetAddresses]
        J[RemoveAddress]
    end

    subgraph EmailOperations
        K[AddEmail]
        L[UpdateEmail]
        M[GetEmails]
        N[RemoveEmail]
    end

    subgraph PhoneOperations
        O[AddPhone]
        P[UpdatePhone]
        Q[GetPhones]
        R[RemovePhone]
    end

    subgraph PlanOperations
        S[CreatePlan]
        T[UpdatePlan]
        U[GetPlan]
        V[GetPlans]
        W[DeletePlan]
    end

    subgraph MemberPlanOperations
        X[AddMemberPlan]
        Y[UpdateMemberPlan]
        Z[GetMemberPlans]
        AA[GetCurrentMemberPlan]
        AB[RemoveMemberPlan]
    end
```

### Member Operations

| Method | Description |
|--------|-------------|
| `CreateMember` | Create a new member with optional pending actions |
| `GetMemberById` | Retrieve member by primary ID |
| `GetMemberByAlternateId` | Retrieve member by alternate ID (NEW_NATIONS, etc.) |
| `SearchMembers` | Search by last name (supports wildcards like "smith*") |
| `SearchMembersByPhone` | Search by phone number |
| `HasPendingAction` | Check if member has a specific pending action |

### Plan Operations

| Method | Description |
|--------|-------------|
| `CreatePlan` | Create a new plan with carrier information |
| `UpdatePlan` | Update an existing plan |
| `GetPlan` | Retrieve a plan by ID |
| `GetPlans` | List all plans |
| `DeletePlan` | Delete a plan |

### Member Plan Operations

| Method | Description |
|--------|-------------|
| `AddMemberPlan` | Assign a plan to a member with effective/expiration dates (UTC) |
| `UpdateMemberPlan` | Update a member's plan assignment |
| `GetMemberPlans` | List all plans for a member |
| `GetCurrentMemberPlan` | Get the currently active plan for a member |
| `RemoveMemberPlan` | Remove a plan assignment from a member |

Member plans use ISO 8601 UTC datetime format (e.g., `2026-01-01T00:00:00Z`) for effective and expiration dates. Overlapping date ranges for the same member are not allowed.

### Contact Operations

- **Address**: Add, Update, Get, Remove member addresses
- **Email**: Add, Update, Get, Remove member emails
- **Phone**: Add, Update, Get, Remove member phones

## REST Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/members/count` | Get total member count |
| GET | `/api/members/search` | Search members by name |
| GET | `/api/members/by-alternate-id/{id}` | Get member by alternate ID |
| GET | `/api/members/search-by-phone` | Search by phone number |
| POST | `/api/members/generate-alternate-ids` | Batch generate alternate IDs |

## Usage Examples

### Create Member

```bash
grpcurl -plaintext -d '{
  "first_name": "John",
  "last_name": "Doe"
}' localhost:9001 com.geastalt.member.grpc.MemberService/CreateMember
```

### Create Member Without Pending Actions

```bash
grpcurl -plaintext -d '{
  "first_name": "Jane",
  "last_name": "Smith",
  "skip_generate_external_identifiers": true,
  "skip_validate_address": true
}' localhost:9001 com.geastalt.member.grpc.MemberService/CreateMember
```

### Search Members

```bash
grpcurl -plaintext -d '{
  "last_name": "smith*",
  "max_results": 25
}' localhost:9001 com.geastalt.member.grpc.MemberService/SearchMembers
```

### Check Pending Action

```bash
grpcurl -plaintext -d '{
  "member_id": 12345,
  "action_type": "GENERATE_EXTERNAL_IDENTIFIERS"
}' localhost:9001 com.geastalt.member.grpc.MemberService/HasPendingAction
```

### Create a Plan

```bash
grpcurl -plaintext -d '{
  "plan_name": "Gold Plan",
  "carrier_id": 1,
  "carrier_name": "Aetna"
}' localhost:9001 com.geastalt.member.grpc.MemberService/CreatePlan
```

### Add Plan to Member

```bash
grpcurl -plaintext -d '{
  "member_id": 11780449,
  "plan_id": 1,
  "effective_date": "2026-01-01T00:00:00Z",
  "expiration_date": "2026-12-31T23:59:59Z"
}' localhost:9001 com.geastalt.member.grpc.MemberService/AddMemberPlan
```

### Get Current Member Plan

```bash
grpcurl -plaintext -d '{
  "member_id": 11780449
}' localhost:9001 com.geastalt.member.grpc.MemberService/GetCurrentMemberPlan
```

### Get All Plans

```bash
grpcurl -plaintext -d '{}' \
  localhost:9001 com.geastalt.member.grpc.MemberService/GetPlans
```

## Configuration

Key configuration in `application.yml`:

```yaml
grpc:
  server:
    port: 9001

server:
  port: 9002

spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

member:
  pending-actions:
    topics:
      generate-external-identifiers: tracking.member.ids
      validate-address: tracking.member.address
```

## Building

```bash
# From project root
mvn clean package -DskipTests -pl member-common,member-api -am

# Build Docker image
docker build -f member-api/Dockerfile -t member-api:latest .
```

## Running

```bash
# With Maven
cd member-api
mvn spring-boot:run

# With Java
java -jar target/member-api-0.0.1-SNAPSHOT.jar

# With Docker
docker run -p 9001:9001 -p 9002:9002 member-api:latest
```

## Health Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Overall health status |
| `/actuator/health/liveness` | Kubernetes liveness probe |
| `/actuator/health/readiness` | Kubernetes readiness probe |

## Dependencies

- `member-common` - Shared entities and repositories
- `spring-boot-starter-web` - REST support
- `grpc-spring-boot-starter` - gRPC server
- `spring-kafka` - Kafka producer
- `opentelemetry-*` - Distributed tracing
