# Feature: ratings-system — calificaciones de clientes, repartidores y tiendas

> Estado: **planificado**. Implementación no iniciada.
> Base: `main` @ `de20e53` (PR #101, modelo de pagos/refunds de orders).
> Rama de trabajo: `feat/ratings-system` (creada desde `origin/main`).

## Specs

### S1 (verbatim, usuario)

> quiero agregar un sistema de calificaciones, tanto para clientes, repartidores y tiendas.

### S2 (verbatim, usuario)

> ayudame a planeartlo

### S3-S9 (decididas con el usuario)

> Reglas confirmadas en la sesión de planificación. Si el usuario las cambia, se
> reescribe la S# y se reabre la tarea afectada.

- **S3** (D2) Una calificación se ancla a una **orden** y solo puede existir si la
  orden está en `ENTREGADO`.
- **S4** (D2) Matriz completa de **6 relaciones**: cliente → repartidor, cliente →
  tienda, repartidor → tienda, repartidor → cliente, tienda → repartidor, tienda →
  cliente.
- **S5** (D3) Escala de **1 a 5 estrellas** entera y obligatoria; **comentario
  opcional de hasta 500 caracteres**.
- **S6** Una sola calificación por (orden, autor, destino). El reintento devuelve
  conflicto, no una fila nueva.
- **S7** El **id del destino lo deriva el servidor** desde la orden. El cliente
  nunca envía el id del actor que califica.
- **S8** (D4) **Promedio y comentarios son públicos**: el promedio se muestra en el
  marketplace y en el perfil del repartidor; los comentarios se renderizan como
  texto plano.
- **S9** (D1) El dato vive en un **servicio nuevo `reviews-service`**, con su propia
  base de datos, su ruta de gateway, su CI/CD y su task-definition en FloCI.

## Design

### 1. Vista de arquitectura

```
apps/web (React/Vite)
   │  POST /api/reviews                       GET /api/reviews/{tipo}/{id}
   │  GET  /api/reviews/pending/{orderId}     GET /api/users/me/reviews
   ▼
gateway (Fastify)  ── /api/reviews ──►  http://reviews-service:8085
                                            │
                                  ┌─────────┴──────────┐
                                  │  reviews-service   │  owns reviews_db
                                  └─────────┬──────────┘
                     GET /api/internal/orders/{id}   (X-Internal-Api-Key)
                                            ▼
                                    orders-service
                                  (orden + participantes)

   GET /api/internal/reviews/aggregates?targetType=&ids=   (X-Internal-Api-Key)
        ▲                                  ▲
        │ promedio de TIENDA               │ promedio de REPARTIDOR
   catalog-service                    delivery-service
   (reviews_db no: cada servicio        (idem: cachea en su propia DB)
    cachea en su propia DB)
```

`reviews-service` es el **único dueño** del dato de calificaciones. `catalog-service`
y `delivery-service` no leen su DB: lo consultan por endpoint interno por lotes y
cachean el resultado en su propia base (AGENTS.md §2).

### 2. Por qué un servicio nuevo (D1)

El usuario eligió el servicio independiente. Lo que cambia frente a la alternativa
de meterlo en `orders-service`:

| | En orders-service | reviews-service (elegido) |
|---|---|---|
| DB | `orders_db` | `reviews_db` (nueva, `reviews_app` user) |
| Ruta gateway | ninguna (ya ruteado) | `/api/reviews` → `http://reviews-service:8085` |
| CI/CD | reutilizado | `reviews-service-ci.yml` + `reviews-service-cd.yml` |
| FloCI | nada | `task-definitions/reviews-service.dev.json` |
| Compose | nada | servicio nuevo en `gateway/docker/docker-compose.stack.yml` |
| Consecuencia | acopla el dato al ciclo de vida de la orden | **orders-service necesita un endpoint interno nuevo** para que reviews valide la orden y sus participantes |

Ventajas que se siguen de D1: moderación, analítica yevolutionable sin tocar
orders; el dominio de "calificación" queda aislado; el agregado público se puede
cachear y exponer sin filtrar datos de órdenes.

Costo explícito que asumimos: un servicio más que operar (deploy, secret de API
key, grants de DB, health checks, alarms).

### 3. Modelo de datos (`reviews-service`, Flyway `V1__reviews.sql`)

```sql
CREATE TABLE reviews (
  id              BIGSERIAL PRIMARY KEY,
  order_id        BIGINT      NOT NULL,          -- id numérico público de la orden
  author_user_id  BIGINT      NOT NULL,          -- auth.users.id
  target_type     VARCHAR(16) NOT NULL,          -- TIENDA | REPARTIDOR | CLIENTE
  target_id       BIGINT      NOT NULL,          -- catalog.restaurant.id | auth.users.id
  rating          SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
  comment         VARCHAR(500),
  status          VARCHAR(16) NOT NULL DEFAULT 'PUBLICADO',  -- PUBLICADO | OCULTO
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uq_review_participant UNIQUE (order_id, author_user_id, target_type)
);
CREATE INDEX idx_reviews_target ON reviews (target_type, target_id);
CREATE INDEX idx_reviews_author ON reviews (author_user_id, created_at DESC);
```

Decisiones:

- **Espacio de ids numérico**: `IdConverter` (orders) es una biyección
  `UUID ↔ Long` (`new UUID(0, id)`), así que el id numérico público es el espacio
  común entre auth (`Long`), catalog (`Long`) y orders (UUID interno ↔ Long
  público). `reviews-service` trabaja **solo en `Long`**: no depende del UUID de
  nadie y no necesita un IdConverter propio.
- `target_id` es polimórfico a propósito: no hay FK posible cross-servicio.
  `TIENDA` → `catalog.restaurant.id`; `REPARTIDOR` y `CLIENTE` → `auth.users.id`
  (la identidad que el cliente reconoce, no el id de `delivery_person`).
- La unicidad `(order_id, author_user_id, target_type)` es la **garantía de
  idempotencia** ante reintentos de red.
- `status` permite moderar sin borrar (T16, fuera de v1).
- Migración **aditiva**, en una base nueva: no toca datos existentes.

### 4. Matriz de autorización (S4)

| Autor \ Destino | TIENDA | REPARTIDOR | CLIENTE |
|---|---|---|---|
| **CLIENTE** | ✅ | ✅ | ❌ |
| **REPARTIDOR** | ✅ | ❌ | ✅ |
| **TIENDA** | ❌ | ✅ | ✅ |

Reglas:

- El autor debe tener el rol del claim `roles` del JWT (`Cliente` /
  `Repartidor` / `Restaurante`; **multi-rol**, nunca tomar el primero) **y** ser
  participante de esa orden.
- Solo `OrderStatus.ENTREGADO` habilita calificar.
- No hay auto-calificación.
- `target_id` se **deriva de la orden** (S7): `reviews-service` pide la orden al
  endpoint interno de orders y calcula el id del destino a partir de
  `restaurantId` / `deliveryInfo.userId` / `clientId`.

### 5. API

**Público (`reviews-service`, JWT)**

| Método | Path | Qué hace |
|---|---|---|
| `POST` | `/api/reviews` | Crea la calificación. Body: `{ orderId, targetType, rating, comment? }`. Idempotente por la UNIQUE. `409` si ya existe; `422` si la orden no está `ENTREGADO` o la relación no está permitida; `403` si el rol no alcanza; `404` si la orden no existe. |
| `GET` | `/api/reviews/pending/{orderId}` | Qué calificaciones puedo hacer todavía en esa orden + las que ya hice. Alimenta el formulario. |
| `GET` | `/api/reviews/{targetType}/{targetId}` | Lista paginada + agregado (`average`, `count`, `distribution`) de un destino. |
| `GET` | `/api/users/me/reviews` | Historial de las calificaciones que escribí. |

**Interno (`X-Internal-Api-Key`)**

| Método | Path | Qué hace |
|---|---|---|
| `GET` | `/api/internal/reviews/aggregates?targetType=&ids=1,2,3` | Agregados por lote. Lo consumen catalog y delivery. |

**Nuevo en `orders-service` (necesario por D1)**

| Método | Path | Qué hace |
|---|---|---|
| `GET` | `/api/internal/orders/{id}` | Resumen de la orden para un servicio interno: `status`, `clientId`, `restaurantId`, `deliveryInfo.userId`, todo en **id numérico público**. Lo consume `reviews-service` para validar participación y derivar el destino. No expone datos de contacto ni detalles de la orden. |

Contrato de errores: mismo formato `ApiError` del repo, con código HTTP estable
por caso (400 id inválido, 403 rol/relación, 404 orden o destino, 409 duplicado,
422 fuera de ventana).

### 6. Agregados y cache en los servicios consumidores

- **catalog-service**: tabla `restaurant_rating_stats(restaurant_id PK,
  average, count, updated_at)` + pull por lotes contra el endpoint interno cada
  ~5 min (o on-demand con TTL). La ficha del restaurante expone `ratingAverage`
  y `ratingCount`.
- **delivery-service**: patrón equivalente con
  `delivery_person_rating_stats`; el perfil del repartidor expone su promedio.
- Si el pull falla, el consumidor **sirve el último valor cacheado** y lo
  registra: la disponibilidad del marketplace no depende del endpoint de
  agregados.

### 7. Seguridad y anti-abuso

- Autorización por rol + participación, probada en `@WebMvcTest` con JWT firmado
  por rol (patrón de `delivery-service`: `RoleAuthoritiesExtractor`).
- `target_id` nunca viene del cliente (S7).
- `comment` ≤ 500 caracteres, sanitizado a la salida y renderizado como texto
  plano (nunca HTML).
- Rate limit por path en el gateway (`overrides:` de `gateway/docker/gateway.yaml`):
  p. ej. 10 escrituras de calificación por usuario/minuto.
- Anti-spam v1: unicidad por orden + rate limit. Detección de spam coordinado y
  bloqueo de cuentas quedan fuera de alcance.
- `X-Internal-Api-Key` nunca se loguea; los logs llevan `orderId`,
  `targetType`, `targetId`, `authorUserId`.

### 8. Checklist de creación del servicio (repo)

- `services/settings.gradle.kts` → `include("reviews-service")`.
- `services/reviews-service/build.gradle.kts` (mismo stack que `delivery-service`:
  Spring Boot 3, JPA, Flyway, Security, Testcontainers 1.21.4, ArchUnit,
  `project(":shared-observability")`).
- `Dockerfile` propio (plantilla de `delivery-service`).
- `src/main/resources/application.yml` + `application-<profile>.yml`; health en
  `/health`; perfil por defecto de la DB propia.
- `openapi.yaml` (los servicios lo mantienen).
- `src/main/resources/db/migration/V1__reviews.sql`.
- `.github/workflows/reviews-service-ci.yml` y `reviews-service-cd.yml` (raíz
  `.github/workflows/`, patrón de `delivery-service-ci.yml`).
- `infra/floci/task-definitions/reviews-service.dev.json` (puerto **8085**).
- `infra/floci/INFRASTRUCTURE.md` §4 → crear el user `reviews_app` con grants
  sobre `reviews_db` (mismo patrón que el resto).
- `gateway/docker/docker-compose.stack.yml` → servicio `reviews-service` en la
  red `floci_default`.
- `gateway/docker/gateway.yaml` → ruta `- prefix: /api/reviews`,
  `target: http://reviews-service:8085`, `stripPrefix: false`.
- Secret compartido: reutiliza `flashdrop/internal-api-key` (único para los 5
  servicios, AGENTS.md §7).
- `README.md` / `AGENTS.md` §3: registrar el servicio nuevo y su build system.

### 9. Testing (convención del repo: unitarios sin Docker, IT con Testcontainers)

- `RatingPolicyTest` (dominio): matriz §4 completa con sus rechazos
  (auto-calificación, rol válido pero no participante, orden no entregada),
  límites 1 y 5, rating fuera de rango.
- `CreateReviewTest` (caso de uso): unicidad → `409`; reintento idempotente;
  derivación de `target_id` desde la orden; timeout de la llamada interna.
- `ReviewsControllerTest` (`@WebMvcTest`): 4 escenarios con JWT real por rol.
- `ReviewsRepositoryIT` (Testcontainers Postgres): `uq_review_participant`,
  `CHECK rating`, índices, lectura por `target_type/target_id`.
- `ReviewsMigrationIT`: V1 aplica sobre schema nuevo y deja el schema esperado.
- `InternalOrdersControllerTest` (en orders): el nuevo endpoint interno devuelve
  ids numéricos y **no** expone datos de contacto.
- **Chequeo diferencial**: `GET /api/orders/**` y el resto de los endpoints de
  orders responden igual antes y después del cambio (stdout, stderr y exit code
  contra la base).

### 10. Rollout

1. Esqueleto del servicio + migración + health + CI verde (sin endpoints de
   negocio): el servicio existe y es monitoreable.
2. Endpoint interno de orders + feature flag `reviews.enabled=false`.
3. API de calificaciones + seguridad + tests.
4. Pull de agregados en catalog/delivery.
5. Web: formulario y promedios.
6. Flag en `true` en dev, validación manual y decisión conjunta del equipo para
   producción (el PR lo mergea el reviewer, no el autor).

### 11. Observabilidad

- Logs estructurados con `orderId`, `targetType`, `targetId`, `authorUserId`.
- Métricas: `reviews.created`, `reviews.rejected{reason}`,
  `reviews.aggregates.pull_duration`, `reviews.aggregates.pull_failures`,
  `reviews.orders_lookup_failures` (degradación hacia orders).

## Tasks

| # | Task | Specs | Ruta | Commit |
|---|---|---|---|---|
| **T0** | Spike read-only: confirmar el endpoint interno de orden a agregar, el espacio de ids público de cada participante, el patrón de `task-definition`/compose/CI de `delivery-service` y el secret de API key | S1 | inline | (sin commit) |
| **T1** | OpenSpec change `ratings-system` (`proposal.md`, `design.md`, `spec.md`, `tasks.md`) + `docs/plans/2026-XX-XX-ratings-design.md` | S1, S2, S9 | inline | `docs(planning): add ratings system openspec change` |
| **T2** | Esqueleto de `reviews-service`: módulo Gradle + `settings.gradle.kts` + `build.gradle.kts` + `Dockerfile` + `application.yml` + `/health` + `openapi.yaml` | S9 | inline | `feat(reviews): scaffold reviews service` |
| **T3** | Persistencia: `V1__reviews.sql` + entity + repositorio JPA + Testcontainers IT de constraints | S6 | inline | `feat(reviews): add reviews table and jpa repository` |
| **T4** | `orders-service`: `GET /api/internal/orders/{id}` (resumen con participantes en id numérico) + test | S3, S7 | inline | `feat(orders): expose internal order summary endpoint` |
| **T5** | Dominio en `reviews-service`: `Rating`, `RatingTarget`, `RatingPolicy` (matriz §4) + tests unitarios | S3, S4, S5 | inline | `feat(reviews): add rating domain model and policy` |
| **T6** | Puertos: `ReviewRepositoryPort`, `OrderLookupPort` (cliente interno de orders) y sus adapters | S3, S7 | inline | `feat(reviews): add review ports and order lookup client` |
| **T7** | Caso de uso `CreateReview`: derivar `target_id` desde la orden, idempotencia, 409/422/403 | S6, S7 | inline | `feat(reviews): create review use case` |
| **T8** | API pública: `POST /api/reviews`, `GET /api/reviews/pending/{orderId}`, `GET /api/reviews/{tipo}/{id}`, `GET /api/users/me/reviews` | S5, S8 | inline | `feat(reviews): expose reviews endpoints` |
| **T9** | API interna: `GET /api/internal/reviews/aggregates` (lotes) | S8 | inline | `feat(reviews): expose internal rating aggregates` |
| **T10** | Seguridad: claim `roles` → authorities, validación de participación, sanitización y longitud del comentario | S4, S5 | inline | `feat(reviews): enforce review authorization` |
| **T11** | Tests de API (`@WebMvcTest` por rol) + IT de la migración + **chequeo diferencial** de `GET /api/orders/**` | S3-S8 | inline | `test(reviews): cover rating api, policy and migration` |
| **T12** | CI/CD: `reviews-service-ci.yml`, `reviews-service-cd.yml`, `task-definitions/reviews-service.dev.json`, compose stack, grants en `INFRASTRUCTURE.md` §4, README/AGENTS | S9 | inline | `ci(reviews): add pipeline, floci task definition and db grants` |
| **T13** | Gateway: ruta `/api/reviews` → `reviews-service:8085` + rate limit de escritura | S5 | inline | `feat(gateway): route and rate limit reviews endpoints` |
| **T14** | catalog-service: `restaurant_rating_stats` + pull interno + `ratingAverage`/`ratingCount` en la ficha del restaurante | S8 | inline | `feat(catalog): expose restaurant rating average` |
| **T15** | delivery-service: `delivery_person_rating_stats` + pull interno + promedio en el perfil del repartidor | S8 | inline | `feat(delivery): expose courier rating average` |
| **T16** | Web (`apps/web`): formulario de calificación post-entrega + promedios en marketplace/store/courier | S3, S5, S8 | inline | `feat(web): add rating form and rating averages` |
| **T17** | Moderación: endpoint de denuncia, `status=OCULTO`, panel admin | — | **fuera de v1** | — |
| **T18** | Cierre: evidencia, informe de implementación, actualización de este documento y de `openspec/` | S1, S2 | inline | `docs(reviews): record ratings implementation evidence` |

## Work units (cada uno cierra con commit + tests + docs)

- **WU-1** — T0 + T1 (spike y especificación).
- **WU-2** — T2 + T3 (esqueleto del servicio y persistencia) — incluye el
  checklist §8, que es lo más fácil de dejar a medias.
- **WU-3** — T4 + T5 + T6 (contrato con orders, dominio, puertos).
- **WU-4** — T7 + T8 + T9 + T10 (caso de uso, API, seguridad) — *contiene los
  ítems de riesgo 2 (permisos) y 3 (contratos públicos)*.
- **WU-5** — T11 (suite de tests + chequeo diferencial).
- **WU-6** — T12 + T13 (CI/CD, FloCI, gateway).
- **WU-7** — T14 + T15 (agregados en catalog y delivery).
- **WU-8** — T16 (web).
- **WU-9** — T18 (cierre y evidencia).

Cada work unit se commitea por separado (Conventional Commits, scope
`reviews` / `orders` / `catalog` / `delivery` / `gateway` / `web`), con tests y
docs acompañando al comportamiento.

## Log

- **L1** (usuario, verbatim): "quiero agregar un sistema de calificaciones, tanto para clientes, repartidores y tiendas."
- **L2** (usuario, verbatim): "ayudame a planeartlo"
- **L3** (usuario, verbatim): "antes de eso. asegurate de que estamos trabajando con la ultima version del main del repo en github"
  → Ejecutado: `git fetch origin --prune`; `main` local y rama de trabajo
  actualizados a `origin/main` @ `de20e53` (PR #101). La rama previa
  `fix/delivery-route-assignment-transactional` ya estaba mergeada (PR #98 y
  #100): no hay nada pendiente de pushear. Rama nueva `feat/ratings-system`
  creada desde `origin/main`.
- **L4** (decisión de sesión): perfil git `team`, flujo `direct` (PRs a `main`
  con squash). Persistido en Engram como `git-mentor:project:flashdrop`.
- **L5** (hallazgo): la exploración delegada a `gentle-ai-explore` falló por
  cuota del modelo; el mapa de dominio se armó en línea (rg acotado).
- **L6** (decisiones del usuario): D1 = servicio nuevo `reviews-service`; D2 =
  matriz completa de 6 relaciones; D3 = estrellas + comentario opcional de 500
  caracteres; D4 = promedio y comentarios públicos. El plan fue reescrito desde
  la variante "dentro de orders-service".
- **L7** (hallazgo de diseño): `IdConverter` (orders) es una biyección
  `UUID ↔ Long` (`new UUID(0, id)`), o sea que el **id numérico público es el
  espacio común** entre auth, catalog y orders. `reviews-service` trabaja solo
  en `Long` y no necesita conversiones propias.
- **L8** (hallazgo de infraestructura): el gateway de producción se configura en
  `gateway/docker/gateway.yaml` (puertos: auth 8081, catalog 8082, orders 8083,
  delivery 8084 → **reviews 8085**); los workflows de CI/CD viven en
  `.github/workflows/` de la raíz y las task-definitions de FloCI en
  `infra/floci/task-definitions/`.
