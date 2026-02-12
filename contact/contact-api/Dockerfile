# Copyright (c) 2026 Bob Hablutzel. All rights reserved.
#
# Licensed under a dual-license model: freely available for non-commercial use;
# commercial use requires a separate license. See LICENSE file for details.
# Contact license@geastalt.com for commercial licensing.

# Build stage - use Debian-based image (glibc) for compatibility
FROM eclipse-temurin:21.0.9_10-jdk AS build

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy parent pom and all modules
COPY pom.xml .
COPY contact-common/pom.xml contact-common/
COPY contact-api/pom.xml contact-api/
COPY async-generate-ffpe-id/pom.xml async-generate-ffpe-id/

# Copy source code
COPY contact-common/src contact-common/src
COPY contact-api/src contact-api/src

# Build
RUN mvn clean package -DskipTests -pl contact-common,contact-api -am

# Runtime stage - Alpine for smaller image and security fixes
FROM eclipse-temurin:21.0.9_10-jre-alpine-3.22

WORKDIR /app

# Create non-root user
RUN addgroup -S contact && adduser -S contact -G contact
USER contact

# Copy built artifact
COPY --from=build /app/contact-api/target/*.jar app.jar

# Expose gRPC and REST ports
EXPOSE 9001 9002

# Health check (HTTP check on actuator port)
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q --spider http://localhost:9002/actuator/health/liveness || exit 1

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
