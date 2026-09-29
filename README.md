# FlashDrop Backend

Backend de FlashDrop, aplicación de delivery. Refactorizado de monolito Node.js a una arquitectura de 4 microservicios Spring Boot (hexagonal) con API Gateway Fastify/TypeScript, desplegado sobre [FloCI](infra/floci/INFRASTRUCTURE.md) (emulador local de AWS).

## Arquitectura

```text
                        App Mobile (Flutter)
                                │
                                ▼
                    ┌──────────────────────┐
                    │     API Gateway      │  Fastify + TypeScript
                    │       :3000          │  Reverse proxy + middleware
                    └──────────┬───────────┘
                               │
        ┌──────────┬───────────┼───────────┬──────────┐
        ▼          ▼           ▼           ▼          ▼
   ┌────────┐ ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
   │  Auth  │ │Catalog │  │ Orders │  │Delivery │  │  ...   │
   │  :8081 │ │  :8082 │  │  :8083 │  │  :8084 │  │        │
   └───┬────┘ └───┬────┘  └───┬────┘  └───┬────┘  └────────┘
       ▼          ▼           ▼           ▼
   auth_db    catalog_db   orders_db   delivery_db
       (PostgreSQL 16 — instancias separadas vía RDS FloCI)
```

Cada servicio expone su API pública por el gateway (`/api/<servicio>/*`) y consume datos ajenos únicamente vía endpoints internos del servicio dueño (`/api/internal/*`, protegidos con `X-Internal-Api-Key`).

## Stack

| Componente | Tecnología |
|---|---|
| API Gateway | Fastify 5, TypeScript, Node.js 20, pnpm 9 |
| Microservicios | Spring Boot 3, Java 21, arquitectura hexagonal |
| Persistencia | PostgreSQL 16 (4 bases independientes) |
| Cache / Rate-limit | Redis 7 |
| Build (auth, catalog, delivery) | Gradle (Kotlin DSL) |
| Build (orders) | Maven |
| Containerización | Docker multi-stage (Eclipse Temurin 21) |
| Orquestación / Infra local | FloCI (emulador AWS: RDS, ECS, Secrets Manager) |

## Estructura del repo

```text
.
├── services/                       # Microservicios
│   ├── auth-service/               # Spring Boot + Gradle, puerto 8081
│   ├── catalog-service/            # Spring Boot + Gradle, puerto 8082
│   ├── orders-service/             # Spring Boot + Maven, puerto 8083
│   ├── delivery-service/           # Spring Boot + Gradle, puerto 8084
│   └── shared-observability/       # Módulo Gradle compartido (logging, tracing, error catalog)
│
├── gateway/                        # API Gateway Fastify/TypeScript
│   ├── src/                        # Código fuente
│   ├── docker/                     # Dockerfile y compose
│   ├── specs/                      # Especificaciones técnicas
│   └── docs/                       # Documentación auto-generada
│
├── infra/
│   └── floci/                      # Definiciones de infra local (RDS, ECS, Secrets Manager)
│       ├── INFRASTRUCTURE.md       # Fuente de verdad del entorno (puertos, usuarios, secrets)
│       └── task-definitions/       # ECS task definitions por servicio
│
├── references/                     # Material histórico (no usar para desarrollo activo)
│   ├── monolith/                   # El monolito original Node.js + Vercel
│   ├── juniors-history/            # Docs del proceso de los juniors
│   ├── migration-plan/             # Plan de migración monolito → microservicios
│   └── archived-coolify/           # Artefactos de un setup Coolify previo (archivado, histórico)
│
├── .github/                        # (vacío en main; CI workflows viven en cada servicio)
├── .gitignore
└── README.md
```

## Servicios

### Auth Service (puerto 8081)

Identidad, autenticación y autorización. Dueño de las tablas `users`, `login`, `roles`, `user_has_roles`, `refresh_tokens`.

Endpoints internos expuestos:
- `GET /api/internal/users/{userId}` → `{ id, name, lastName, email, phone }`
- `GET /api/internal/users/{userId}/roles` → `[{ id, name }]`

### Catalog Service (puerto 8082)

Productos, categorías y restaurantes. Dueño de `categories`, `products`, `restaurant`.

Endpoints internos:
- `GET /api/internal/products?ids={id1,id2,...}` → productos con precio y disponibilidad
- `GET /api/internal/restaurants/{restaurantId}` → datos del restaurante
- `GET /api/internal/restaurants?userId={userId}` → restaurante por dueño

### Orders Service (puerto 8083)

