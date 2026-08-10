# syntax=docker/dockerfile:1.6
# Usamos el `mvn` provisto por la imagen base en lugar del wrapper (mvnw)
# porque Docker Desktop sobre Windows a veces no transfiere la carpeta
# oculta `.mvn/wrapper/` desde el build context al container.
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/orders-service-*.jar app.jar
EXPOSE 8083
ENV SERVER_PORT=8083
ENTRYPOINT ["java","-jar","/app/app.jar"]