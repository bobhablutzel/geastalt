# Geastalt Mono-Repo

## Documentation
Documentation maintained in a README.md at both the project and module level
Architecture diagrams use Mermaid notation for integration with GitHub

## Structure
- Mono-repo containing multiple independent systems
- Each system builds and deploys independently (no shared parent POM)
- Docker context is per-system directory

## Member Service (member/)

### Architecture
- Spring Boot application with gRPC and REST APIs
- PostgreSQL database at 192.168.1.17:5432
- Kafka running on localhost: 9092
- Runs on Kubernetes (minikube locally)
- 4 replicas configured in deployment
- gRPC port: 9001, REST port: 9002

### Building and Deploying
```bash
# Build all modules (from member/ directory)
cd member
mvn clean package -DskipTests

# Build Docker images for minikube
eval $(minikube docker-env)
docker build -t member-api:latest -f member-api/Dockerfile .
docker build -t member-consumer-ids:latest -f member-consumer-ids/Dockerfile .
docker build -t member-consumer-address:latest -f member-consumer-address/Dockerfile .

# Restart deployment
kubectl rollout restart deployment/member-api && kubectl rollout status deployment/member-api --timeout=120s

# Port forward for gRPC testing
kubectl port-forward service/member-api 9001:9001

# Check for stale local processes before testing (had issues with old Java process on port 9001)
lsof -i :9001
```

### Testing gRPC Endpoints
```bash
# List available methods
grpcurl -plaintext localhost:9001 list com.geastalt.member.grpc.MemberService

# Example calls
grpcurl -plaintext -d '{"member_id": 11780449}' localhost:9001 com.geastalt.member.grpc.MemberService/GetMemberById
grpcurl -plaintext -d '{"last_name": "smith", "max_results": 25}' localhost:9001 com.geastalt.member.grpc.MemberService/SearchMembers
```

### Load Testing
- Load test class: `member/member-api/src/test/java/com/geastalt/member/GrpcLoadTest.java`
- Tests all gRPC endpoints with burn-in phase followed by sustained load
- Usage: `java GrpcLoadTest [concurrency] [burnInSeconds] [testDurationSeconds]`
- Defaults: 50 concurrent, 30s burn-in, 300s (5 min) test

### Key Files
- Proto definitions: `member/member-api/src/main/proto/member.proto`
- gRPC service impl: `member/member-api/src/main/java/com/geastalt/member/grpc/MemberServiceImpl.java`
- JDBC repository: `member/member-common/src/main/java/com/geastalt/member/repository/MemberSearchJdbcRepository.java`
- Application config: `member/member-api/src/main/resources/application.yml`
- Kubernetes manifests: `member/terraform/kubernetes/`

### Database
- ~6.6M members in `members` table
- Alternate IDs in `member_alternate_ids` table (types: NEW_NATIONS, OLD_NATIONS, PAN_HASH, MEMBER_TUPLE)
- Connection pool: HikariCP (100 max, 20 min idle)
- Batch fetching used to avoid N+1 query problems

### Password and credentials
- Database credentials and other credentials in the kubernetes secret vault under member-api-secret
  - Database password is DB_PASSWORD
  - USPS key and secret are USPS_CLIENT_ID and USPS_CLIENT_SECRET, respectively

### gRPC Endpoints
- GetMemberById - lookup by primary member ID
- GetMemberByAlternateId - lookup by alternate ID (NEW_NATIONS, etc.)
- SearchMembers - search by last name (supports wildcards like "smith*")
- SearchMembersByPhone - search by phone number
- GetAddresses/GetEmails/GetPhones - get member contact info
- Add/Update/Remove operations for addresses, emails, phones

### Recent Changes
- Restructured from standalone repo to geastalt mono-repo
- Renamed packages from com.nationsbenefits to com.geastalt
- Refactored external IDs to alternate IDs with multiple types per member
- Fixed N+1 query problem in search endpoints using batch fetching
- Added GetMemberById gRPC endpoint
