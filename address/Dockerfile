# Copyright (c) 2026 Bob Hablutzel. All rights reserved.
#
# Licensed under a dual-license model: freely available for non-commercial use;
# commercial use requires a separate license. See LICENSE file for details.
# Contact license@geastalt.com for commercial licensing.

# Build stage
FROM eclipse-temurin:21.0.9_10-jdk AS build

WORKDIR /app

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy POM and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src src

# Build
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21.0.9_10-jre-alpine-3.22

WORKDIR /app

# Create non-root user
RUN addgroup -S address && adduser -S address -G address
USER address

# Copy built artifact
COPY --from=build /app/target/*.jar app.jar

# Expose gRPC and management ports
EXPOSE 9010 9011

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q --spider http://localhost:9011/actuator/health/liveness || exit 1

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