Pedidos. Dueño de `orders`, `order_items`, `client`.

Endpoints internos:
- `GET /api/internal/orders?ids={id1,id2,...}` → órdenes con dirección y restaurant_id

### Delivery Service (puerto 8084)

Repartidores y rutas. Dueño de `delivery_db` (PostgreSQL, schema `internal`), accedido via Spring Data JPA + Flyway. Reemplaza el adaptador anterior que leía las tablas `delivery` y `delivery_routes` via Supabase REST.

Endpoints internos:
- `GET /api/internal/delivery-persons?userId={userId}` → perfil del repartidor
- `POST /api/internal/routes` → crea ruta de entrega para una orden
- `PATCH /api/internal/routes/{orderId}/status` → actualiza estado de ruta

Inter-service: `delivery-service` lee datos de `orders-service` via HTTP (`HttpOrderServiceClientAdapter`). El cliente REST envia `X-Internal-Api-Key` en cada request y tiene graceful degradation: si `orders-service` no responde, retorna lista vacia (loggea WARN con trace ID). Perfil `mock-orders` activa un mock para desarrollo local sin necesidad de correr `orders-service`.

### Shared Observability (módulo Gradle)

Librería compartida por `auth-service` (y disponible para el resto cuando lo necesiten). Provee:
- `CorrelationIdFilter`: propaga `X-Request-Id` entre servicios
- `ApiError` y `ErrorCatalog`: formato de error consistente (`{ status, error, message }`)
- `TraceContext`: logging estructurado con contexto de tracing
- `InternalApiKeyFilter`: valida el header `X-Internal-Api-Key` en `/api/internal/*`
- Configuración auto-instalable vía Spring Boot `AutoConfiguration.imports`

## API pública (vía Gateway)

| Path público | Servicio | Notas |
|---|---|---|
| `/api/auth/*` | Auth | Login, registro, refresh token, perfil |
| `/api/catalog/*` | Catalog | Listar productos, categorías, restaurantes |
| `/api/orders/*` | Orders | Crear orden, listar, cambiar estado, reclamar |
| `/api/delivery/*` | Delivery | Rutas, repartidores disponibles |
| `/api/internal/*` | varios | Solo entre servicios (header `X-Internal-Api-Key`) |

## Desarrollo local

### Pre-requisitos

- JDK 21 (Temurin recomendado)
- Node.js 20 + pnpm 9 (para el gateway)
- Docker (para Postgres y Redis en local)
- Gradle 8.x wrapper o Maven 3.x (los proyectos los incluyen vía wrapper)

### Levantar dependencias

```bash
# Levantar Postgres y Redis
docker run -d --name flashdrop-postgres -p 5432:5432 \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=devpass \
  postgres:16-alpine

docker run -d --name flashdrop-redis -p 6379:6379 redis:7-alpine

# Crear las 4 bases y los 4 usuarios (script archivado del setup Coolify
# previo — sigue siendo útil para dev local porque crea los usuarios
# <servicio>_svc que matchean los defaults de los application*.yml).
psql -h localhost -U postgres -f references/archived-coolify/01-postgres-init.sql
```

> **Nota**: para FloCI el script anterior **no aplica** — los usuarios reales
> son `<servicio>_app` y las bases ya están provisionadas vía `aws --endpoint-url
> http://127.0.0.1:4566 rds ...`. Ver `infra/floci/INFRASTRUCTURE.md` §4.

### Correr un servicio

```bash
# Auth
cd services/auth-service
./gradlew bootRun

# Catalog
cd services/catalog-service
./gradlew bootRun

# Orders (Maven)
cd services/orders-service
./mvnw spring-boot:run

# Delivery
cd services/delivery-service
./gradlew bootRun

# Gateway
cd gateway
pnpm install
pnpm dev
```

### Variables de entorno mínimas (ejemplo para Auth)

```bash
SPRING_PROFILES_ACTIVE=local
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/auth_db
SPRING_DATASOURCE_USERNAME=auth_svc
SPRING_DATASOURCE_PASSWORD=devpass
INTERNAL_API_KEY=dev-key
AUTH_SERVICE_URL=http://localhost:8081
```

La plantilla completa de variables compartidas está en
[`references/archived-coolify/env.shared.template`](references/archived-coolify/env.shared.template)
(referencia histórica del setup Coolify previo).

## Deploy en FloCI

Referencia operativa completa: **[`infra/floci/INFRASTRUCTURE.md`](infra/floci/INFRASTRUCTURE.md)**.

