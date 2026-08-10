# Plan B — Stack Docker Compose completo (gateway + 4 microservicios)

> Plan para armar el stack completo de FlashDrop con Docker Compose, fuera de WSL (en Windows nativo donde Docker funciona bien). El stack une el API Gateway (TypeScript) con los 4 microservicios Spring Boot (auth, catalog, orders, delivery) y Redis, todos en una sola red.

---

## 0. Resumen ejecutivo

- **Servicios en el stack**: `redis-cache`, `auth-service` (8081), `catalog-service` (8082), `orders-service` (8083), `delivery-service` (8084), `gateway` (3000)
- **Red interna**: `stack` (bridge)
- **Persistencia**: volumen `redis-data` (Redis). Los servicios Spring Boot son stateless.
- **Imagen del gateway**: ya existe (`gateway/docker/Dockerfile`, multi-stage Node 20 alpine).
- **Imágenes de los 4 servicios**: hay Dockerfiles canónicos en `services/{auth,catalog,orders,delivery}-service/Dockerfile` pero **NO reflejan el código migrado a REST** — `services/{auth,catalog,orders}/` siguen siendo código JDBC viejo. Por eso apuntamos el compose a `juniors/` para los 3 juniors migrados, y a `services/delivery-service/` solo para delivery (que sí reescribí a REST).

---

## 1. Pre-requisitos

- Docker Desktop funcionando (probado fuera de WSL en Windows)
- Credenciales Supabase (URL + SERVICE_ROLE_KEY)
- Permisos de lectura/escritura en el árbol de `flashdrop_backend/`

---

## 2. Estructura final esperada

```
flashdrop_backend/
├── gateway/
│   ├── docker/
│   │   ├── docker-compose.stack.yml   ← NUEVO
│   │   ├── gateway.yaml               ← NUEVO (o reemplazar existente)
│   │   └── .env                       ← NUEVO (con tus secretos)
│   └── (resto del proyecto gateway intacto)
├── juniors/
│   ├── junior-1-auth/auth-service/
│   │   ├── Dockerfile                 ← NUEVO (sobrescribir)
│   │   └── .dockerignore              ← NUEVO
│   ├── junior-2-orders/orders-service/
│   │   ├── Dockerfile                 ← NUEVO (sobrescribir)
│   │   └── .dockerignore              ← NUEVO
│   └── junior-3-catalog/
│       ├── Dockerfile                 ← NUEVO (sobrescribir)
│       └── .dockerignore              ← NUEVO
└── services/
    └── delivery-service/
        └── Dockerfile                 ← CORREGIR (el .dockerignore ya está OK)
```

---

## 3. Archivos a crear (contenido completo)

### 3.1 `gateway/docker/docker-compose.stack.yml`

```yaml
version: '3.9'

networks:
  stack:
    driver: bridge

volumes:
  redis-data:

services:
  redis-cache:
    image: redis:7-alpine
    container_name: stack-redis
    networks: [stack]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5
    volumes:
      - redis-data:/data

  auth-service:
    build:
      context: ../../juniors/junior-1-auth/auth-service
      dockerfile: Dockerfile
    container_name: stack-auth
    environment:
      - SPRING_PROFILES_ACTIVE=supabase
      - SERVER_PORT=8081
      - SUPABASE_URL=${SUPABASE_URL}
      - SUPABASE_SERVICE_ROLE_KEY=${SUPABASE_SERVICE_ROLE_KEY}
      - JWT_ALLOW_EPHEMERAL=true
    networks: [stack]
    expose: ["8081"]
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8081/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 40s

  catalog-service:
    build:
      context: ../../juniors/junior-3-catalog
      dockerfile: Dockerfile
    container_name: stack-catalog
    environment:
      - SPRING_PROFILES_ACTIVE=supabase
      - SERVER_PORT=8082
      - SUPABASE_URL=${SUPABASE_URL}
      - SUPABASE_SERVICE_ROLE_KEY=${SUPABASE_SERVICE_ROLE_KEY}
    networks: [stack]
    expose: ["8082"]
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8082/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 40s

  orders-service:
    build:
      context: ../../juniors/junior-2-orders/orders-service
      dockerfile: Dockerfile
    container_name: stack-orders
    environment:
      - SPRING_PROFILES_ACTIVE=supabase
      - SERVER_PORT=8083
      - SUPABASE_URL=${SUPABASE_URL}
      - SUPABASE_SERVICE_ROLE_KEY=${SUPABASE_SERVICE_ROLE_KEY}
      - AUTH_SERVICE_URL=http://auth-service:8081
      - DELIVERY_FEE=2500
    networks: [stack]
    depends_on:
      auth-service:
        condition: service_healthy
    expose: ["8083"]
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8083/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 40s

  delivery-service:
    build:
      context: ../../services/delivery-service
      dockerfile: Dockerfile
    container_name: stack-delivery
    environment:
      - SPRING_PROFILES_ACTIVE=supabase
      - SERVER_PORT=8084
      - SUPABASE_URL=${SUPABASE_URL}
      - SUPABASE_SERVICE_ROLE_KEY=${SUPABASE_SERVICE_ROLE_KEY}
    networks: [stack]
    expose: ["8084"]
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8084/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 40s

  gateway:
    build:
      context: ..
      dockerfile: docker/Dockerfile
    container_name: stack-gateway
    environment:
      - NODE_ENV=production
      - CONFIG_PATH=/app/config/gateway.yaml
      - REDIS_URL=redis://redis-cache:6379
      - LOG_LEVEL=info
    volumes:
      - ./gateway.yaml:/app/config/gateway.yaml:ro
    networks: [stack]
    depends_on:
      redis-cache:
        condition: service_healthy
      auth-service:
        condition: service_healthy
      catalog-service:
        condition: service_healthy
      orders-service:
        condition: service_healthy
      delivery-service:
        condition: service_healthy
    ports:
      - "3000:3000"
```

