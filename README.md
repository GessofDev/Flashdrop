# FlashDrop Backend

Backend de FlashDrop, aplicación de delivery. Refactorizado de monolito Node.js a una arquitectura de 4 microservicios Spring Boot (hexagonal) con API Gateway Fastify/TypeScript, desplegado en Coolify sobre un VPS.

## Arquitectura

```
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
       (PostgreSQL 16 — instancias separadas, red interna Coolify)
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
| Orquestación | Coolify sobre VPS |

## Estructura del repo

```
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
│   └── coolify/                    # Archivos de deploy
│       ├── 01-postgres-init.sql    # Init: 4 bases + 4 usuarios (least-privilege)
│       ├── env.shared.template     # Variables compartidas
│       └── DEPLOY.md               # Guía de deploy paso a paso
│
├── references/                     # Material histórico (no usar para desarrollo activo)
│   ├── monolith/                   # El monolito original Node.js + Vercel
│   ├── juniors-history/            # Docs del proceso de los juniors
│   └── migration-plan/             # Plan de migración monolito → microservicios
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

Repartidores y rutas. Dueño de `delivery`, `delivery_routes`.

Endpoints internos:
- `GET /api/internal/delivery-persons?userId={userId}` → perfil del repartidor
- `POST /api/internal/routes` → crea ruta de entrega para una orden
- `PATCH /api/internal/routes/{orderId}/status` → actualiza estado de ruta

### Shared Observability (módulo Gradle)

Librería compartida por `auth-service` (y disponible para el resto cuando lo necesiten). Provee:
- `CorrelationIdFilter`: propaga `X-Request-Id` entre servicios
- `ApiError` y `ErrorCatalog`: formato de error consistente (`{ status, error, message }`)
- `TraceContext`: logging estructurado con contexto de tracing
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

# Crear las 4 bases y los 4 usuarios
psql -h localhost -U postgres -f infra/coolify/01-postgres-init.sql
```

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

```
SPRING_PROFILES_ACTIVE=local
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/auth_db
SPRING_DATASOURCE_USERNAME=auth_svc
SPRING_DATASOURCE_PASSWORD=devpass
INTERNAL_API_KEY=dev-key
AUTH_SERVICE_URL=http://localhost:8081
```

Las plantillas completas de variables están en [`infra/coolify/env.shared.template`](infra/coolify/env.shared.template).

## Deploy en Coolify

Referencia completa: **[`infra/coolify/DEPLOY.md`](infra/coolify/DEPLOY.md)**

Resumen del flujo:

1. **Crear Postgres en Coolify** con el init script `infra/coolify/01-postgres-init.sql` (crea `auth_db`, `catalog_db`, `orders_db`, `delivery_db` con un usuario por servicio).
2. **Crear Redis** para rate-limit del gateway.
3. **Configurar variables compartidas** desde `env.shared.template` (especialmente `INTERNAL_API_KEY` con el mismo valor en los 4 servicios).
4. **Crear una Application por servicio** en Coolify:
   - Source: `GessofDev/Flashdrop`, branch `main`
   - Build Pack: Dockerfile
   - Rutas de Dockerfile:
     - Auth: `services/auth-service/Dockerfile`
     - Catalog: `services/catalog-service/Dockerfile`
     - Orders: `services/orders-service/Dockerfile`
     - Delivery: `services/delivery-service/Dockerfile`
     - Gateway: `gateway/docker/Dockerfile`
5. **Deployar el gateway primero** y configurar las rutas (`gateway/docker/gateway.yaml`) apuntando a los nombres internos de Coolify (`flashdrop-auth:8081`, etc.).

## CI / CD

GitHub Actions corre por servicio dentro de su propio subdirectorio:

- `services/auth-service/.github/workflows/ci.yml` — build y tests del Auth Service

Los demás servicios no tienen CI configurado todavía; la convención es agregar `.github/workflows/ci.yml` dentro de cada `services/<X>/` cuando se quiera CI para ese servicio.

## Observabilidad

- **Health check agregado**: `GET /health` en el gateway consulta el health de los 4 servicios en paralelo. Status 200 = todo OK, 503 = alguno caído.
- **Métricas Prometheus**: `GET /metrics` en el gateway expone `gateway_http_*`, `gateway_rate_limit_*`, `gateway_circuit_breaker_*`, `gateway_jwt_*`, `gateway_cors_*`.
- **Logs estructurados**: JSON vía Pino (gateway) y Logback (servicios Spring Boot).
- **Tracing**: `X-Request-Id` se propaga entre servicios vía `shared-observability`.

## Seguridad

- **API key compartida** entre los 4 servicios para endpoints internos (header `X-Internal-Api-Key`). Valor único generado con `openssl rand -hex 32`, idéntico en los 5 deployments.
- **JWT** para endpoints públicos, emitido por Auth Service, validado por el gateway vía JWKS.
- **Least-privilege en BD**: cada servicio tiene su propio usuario Postgres (`auth_svc`, `catalog_svc`, `orders_svc`, `delivery_svc`) con permisos solo sobre su base.
- **Red interna de Coolify**: el gateway y los servicios se llaman entre sí por nombre de recurso, no exponen puertos públicos innecesariamente.

## Migración desde el monolito

Este repo es el resultado de la refactorización del monolito original Node.js + Express + Vercel + Supabase. El monolito está preservado en `references/monolith/` solo como referencia histórica — no se usa activamente.

El plan completo de la migración (de monolito a microservicios, separación de BDs, contratos de endpoints internos, estrategia de testing) está en [`references/migration-plan/MIGRATION_PLAN.md`](references/migration-plan/MIGRATION_PLAN.md).

## Documentación adicional

- [`infra/coolify/DEPLOY.md`](infra/coolify/DEPLOY.md) — guía operativa de deploy
- [`gateway/README.md`](gateway/README.md) — documentación técnica del gateway
- [`gateway/specs/`](gateway/specs/) — especificaciones del gateway (JWT/JWKS, CORS, circuit breakers, hot-reload, observabilidad)
- [`references/migration-plan/MIGRATION_PLAN.md`](references/migration-plan/MIGRATION_PLAN.md) — plan original de migración
- [`services/auth-service/`](services/auth-service/) — endpoints internos, tests, FEEDBACK/HANDOVER del proceso
