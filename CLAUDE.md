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

### Async Modules (Kafka-based)
- Prefix: `async-` for all Kafka consumer function apps
- Convention: `async-<verb>-<noun>` (e.g., `async-generate-ffpe-id`)

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
docker build -t async-generate-ffpe-id:latest -f async-generate-ffpe-id/Dockerfile .
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

## Address Verification Service (address/)

### Architecture
- Stateless gRPC verification facade (no database, no Kafka)
- Accepts addresses, routes to verification providers (USPS, Smarty), returns results
- Provider routing engine: configurable per-country routing with count-based or percentage-based distribution and automatic fallback on provider error
- Format verification for US, CA, GB addresses
- International address model: locality, administrative_area, postal_code, country_code (ISO 3166-1 numeric: 840=US, 124=CA, 826=GB)
- gRPC port: 9010, HTTP management port: 9011

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

### Testing gRPC Endpoints
```bash
# List available services
grpcurl -plaintext localhost:9010 list

# Verify an address (country_code is ISO 3166-1 numeric: 840=US, 124=CA, 826=GB)
grpcurl -plaintext -d '{"address":{"country_code":840,"address_lines":["123 Main St"],"locality":"Springfield","administrative_area":"IL","postal_code":"62704"}}' \
  localhost:9010 com.geastalt.address.grpc.AddressService/VerifyAddress

# Verify address format
grpcurl -plaintext -d '{"address":{"country_code":840,"address_lines":["123 Main St"],"locality":"Springfield","administrative_area":"IL","postal_code":"62704"}}' \
  localhost:9010 com.geastalt.address.grpc.AddressService/VerifyAddressFormat

# List available providers
grpcurl -plaintext -d '{}' localhost:9010 com.geastalt.address.grpc.AddressService/GetProviders
```

### Key Files
- Proto definition: `address/src/main/proto/address_service.proto`
- gRPC service: `address/src/main/java/com/geastalt/address/grpc/AddressGrpcService.java`
- Provider registry: `address/src/main/java/com/geastalt/address/provider/ProviderRegistry.java`
- Provider router: `address/src/main/java/com/geastalt/address/provider/routing/ProviderRouter.java`
- USPS provider: `address/src/main/java/com/geastalt/address/provider/usps/UspsValidationProvider.java`
- Smarty provider: `address/src/main/java/com/geastalt/address/provider/smarty/SmartyValidationProvider.java`
- Format verifiers: `address/src/main/java/com/geastalt/address/format/`
- Application config: `address/src/main/resources/application.yml`
- Helm chart: `address/helm/address/`

### Verification Providers
- **USPS** (`usps`): US addresses only (country 840). Uses OAuth. Config prefix: `usps`
- **Smarty** (`smarty`): US (840), CA (124), GB (826). Uses auth-id/auth-token query params. Config prefix: `smarty`
  - US → Smarty US Street API; CA/GB → Smarty International Street API

### Provider Routing
- Configured in `address.providers.routing-configuration` (country code → strategy map)
- Single provider per country: `provider: smarty`
- Failover: `strategy: failover, specification: usps,smarty` — fixed priority order, tries next on error
- Round-robin: `strategy: round_robin, specification: usps,smarty` — rotates primary provider each request
- Count-based: `strategy: counter, specification: usps:3,smarty` — cycles by absolute count
- Percentage-based: `strategy: percentage, specification: usps:70,smarty` — distributes by percentage per 100-request block
- Automatic fallback: on PROVIDER_ERROR, tries next provider in the configured order
- Routing strategies implemented as sealed interface hierarchy (`ProviderLoadBalancerStrategy`)

### gRPC Endpoints
- VerifyAddress - verify/standardize an address using routed providers or explicit provider override
  - Response includes optional `latitude`/`longitude` (Smarty populates; USPS does not)
- VerifyAddressFormat - verify address format (ZIP, state codes, postal codes) for US/CA/GB
- GetProviders - list available verification providers and their supported countries

### Password and credentials
- Kubernetes secret: `address-secret`
  - USPS credentials: `USPS_CLIENT_ID`, `USPS_CLIENT_SECRET`
  - Smarty credentials: `SMARTY_AUTH_ID`, `SMARTY_AUTH_TOKEN`

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

## Coding Standards
- Variables should be defined as final wherever possible
- Formal parameters should be defined as final wherever possible
- Local variables should be defined as var wherever possible (inferred types)
- Prefer streaming implementations over for loops