### 3.2 `gateway/docker/gateway.yaml`

```yaml
server:
  port: 3000
  host: 0.0.0.0

redis:
  url: redis://redis-cache:6379
  onFailure: open

logging:
  level: info

metrics:
  enabled: true
  path: /metrics

# Health aggregation — /actuator/health es el único path que los 4 servicios comparten
# auth, catalog y orders lo exponen por Spring Actuator; delivery también.
health:
  enabled: true
  path: /health
  backendPath: /actuator/health
  timeoutMs: 2000

# CORS permisivo en dev — ajustar orígenes en prod
cors:
  enabled: true
  origins: ["*"]
  methods: [GET, POST, PUT, DELETE, OPTIONS]
  allowedHeaders: [Content-Type, Authorization]
  maxAge: 86400

routes:
  # === AUTH ===
  - prefix: /auth
    target: http://auth-service:8081
    stripPrefix: false
    backendName: auth-service

  # === CATALOG ===
  - prefix: /catalog
    target: http://catalog-service:8082
    stripPrefix: false
    backendName: catalog-service

  # === ORDERS (2 prefijos distintos, mismo target) ===
  - prefix: /api/orders
    target: http://orders-service:8083
    stripPrefix: false
    backendName: orders-service

  - prefix: /api/delivery
    target: http://orders-service:8083
    stripPrefix: false
    backendName: orders-service

  # === DELIVERY ===
  - prefix: /delivery
    target: http://delivery-service:8084
    stripPrefix: false
    backendName: delivery-service
```

### 3.3 `gateway/docker/.env`

```env
SUPABASE_URL=http://supabasekong-wymwq8rktid7ov678oe4va90.76.13.169.150.sslip.io
SUPABASE_SERVICE_ROLE_KEY=<tu-service-role-key-real-aqui>
```

> ⚠️ **NUNCA commitear este archivo** — agregá `gateway/docker/.env` al `.gitignore` si no está.

### 3.4 `juniors/junior-1-auth/auth-service/Dockerfile`

```dockerfile
# syntax=docker/dockerfile:1.6
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon
COPY src ./src
RUN ./gradlew :auth-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/auth-service/build/libs/*.jar app.jar
EXPOSE 8081
ENV SERVER_PORT=8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

### 3.5 `juniors/junior-1-auth/auth-service/.dockerignore`

```
.gradle
build
*.class
*.log
.DS_Store
Thumbs.db
.idea
*.iml
.vscode
!gradle/wrapper/gradle-wrapper.jar
```

### 3.6 `juniors/junior-3-catalog/Dockerfile`

```dockerfile
# syntax=docker/dockerfile:1.6
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon
COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8082
ENV SERVER_PORT=8082
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

### 3.7 `juniors/junior-3-catalog/.dockerignore`

```
.gradle
build
*.class
*.log
.DS_Store
Thumbs.db
.idea
*.iml
.vscode
bin/
!gradle/wrapper/gradle-wrapper.jar
```

### 3.8 `juniors/junior-2-orders/orders-service/Dockerfile`

