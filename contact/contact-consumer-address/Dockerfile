# Build stage - use Debian-based image (glibc) for compatibility
FROM eclipse-temurin:21.0.9_10-jdk AS build

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy parent pom and all modules
COPY pom.xml .
COPY member-common/pom.xml member-common/
COPY member-api/pom.xml member-api/
COPY member-consumer-ids/pom.xml member-consumer-ids/
COPY member-consumer-address/pom.xml member-consumer-address/

# Copy source code
COPY member-common/src member-common/src
COPY member-consumer-address/src member-consumer-address/src

# Build
RUN mvn clean package -DskipTests -pl member-common,member-consumer-address -am

# Runtime stage - Alpine for smaller image and security fixes
FROM eclipse-temurin:21.0.9_10-jre-alpine-3.22

WORKDIR /app

# Create non-root user
RUN addgroup -S member && adduser -S member -G member
USER member

# Copy built artifact
COPY --from=build /app/member-consumer-address/target/*.jar app.jar

# Expose management port
EXPOSE 9011

# Health check (HTTP check on actuator port)
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q --spider http://localhost:9011/actuator/health/liveness || exit 1

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
