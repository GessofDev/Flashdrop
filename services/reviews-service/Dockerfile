# =============================================================================
# reviews-service Dockerfile
# Build context: services/  (NOT services/reviews-service/)
# This is required because reviews-service depends on :shared-observability,
# which is a sibling module in the services/ multi-project build.
#
# Build command:
#   docker build -t reviews-service:latest \
#     -f reviews-service/Dockerfile .
#
# Run command:
#   docker run --rm -p 8085:8085 \
#     -e SPRING_PROFILES_ACTIVE=reviews,orders \
#     -e REVIEWS_DB_HOST=<rds-endpoint> \
#     -e REVIEWS_DB_PORT=5432 \
#     -e REVIEWS_DB_NAME=reviews_db \
#     -e REVIEWS_DB_USER=reviews_svc \
#     -e REVIEWS_DB_PASSWORD=<password> \
#     -e INTERNAL_API_KEY=<shared-secret> \
#     reviews-service:latest
# =============================================================================

FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Root Gradle scaffolding
COPY gradle gradle
# build.gradle.kts de la raiz es imprescindible: reviews-service/build.gradle.kts
# solo declara el plugin `java`, y quien aplica org.springframework.boot es el
# bloque subprojects{} de la raiz. Sin ese archivo la tarea bootJar no existe y
# el build corta con "task 'bootJar' not found in project ':reviews-service'".
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./

# Both modules: reviews-service needs shared-observability on runtime classpath
COPY shared-observability shared-observability
COPY reviews-service reviews-service

# Strip CRLF line endings (gradlew ships with CRLF on Windows checkouts, breaks /bin/sh on Alpine)
# and make sure gradlew is executable regardless of host umask.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew && ./gradlew :reviews-service:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/reviews-service/build/libs/*.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]