```dockerfile
# syntax=docker/dockerfile:1.6
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml ./
COPY mvnw .mvn ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/orders-service-*.jar app.jar
EXPOSE 8083
ENV SERVER_PORT=8083
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

### 3.9 `juniors/junior-2-orders/orders-service/.dockerignore`

```
target
.git
.gitignore
*.log
.DS_Store
Thumbs.db
.idea
*.iml
.vscode
bin/
```

### 3.10 `services/delivery-service/Dockerfile` (sobrescribir el existente)

```dockerfile
# syntax=docker/dockerfile:1.6
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon
COPY src ./src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8084
ENV SERVER_PORT=8084
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

> El `.dockerignore` de `services/delivery-service/` ya está OK — dejá el que existe.

---

## 4. Pasos para levantar el stack

```bash
# 1. Asegurate de tener tu SUPABASE_SERVICE_ROLE_KEY real
#    Reemplazá <tu-service-role-key-real-aqui> en gateway/docker/.env

# 2. Verificá que el árbol de archivos esté como en la sección 2
cd flashdrop_backend/gateway/docker
ls docker-compose.stack.yml gateway.yaml .env

# 3. (Opcional) Pre-descargá las imágenes base para reducir el tiempo del primer build
docker pull redis:7-alpine
docker pull eclipse-temurin:21-jre-alpine
docker pull eclipse-temurin:21-jdk-alpine
docker pull maven:3.9-eclipse-temurin-21
docker pull node:20-alpine

# 4. Build + levantar (la primera vez tarda — descarga imágenes y compila 4 Spring Boot + TypeScript)
docker compose -f docker-compose.stack.yml --env-file .env up --build

# 5. En otra terminal, seguí los logs en vivo
docker compose -f docker-compose.stack.yml logs -f

# 6. Para detener todo
#    Ctrl+C si lo lanzaste en foreground
#    O desde otra terminal: docker compose -f docker-compose.stack.yml down
```

---

## 5. Verificación end-to-end (una vez que todo esté UP)

### 5.1 Health agregado del gateway

```bash
curl -s http://localhost:3000/health | jq
# Esperado: {"status":"ok","services":{"auth-service":"ok","catalog-service":"ok","orders-service":"ok","delivery-service":"ok"}}
# Si algún servicio está "degraded" o "down", revisar logs de ese contenedor
```

### 5.2 Login en auth (vía gateway, sin auth previa)

```bash
curl -s -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"cliente@demo.cl","password":"123456"}' | jq
# Esperado: {"userId":1,"name":"Cliente","email":"cliente@demo.cl","roles":["Cliente"],"accessToken":"eyJ...","refreshToken":"...","expiresInSeconds":900}
```

### 5.3 Catalog público (vía gateway, sin auth)

```bash
curl -s http://localhost:3000/catalog/products | jq '.data | length'
# Esperado: 14 (o el número de productos en Supabase)
```

### 5.4 Orders CON Bearer (vía gateway)

```bash
TOKEN=$(curl -s -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"cliente@demo.cl","password":"123456"}' \
  | jq -r .accessToken)

curl -s http://localhost:3000/api/orders -H "Authorization: Bearer ${TOKEN}" | jq '.data | length'
# Esperado: 14 (o el número de orders en Supabase)

curl -s http://localhost:3000/api/orders -H "Authorization: Bearer invalid.token.here"
# Esperado: HTTP 401
```

### 5.5 Delivery (vía gateway, sin auth)

```bash
curl -s "http://localhost:3000/delivery/routes?deliveryPersonId=1" | jq '. | length'
# Esperado: 10 (o el número de delivery_routes en Supabase)
```

### 5.6 Validaciones de error (opcionales pero recomendables)

```bash
# Sin token en endpoint protegido → 401
curl -s -w "\nHTTP=%{http_code}\n" http://localhost:3000/api/orders

# Token Bearer inválido → 401
curl -s -w "\nHTTP=%{http_code}\n" -H "Authorization: Bearer garbage" http://localhost:3000/api/orders

# Origin del gateway (no CORS error)
curl -s -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: POST" \
  -X OPTIONS http://localhost:3000/auth/login -i | head -20
```

---

## 6. Gotchas y troubleshooting

### Build

- **`gradlew: not found` durante el build**
  Causa: el `.dockerignore` excluye `gradlew` o el `Dockerfile` no lo copia. Verificá que ambos archivos estén alineados (ambos vienen juntos en este plan).

- **`pom.xml: not found` durante build de orders**
  Causa: el contexto del build está mal. El compose usa `context: ../../juniors/junior-2-orders/orders-service`. Verificá que `pom.xml` esté en ese path.

- **Build tarda más de 10 minutos la primera vez**
  Normal — descarga imágenes base (~500 MB total) y compila 4 Spring Boot. Builds posteriores son rápidos por cache.

