# Build stage - use Debian-based image (glibc) for compatibility
FROM eclipse-temurin:21.0.9_10-jdk AS build

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy parent pom and all modules
COPY pom.xml .
COPY contact-common/pom.xml contact-common/
COPY contact-api/pom.xml contact-api/
COPY contact-consumer-ids/pom.xml contact-consumer-ids/
COPY contact-consumer-address/pom.xml contact-consumer-address/

# Copy source code
COPY contact-common/src contact-common/src
COPY contact-consumer-ids/src contact-consumer-ids/src

# Build
RUN mvn clean package -DskipTests -pl contact-common,contact-consumer-ids -am

# Runtime stage - Alpine for smaller image and security fixes
FROM eclipse-temurin:21.0.9_10-jre-alpine-3.22

WORKDIR /app

# Create non-root user
RUN addgroup -S contact && adduser -S contact -G contact
USER contact

# Copy built artifact
COPY --from=build /app/contact-consumer-ids/target/*.jar app.jar

# Expose management port
EXPOSE 9010

# Health check (HTTP check on actuator port)
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q --spider http://localhost:9010/actuator/health/liveness || exit 1

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
