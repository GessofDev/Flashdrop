# Diseño e implementación: sistema de calificaciones (reviews-service)

> Fecha: 2026-11-18 · Change OpenSpec: `openspec/changes/ratings-system/`
> Plan trackeado: `odd/tasks/ratings-system.md` · Rama: `feat/ratings-system`

## 1. Qué se construye

Un sistema de calificaciones (1-5 estrellas + comentario opcional) para los
tres actores de una orden: **cliente**, **repartidor** y **tienda**. Las
calificaciones se anclan a una orden entregada, se validan contra la
participación real de cada actor, y producen promedios públicos que el
marketplace y el perfil del repartidor muestran.

El dato vive en un **servicio nuevo `reviews-service`** (decisión D1 del
equipo): es el dueño de `reviews_db` y de las reglas del dominio de
"calificación"; `catalog-service` y `delivery-service` solo consumen los
agregados por endpoint interno.

## 2. Decisiones de diseño y por qué

### 2.1 El destino lo deriva el servidor

El cliente manda `targetType` (`TIENDA` | `REPARTIDOR` | `CLIENTE`), nunca el
id de a quién califica. `reviews-service` pide la orden a `orders-service` y
calcula el `target_id` a partir de ella.

Consecuencia: es imposible calificar a alguien con quien no se tuvo relación,
aunque el cliente manipule el body. El chequeo de "participó en la orden"
deja de ser una validación de input y pasa a ser una derivación.

### 2.2 Un endpoint interno nuevo en orders

`reviews-service` necesita dos cosas de una orden: que esté `ENTREGADO` y
quiénes participaron. Eso no existe expuesto hoy para servicios internos
(`orders-service` solo expone `/api/internal/orders` para lista y
`/api/internal/orders/claim`).

Se agrega `GET /api/internal/orders/{id}` que devuelve **el mínimo**:

```json
{ "orderId": 501, "status": "ENTREGADO", "clientId": 12, "restaurantId": 7, "courierUserId": 33 }
```

Sin dirección, sin teléfono, sin nombres, sin items, sin importes. El
endpoint responde a una pregunta de autorización, no a una consulta de
dominio.

### 2.3 Id numérico como espacio común

`IdConverter` de `orders-service` mapea `UUID ↔ Long` de forma biyectiva
(`new UUID(0, id)`). Como `auth-service` y `catalog-service` ya usan `Long`,
el **id público numérico es el espacio común** de los cuatro servicios.
`reviews-service` guarda y compara solo `Long`: no hay UUID en su esquema, no
necesita conversiones y no depende de la representación interna de orders.

### 2.4 Idempotencia por constraint

`UNIQUE (order_id, author_user_id, target_type)`. Un reintento del cliente
(por timeout de red) recibe 409 y no crea una segunda fila. La regla vive en
la base, no en la lógica de aplicación: no depende de que el flujo que la
invoca esté bien escrito.

### 2.5 Una sola tabla polimórfica

`target_type` + `target_id` en la misma tabla. Una tabla por tipo obligaría a
los consumidores (marketplace, perfil de repartidor) a unir contra tres
fuentes distintas y multiplicaría las migraciones.

## 3. Checklist de creación del servicio (T2, T12)

`reviews-service` se crea copiando la estructura de `delivery-service`, que
es el servicio más reciente y ya tiene el stack que necesitamos.

| Pieza | Origen / valor |
|---|---|
| Módulo Gradle | `include("reviews-service")` en `services/settings.gradle.kts` |
| Dependencias | mismas que `delivery-service`: web, validation, actuator, JPA, security, nimbus-jose-jwt, flyway + flyway-database-postgresql, `project(":shared-observability")`; Testcontainers **1.21.4** |
| Paquete | `com.flashdrop.reviews` |
| Puerto | **8085** (auth 8081, catalog 8082, orders 8083, delivery 8084) |
| Base | `reviews_db`, user `reviews_app` (FloCI) / `reviews_svc` (docker-compose local) |
| Migraciones | `db/migration/V1__reviews.sql` (base nueva ⇒ arranca en V1) |
| Config | `application.yml` (puerto, nombre) + `application-reviews.yml` (datasource, JWKS) + `application-orders.yml` (URL de orders + `INTERNAL_API_KEY`) |
| Seguridad | `JwtAuthenticationFilter` (verificación local contra JWKS), `RoleAuthoritiesExtractor`, `CurrentUserResolver`, `SecurityConfig` con `InternalApiKeyFilter` de `shared-observability` para `/api/internal/**` |
| Dockerfile | Copia del de delivery con `:reviews-service:bootJar` y `EXPOSE 8085` |
| CI/CD | `.github/workflows/reviews-service-ci.yml` (build + tests + postgres para IT) y `reviews-service-cd.yml` (deploy a FloCI) |
| FloCI | `infra/floci/task-definitions/reviews-service.dev.json` (puerto 8085, `reviews_db`, secret `flashdrop/internal-api-key`) |
| Compose | Servicio `reviews-service` en `gateway/docker/docker-compose.stack.yml`, red `floci_default` |
| Gateway | `- prefix: /api/reviews` → `target: http://reviews-service:8085`, `stripPrefix: false`, con rate limit de escritura |

> Nota de build: el `Dockerfile` se construye con contexto `services/` (raíz del
> multi-project), no `services/reviews-service/`, porque el módulo depende de
> `:shared-observability`.

## 4. Estrategia de pruebas

| Nivel | Qué cubre | Dónde |
|---|---|---|
| Unitario | Matriz de autorización completa (6 relaciones y sus rechazos), límites 1 y 5, rating inválido, idempotencia, derivación del `targetId` | `reviews-service` |
| `@WebMvcTest` | 4 escenarios con JWT firmado por rol (patrón de `delivery-service`), incluido "rol válido pero no participante" | `reviews-service` |
| IT Testcontainers | Constraints (`uq_review_participant`, `CHECK rating`), índices, lectura por destino | `reviews-service` |
| IT migración | `V1` deja el esquema esperado | `reviews-service` |
| Unitario (orders) | El endpoint interno devuelve ids numéricos y **no** expone datos de contacto | `orders-service` |
| Unitario (web) | Render del formulario, payload del POST, render del promedio | `apps/web` |

**Chequeo diferencial**: `GET /api/orders/**` y el resto de los endpoints de
`orders-service` deben responder igual antes y después del cambio (stdout,
stderr y exit code contra la base). El cambio agrega un endpoint; no puede
alterar los existentes.

## 5. Rollout

1. Esqueleto + migración + health + CI verde → el servicio existe y es
   monitoreable (aunque no haga nada útil todavía).
2. Endpoint interno de orders + feature flag `reviews.enabled=false`.
3. API de calificaciones + seguridad + suite de tests.
4. Pull de agregados en `catalog-service` y `delivery-service`.
5. Web: formulario y promedios.
6. Flag en `true` en dev → validación manual → decisión del equipo para
   producción.

## 6. Deuda que se deja consciente

- **Sin moderación en v1**: el campo `status` (`PUBLICADO` | `OCULTO`) ya está
  en el esquema, pero no hay endpoint de denuncia ni panel admin (T17).
- **Sin editar reseñas**: una vez escrita, la calificación es inmutable en v1.
- **Sin agregados incrementales**: el promedio se calcula sobre la tabla con
  índice por destino. Al volumen actual es suficiente; si aparece un
  materialized view, el contrato interno no cambia.
- **Anti-spam básico**: unicidad + rate limit. No hay detección de spam
  coordinado ni bloqueo de cuentas.