FloCI es un emulador local de AWS (RDS, ECS, Secrets Manager) que corre
sobre un VPS y se opera vía AWS CLI con `--endpoint-url http://127.0.0.1:4566`.
Las bases Postgres y los servicios se levantan dentro del contenedor FloCI,
y los servicios se comunican entre sí por la red bridge `floci_default`.

Resumen del flujo:

1. **Verificar estado actual** de bases (`aws rds describe-db-instances`) y
   secrets (`aws secretsmanager list-secrets`). Las 4 bases (`auth_db`,
   `catalog_db`, `orders_db`, `delivery_db`) ya están provisionadas — no hay
   que correr scripts de bootstrap. Ver `INFRASTRUCTURE.md` §4.
2. **Asegurar que existe `flashdrop/internal-api-key`** en Secrets Manager
   (un solo secret compartido por los 5 servicios). Si cada servicio genera
   su propio valor, las llamadas `/api/internal/*` empiezan a devolver 403.
3. **Build de imágenes** localmente en el VPS (`/home/dev/dbuild/`) o en CI.
4. **Levantar los servicios** con ECS task definitions en
   `infra/floci/task-definitions/`. Cada task definition inyecta las env vars
   necesarias desde Secrets Manager.
5. **Smoke test**: `curl http://flashdrop-orders:8083/health` desde un
   container en `floci_default`, o vía el túnel del VPS.

## CI / CD

GitHub Actions corre por servicio dentro de su propio subdirectorio:

- `services/auth-service/.github/workflows/ci.yml` — build y tests del Auth Service
- `services/orders-service/.github/workflows/ci.yml` — build y tests del Orders Service

Los demás servicios no tienen CI configurado todavía; la convención es agregar `.github/workflows/ci.yml` dentro de cada `services/<X>/` cuando se quiera CI para ese servicio. El deploy a FloCI puede dispararse con webhooks de GitHub Actions.

## Observabilidad

- **Health check agregado**: `GET /health` en el gateway consulta el health de los 4 servicios en paralelo. Status 200 = todo OK, 503 = alguno caído.
- **Métricas Prometheus**: `GET /metrics` en el gateway expone `gateway_http_*`, `gateway_rate_limit_*`, `gateway_circuit_breaker_*`, `gateway_jwt_*`, `gateway_cors_*`.
- **Logs estructurados**: JSON vía Pino (gateway) y Logback (servicios Spring Boot).
- **Tracing**: `X-Request-Id` se propaga entre servicios vía `shared-observability`.

## Seguridad

- **API key compartida** entre los 4 servicios para endpoints internos (header `X-Internal-Api-Key`). Valor único guardado en `flashdrop/internal-api-key` de FloCI Secrets Manager, idéntico en los 5 deployments.
- **JWT** para endpoints públicos, emitido por Auth Service, validado por el gateway vía JWKS.
- **Least-privilege en BD**: cada servicio tiene su propio usuario Postgres. En dev local es `<servicio>_svc` (creado por `references/archived-coolify/01-postgres-init.sql`); en FloCI es `<servicio>_app` (provisionado por RDS) — cada usuario tiene permisos solo sobre su base.
- **Red interna de FloCI**: el gateway y los servicios se llaman entre sí por DNS del bridge `floci_default`, no exponen puertos públicos innecesariamente.

## Migración desde el monolito

Este repo es el resultado de la refactorización del monolito original Node.js + Express + Vercel + Supabase. El monolito está preservado en `references/monolith/` solo como referencia histórica — no se usa activamente.

El plan completo de la migración (de monolito a microservicios, separación de BDs, contratos de endpoints internos, estrategia de testing) está en [`references/migration-plan/MIGRATION_PLAN.md`](references/migration-plan/MIGRATION_PLAN.md).

## Documentación adicional

- [`infra/floci/INFRASTRUCTURE.md`](infra/floci/INFRASTRUCTURE.md) — fuente de verdad del entorno FloCI (puertos, usuarios, secrets, ECS tasks)
- [`gateway/README.md`](gateway/README.md) — documentación técnica del gateway
- [`gateway/specs/`](gateway/specs/) — especificaciones del gateway (JWT/JWKS, CORS, circuit breakers, hot-reload, observabilidad)
- [`references/migration-plan/MIGRATION_PLAN.md`](references/migration-plan/MIGRATION_PLAN.md) — plan original de migración
- [`references/archived-coolify/`](references/archived-coolify/) — artefactos históricos del setup Coolify previo (no se usan activamente)
- [`services/auth-service/`](services/auth-service/) — endpoints internos, tests, FEEDBACK/HANDOVER del proceso
