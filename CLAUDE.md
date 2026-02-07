# Geastalt Mono-Repo

## Documentation
Documentation maintained in a README.md at both the project and module level
Architecture diagrams use Mermaid notation for integration with GitHub

## Structure
- Mono-repo containing multiple independent systems
- Each system builds and deploys independently (no shared parent POM)
- Docker context is per-system directory

## Standardized Patterns

### Helm Charts
- Every chart has `templates/_helpers.tpl` with `name`, `fullname`, `chart`, `labels`, `selectorLabels` helpers
- Kubernetes recommended labels on all resources: `app.kubernetes.io/name`, `/instance`, `/version`, `/managed-by`, `helm.sh/chart`
- Resource names use `include "<chart>.fullname"`, selectors use `include "<chart>.selectorLabels"`
- `Chart.yaml` must include `keywords` and `maintainers: Geastalt`

### Dockerfiles
- Pinned base images: `eclipse-temurin:21.0.9_10-jdk` (build), `eclipse-temurin:21.0.9_10-jre-alpine-3.22` (runtime)
- Non-root user via `addgroup -S` / `adduser -S` (group/user name matches the service)
- `HEALTHCHECK` directive included in every Dockerfile
- JVM options via `JAVA_OPTS` env var: `-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport`
- Entrypoint: `sh -c "java $JAVA_OPTS -jar app.jar"`
- Clean apt lists in build stage: `rm -rf /var/lib/apt/lists/*`

### Java Packages
- Convention: `com.geastalt.<system>.*` (e.g. `com.geastalt.contact`, `com.geastalt.lock`)
- Proto generated code: `com.geastalt.<system>.grpc.generated`
- Maven groupId: `com.geastalt`

## Contact Service (contact/)

### Architecture
- Spring Boot application with gRPC and REST APIs
- PostgreSQL database at 192.168.1.17:5432
- Kafka running on localhost: 9092
- Runs on Kubernetes (minikube locally)
- 4 replicas configured in deployment
- gRPC port: 9001, REST port: 9002

### Building and Deploying
```bash
# Build all modules (from contact/ directory)
cd contact
mvn clean package -DskipTests

# Build Docker images for minikube
eval $(minikube docker-env)
docker build -t contact-api:latest -f contact-api/Dockerfile .
docker build -t contact-consumer-ids:latest -f contact-consumer-ids/Dockerfile .
# Restart deployment
kubectl rollout restart deployment/contact-api && kubectl rollout status deployment/contact-api --timeout=120s

# Port forward for gRPC testing
kubectl port-forward service/contact-api 9001:9001

# Check for stale local processes before testing (had issues with old Java process on port 9001)
lsof -i :9001
```

### Testing gRPC Endpoints
```bash
# List available methods
grpcurl -plaintext localhost:9001 list com.geastalt.contact.grpc.ContactService

# Example calls
grpcurl -plaintext -d '{"contact_id": 11780449}' localhost:9001 com.geastalt.contact.grpc.ContactService/GetContactById
grpcurl -plaintext -d '{"last_name": "smith", "max_results": 25}' localhost:9001 com.geastalt.contact.grpc.ContactService/SearchContacts
```

### Load Testing
- Load test class: `contact/contact-api/src/test/java/com/geastalt/contact/GrpcLoadTest.java`
- Tests all gRPC endpoints with burn-in phase followed by sustained load
- Usage: `java GrpcLoadTest [concurrency] [burnInSeconds] [testDurationSeconds]`
- Defaults: 50 concurrent, 30s burn-in, 300s (5 min) test

### Key Files
- Proto definitions: `contact/contact-api/src/main/proto/contact.proto`
- gRPC service impl: `contact/contact-api/src/main/java/com/geastalt/contact/grpc/ContactServiceImpl.java`
- JDBC repository: `contact/contact-common/src/main/java/com/geastalt/contact/repository/ContactSearchJdbcRepository.java`
- Application config: `contact/contact-api/src/main/resources/application.yml`
- Kubernetes manifests: `contact/terraform/kubernetes/`

### Database
- ~6.6M contacts in `contacts` table
- Alternate IDs in `contact_alternate_ids` table (free-form string types; well-known: NEW_NATIONS, OLD_NATIONS, PAN_HASH, CONTACT_TUPLE)
- Connection pool: HikariCP (100 max, 20 min idle)
- Batch fetching used to avoid N+1 query problems

### Password and credentials
- Database credentials and other credentials in the kubernetes secret vault under external-fpe-key-secret
  - Database password is DB_PASSWORD
  - USPS key and secret are USPS_CLIENT_ID and USPS_CLIENT_SECRET, respectively

