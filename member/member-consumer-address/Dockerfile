# Build stage
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy parent pom and all modules
COPY pom.xml .
COPY member-common/pom.xml member-common/
COPY member-api/pom.xml member-api/
COPY member-consumer-ids/pom.xml member-consumer-ids/
COPY member-consumer-address/pom.xml member-consumer-address/

# Copy source code
COPY member-common/src member-common/src
COPY member-consumer-address/src member-consumer-address/src

RUN apt-get update && apt-get install -y maven && \
    mvn clean package -DskipTests -pl member-common,member-consumer-address -am

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/member-consumer-address/target/*.jar app.jar

EXPOSE 9011

ENTRYPOINT ["java", "-jar", "app.jar"]