### Runtime

- **Service arranca pero `/actuator/health` da 404**
  El servicio no tiene `spring-boot-starter-actuator` en su `pom.xml`/`build.gradle.kts`. Agregalo y rebuildá.

- **`Could not resolve placeholder 'SUPABASE_URL'`**
  El `.env` no tiene `SUPABASE_URL` o no se pasó con `--env-file`. Verificá `cat gateway/docker/.env`.

- **Gateway arranca pero `/health` da error 503 "down"**
  Algún servicio no responde a `/actuator/health`. Probá directo: `curl http://localhost:8081/actuator/health`. Si falla, el healthcheck del compose esperará 10 retries × 15s = 150s antes de marcar unhealthy.

- **Orders no valida el Bearer (devuelve 401 siempre)**
  Verificá que `AUTH_SERVICE_URL=http://auth-service:8081` (NO `localhost`) — dentro del compose se resuelven por nombre de servicio.

- **Catalog devuelve 401 cuando era público**
  Catalog NO debería tener JWT. Si lo agregó alguien, hay que quitarlo. (El código verificado de junior-3 no tiene JWT.)

- **`docker compose` warnings sobre `version` obsolete**
  El `version: '3.9'` en el YAML es obsoleto en Docker Compose v2+. Es solo un warning, se puede ignorar. Si querés, borralo.

### Docker Desktop (Windows)

- **WSL2 + Docker Desktop funciona mejor con archivos en `/home/<user>/`** que en `/mnt/c/`. Si tenés problemas de I/O, mové el árbol `flashdrop_backend/` adentro de WSL antes de buildear.
- **No uses rutas con espacios en Windows** para los `context:` del compose — ya lo tenés así con "ASUS TUF F15 i5", pero Docker Desktop lo maneja bien.

---

## 7. Después de que funcione

Cuando todo esté verde end-to-end:

1. **Mergear `juniors/junior-2-orders` a su repo principal**: el PR #1 ya está mergeado en su repo (`felipepousacerda/FlashDrop`). No queda trabajo pendiente para orders.
2. **Agregar el stack compose al repo del monolito** (o donde tenga sentido) para CI/CD.
3. **Resolver el TODO de JWT keys** en auth-service: las claves RSA efímeras hacen que el gateway NO pueda validar tokens emitidos por otros gateways/instances. Cuando se resuelva, orders seguirá funcionando sin cambios.
4. **Unificar los 3 juniors en `services/`**: hoy el código migrado a REST vive en `juniors/junior-{1,2,3}-*`. Para la versión "production" conviene mover a `services/{auth,catalog,orders}-service/` y borrar los juniors. Es un refactor grande pero de un solo movimiento.

---

## 8. Archivos que NO hay que tocar

- `gateway/src/**/*.ts` — código del gateway intacto (config va por YAML)
- `gateway/docker/Dockerfile` — Dockerfile del gateway ya está OK (multi-stage Node 20 alpine)
- `gateway/package.json` — dependencias OK
- Cualquier `services/*/src/**` — el código fuente de los servicios está verificado funcionando
- `juniors/junior-{1,2,3}-*/auth-service|orders-service|src/**` — código fuente intacto

---

## 9. Quick reference de URLs

| URL | Qué hace |
|---|---|
| `http://localhost:3000/health` | Health aggregation del gateway |
| `http://localhost:3000/metrics` | Métricas Prometheus del gateway |
| `http://localhost:3000/auth/login` | POST login (no auth) |
| `http://localhost:3000/auth/register` | POST register (no auth) |
| `http://localhost:3000/auth/validate` | GET validar Bearer token |
| `http://localhost:3000/catalog/products` | GET productos (no auth) |
| `http://localhost:3000/catalog/categories` | GET categorías (no auth) |
| `http://localhost:3000/catalog/restaurants` | GET restaurantes (no auth) |
| `http://localhost:3000/api/orders` | GET/POST orders (Bearer) |
| `http://localhost:3000/api/orders/{uuid}` | GET/PUT orders/{uuid} (Bearer) |
| `http://localhost:3000/api/delivery/routes` | GET delivery routes (Bearer) |
| `http://localhost:3000/api/delivery/claim` | POST claim delivery (Bearer) |
| `http://localhost:3000/delivery/routes` | GET delivery routes del servicio delivery (no auth) |
| `http://localhost:3000/delivery/claim` | POST claim del servicio delivery (no auth) |

---

**Última actualización**: 2026-07-28. Plan armado después de verificar end-to-end los 4 servicios contra Supabase real + smoke test del gateway con auth-service + orders-service en paralelo.