### gRPC Endpoints
- GetContactById - lookup by primary contact ID
- GetContactByAlternateId - lookup by alternate ID (NEW_NATIONS, etc.)
- SearchContacts - search by last name (supports wildcards like "smith*")
- SearchContactsByPhone - search by phone number
- GetAddresses/GetEmails/GetPhones - get contact info
- Add/Update/Remove operations for addresses, emails, phones

### Recent Changes
- Restructured from standalone repo to geastalt mono-repo
- Renamed packages from com.nationsbenefits to com.geastalt
- Refactored external IDs to alternate IDs with multiple types per contact
- Fixed N+1 query problem in search endpoints using batch fetching
- Added GetContactById gRPC endpoint

## Address Validation Consumer (address/)

### Architecture
- Spring Boot Kafka consumer for address validation
- Consumes from `tracking.contact.address` Kafka topic (published by contact service)
- Validates/standardizes addresses using USPS API
- Shared database with contact service (PostgreSQL at 192.168.1.17:5432)
- Independent build and deployment (no dependency on contact-common)
- HTTP management port: 9011

### Building and Deploying
```bash
# Build (from address/ directory)
cd address
mvn clean package -DskipTests

# Build Docker image for minikube
eval $(minikube docker-env)
docker build -t address:latest .

# Helm deploy
helm upgrade --install address helm/address -n address --create-namespace
```

### Key Files
- Application entry: `address/src/main/java/com/geastalt/address/AddressApplication.java`
- Kafka consumer: `address/src/main/java/com/geastalt/address/consumer/ValidateAddressConsumer.java`
- Validation service: `address/src/main/java/com/geastalt/address/service/ValidateAddressService.java`
- USPS integration: `address/src/main/java/com/geastalt/address/service/AddressStandardizationService.java`
- Application config: `address/src/main/resources/application.yml`
- Helm chart: `address/helm/address/`

### Database Tables Used
- `contacts` (read-only)
- `contact_addresses` (read/write)
- `standardized_addresses` (read/write)
- `contact_pending_actions` (read/delete)

### Password and credentials
- Kubernetes secret: `address-secret`
  - Database password: `DB_PASSWORD`
  - USPS credentials: `USPS_CLIENT_ID`, `USPS_CLIENT_SECRET`

## Lock Manager (lock/)

### Architecture
- Spring Boot application with gRPC API
- Raft consensus for leader election within regional clusters
- Cross-region quorum for global lock coordination
- Java 21 with virtual threads
- gRPC client port: 9090, inter-region port: 9091

### Building and Deploying
```bash
# Build (from lock/ directory)
cd lock
mvn clean package -DskipTests

# Build Docker image
docker build -t lockmgr:latest .

# Run with docker-compose (3 regions)
docker-compose up -d

# Run full cluster (3 regions x 3 Raft nodes = 9 containers)
docker-compose -f docker-compose-cluster.yml up -d

# Helm deploy (Kubernetes)
helm upgrade --install lockmgr-us-east helm/lockmgr -f helm/lockmgr/values-us-east.yaml -n lockmgr-us-east --create-namespace
```

### Testing gRPC Endpoints
```bash
# List available services
grpcurl -plaintext localhost:9090 list

# Acquire a lock
grpcurl -plaintext -d '{"lock_id": "550e8400-e29b-41d4-a716-446655440000", "client_id": "my-client", "timeout_ms": 30000}' localhost:9090 com.geastalt.lock.grpc.LockService/AcquireLock

# Check lock status
grpcurl -plaintext -d '{"lock_id": "550e8400-e29b-41d4-a716-446655440000"}' localhost:9090 com.geastalt.lock.grpc.LockService/CheckLock

# Release a lock
grpcurl -plaintext -d '{"lock_id": "550e8400-e29b-41d4-a716-446655440000", "client_id": "my-client", "fencing_token": 1}' localhost:9090 com.geastalt.lock.grpc.LockService/ReleaseLock
```

### Key Files
- Proto definitions: `lock/src/main/proto/lock_service.proto`, `raft_service.proto`, `region_service.proto`
- gRPC service impl: `lock/src/main/java/com/geastalt/lock/grpc/LockGrpcService.java`
- Raft consensus: `lock/src/main/java/com/geastalt/lock/raft/RaftNode.java`
- Lock store: `lock/src/main/java/com/geastalt/lock/service/LockStore.java`
- Application config: `lock/src/main/resources/application.yml`
- Helm charts: `lock/helm/lockmgr/`, `lock/helm/lockmgr-istio/`

### gRPC Endpoints
- AcquireLock - acquire a distributed lock with timeout and fencing token
- ReleaseLock - release a previously acquired lock (requires matching fencing token)
- CheckLock - query lock status (holder, TTL, fencing token)
