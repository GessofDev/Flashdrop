# Pruebas de punta a punta F23 y F24

`f23_f24.py` recorre por API, **a traves del gateway**, el alta de una tienda y un repartidor por un administrador
(F24) y la eliminacion de cuenta de un cliente, un repartidor y un dueno de tienda (F23): rechazo con pedidos en curso,
aceptacion, sesion y token cerrados al instante, datos anonimizados en Orders, Delivery y Catalog.
Imprime `PASS`/`FAIL` por comprobacion y termina con codigo distinto de 0 si algo falla (50 comprobaciones).

Solo usa Python 3 y Docker (consulta las bases con `docker exec`). Crea sus propios usuarios; no toca las cuentas demo.

## Entorno descartable (no toca ningun dato existente)

Cuatro PostgreSQL nuevos (`fdtest-*`, puertos 15481-15484) y un Redis (6399), los cuatro servicios en 18081-18084 y el
gateway en 13000. Requiere los jar construidos (`./gradlew :auth-service:bootJar :delivery-service:bootJar` en `services/`,
`./gradlew bootJar` en `services/catalog-service/` y `./mvnw package -DskipTests` en `services/orders-service/`).

```bash
for spec in auth:15481:flashdrop_auth catalog:15482:flashdrop_catalog orders:15483:flashdrop_orders delivery:15484:delivery_db; do
  IFS=: read n p d <<< "$spec"
  docker run -d --name fdtest-$n -e POSTGRES_PASSWORD=fdtest -e POSTGRES_DB=$d -p 127.0.0.1:$p:5432 postgres:16-alpine
done
docker run -d --name fdtest-redis -p 127.0.0.1:6399:6379 redis:7-alpine

export INTERNAL_API_KEY=fdtest-key
PORT=18081 SPRING_PROFILES_ACTIVE=postgres DB_URL=jdbc:postgresql://127.0.0.1:15481/flashdrop_auth DB_USERNAME=postgres DB_PASSWORD=fdtest \
  FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed JWT_ALLOW_EPHEMERAL=true ACCOUNT_DELETION_POLL_MS=3000 \
  ORDERS_SERVICE_URL=http://127.0.0.1:18083 DELIVERY_SERVICE_URL=http://127.0.0.1:18084 CATALOG_SERVICE_URL=http://127.0.0.1:18082 \
  java -jar services/auth-service/build/libs/auth-service-0.1.0.jar &
SERVER_PORT=18082 SPRING_PROFILES_ACTIVE=postgres DB_URL=jdbc:postgresql://127.0.0.1:15482/flashdrop_catalog DB_USERNAME=postgres DB_PASSWORD=fdtest \
  FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed AUTH_SERVICE_JWKS_URI=http://127.0.0.1:18081/auth/.well-known/jwks.json \
  java -jar services/catalog-service/build/libs/catalog-service-0.0.1-SNAPSHOT.jar &
SERVER_PORT=18083 SPRING_PROFILES_ACTIVE=postgres DB_URL=jdbc:postgresql://127.0.0.1:15483/flashdrop_orders DB_USERNAME=postgres DB_PASSWORD=fdtest \
  AUTH_SERVICE_URL=http://127.0.0.1:18081 CATALOG_SERVICE_URL=http://127.0.0.1:18082 DELIVERY_SERVICE_URL=http://127.0.0.1:18084 \
  DELIVERY_CODE_SECRET=fdtest-secret-fdtest-secret-fdtest-0123 java -jar services/orders-service/target/orders-service-1.0.0.jar &
SERVER_PORT=18084 SPRING_PROFILES_ACTIVE=delivery DELIVERY_DB_HOST=127.0.0.1 DELIVERY_DB_PORT=15484 DELIVERY_DB_NAME=delivery_db \
  DELIVERY_DB_USER=postgres DELIVERY_DB_PASSWORD=fdtest AUTH_SERVICE_JWKS_URI=http://127.0.0.1:18081/auth/.well-known/jwks.json \
  ORDERS_SERVICE_URL=http://127.0.0.1:18083 java -jar services/delivery-service/build/libs/delivery-service-0.1.0.jar &
```

Gateway: copiar `gateway/docker/gateway.yaml`, cambiar los hosts de los servicios por `http://127.0.0.1:1808x`, el puerto a
`13000`, `redis://127.0.0.1:6399` y `cors.origins` por una lista (`["http://localhost:3000"]`), y arrancar desde `gateway/`:

```bash
CONFIG_PATH=/ruta/gateway-test.yaml AUTH_SERVICE_JWKS_URI=http://127.0.0.1:18081/auth/.well-known/jwks.json npx tsx src/index.ts
```

## Ejecutar y limpiar

```bash
python -I qa/e2e/f23_f24.py
docker rm -f fdtest-auth fdtest-catalog fdtest-orders fdtest-delivery fdtest-redis   # y detener los java / el gateway
```

Variables opcionales: `FD_GATEWAY`, `FD_AUTH_DIRECT`, `FD_INTERNAL_KEY`.
