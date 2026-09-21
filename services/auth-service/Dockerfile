# =============================================================================
# auth-service
# =============================================================================
# Contexto de build: services/   (NO services/auth-service/)
# auth-service depende de :shared-observability, que es un modulo hermano del
# build multi-proyecto de services/.
#
#   docker build -t auth-service:latest -f auth-service/Dockerfile .
#
# Se copian SOLO los dos modulos que hacen falta. La version anterior hacia
# `COPY . .` y eso traia dos problemas: metia el .git completo dentro de la
# imagen, y arrastraba catalog-service, que pide Spring Boot 3.5.16 mientras la
# raiz fija 3.3.5. Gradle configura ese modulo aunque solo se le pida
# :auth-service, y el build moria con "the plugin is already on the classpath
# with a different version". Al no copiarlo, el choque no existe.
# =============================================================================

FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Andamiaje de Gradle de la raiz
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./

COPY shared-observability shared-observability
COPY auth-service auth-service

# gradlew viaja con CRLF desde los checkouts de Windows y /bin/sh de Alpine no
# lo tolera. --configure-on-demand no hace falta hoy (catalog no se copia) pero
# se deja por si el build de la raiz vuelve a crecer.
RUN sed -i 's/\r$//' gradlew \
 && chmod +x gradlew \
 && ./gradlew :auth-service:bootJar --no-daemon --configure-on-demand -x test

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Sin esto el proceso corre como root dentro del contenedor.
RUN addgroup -S app && adduser -S app -G app

COPY --from=build /app/auth-service/build/libs/*.jar app.jar

USER app

EXPOSE 8081

# La JVM por defecto toma el 25% de la memoria del contenedor. Con el limite
# que declara la task definition, eso deja mas de la mitad sin usar.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
