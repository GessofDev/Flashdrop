# =============================================================================
# delivery-service Dockerfile
# =============================================================================
# Build context: services/  (NOT services/delivery-service/)
# This is required because delivery-service depends on :shared-observability,
# which is a sibling module in the services/ multi-project build.
#
# Build command:
#   docker build -t delivery-service:latest \
#     -f delivery-service/Dockerfile .
#
# Run command:
#   docker run --rm -p 8084:8084 \
#     -e SPRING_PROFILES_ACTIVE=delivery,mock-orders \
#     -e DELIVERY_DB_HOST=<rds-endpoint> \
#     -e DELIVERY_DB_PORT=5432 \
#     -e DELIVERY_DB_NAME=delivery_db \
#     -e DELIVERY_DB_USER=delivery_svc \
#     -e DELIVERY_DB_PASSWORD=<password> \
#     -e INTERNAL_API_KEY=<shared-secret> \
#     delivery-service:latest
# =============================================================================

FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Root Gradle scaffolding
COPY gradle gradle
COPY gradlew settings.gradle.kts gradle.properties ./

# Both modules: delivery-service needs shared-observability on runtime classpath
COPY shared-observability shared-observability
COPY delivery-service delivery-service

# Strip CRLF line endings (gradlew ships with CRLF on Windows checkouts, breaks /bin/sh on Alpine)
# and make sure gradlew is executable regardless of host umask.
RUN sed -i 's/\r$//' gradlew gradlew.bat && chmod +x gradlew && ./gradlew :delivery-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/delivery-service/build/libs/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
