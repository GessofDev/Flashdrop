# Tasks: `ratings-system`

Ejecución trackeada en `odd/tasks/ratings-system.md` (feature doc ODD). Este
archivo es el espejo de convención del repo.

## Status

| T | Task | Superficie | Estado |
|---|---|---|---|
| T0 | Spike read-only (ids, endpoint interno, plantillas de infra) | — | ✅ Hecho (notas en el feature doc, L9) |
| T1 | OpenSpec change + doc de diseño en `docs/plans/` | `openspec/changes/ratings-system/**`, `docs/plans/**` | ✅ Hecho |
| T2 | Esqueleto de `reviews-service` (Gradle, Dockerfile, config, health, openapi) | `services/reviews-service/**` | ⏳ En curso |
| T3 | Persistencia: `V1__reviews.sql`, entity, repositorio JPA, IT | `services/reviews-service/**` | ⏳ En curso |
| T4 | `GET /api/internal/orders/{id}` en orders + test | `services/orders-service/**` | ⏳ En curso |
| T5 | Dominio: `Rating`, `RatingTarget`, `RatingPolicy` + tests | `services/reviews-service/**` | ⏳ En curso |
| T6 | Puertos: `ReviewRepositoryPort`, `OrderLookupPort` + adapters | `services/reviews-service/**` | ⏳ En curso |
| T7 | Caso de uso `CreateReview` | `services/reviews-service/**` | ⏳ En curso |
| T8 | API pública de reviews | `services/reviews-service/**` | ⏳ En curso |
| T9 | API interna de agregados | `services/reviews-service/**` | ⏳ En curso |
| T10 | Seguridad: roles, participación, sanitización | `services/reviews-service/**` | ⏳ En curso |
| T11 | Tests de API, migración y chequeo diferencial de orders | ambos servicios | ⏳ Pendiente |
| T12 | CI/CD, task-definition FloCI, grants, compose | `.github/workflows/**`, `infra/floci/**`, `gateway/docker/**` | ⏳ En curso |
| T13 | Gateway: ruta `/api/reviews` + rate limit | `gateway/docker/gateway.yaml` | ⏳ En curso |
| T14 | catalog-service: `restaurant_rating_stats` + promedio en la ficha | `services/catalog-service/**` | ⏳ En curso |
| T15 | delivery-service: `delivery_person_rating_stats` + promedio en el perfil | `services/delivery-service/**` | ⏳ En curso |
| T16 | Web: formulario de calificación + promedios | `apps/web/**` | ⏳ En curso |
| T17 | Moderación y denuncias | — | ⛔ Fuera de v1 |
| T18 | Cierre: evidencia e informe | docs | ⏳ Pendiente |

## Contratos entre tareas

Estos contratos son la dependencia dura entre frentes paralelos. Si alguno
cambia, hay que actualizar **los dos** lados y avisar en el feature doc.

1. `orders-service` → `reviews-service`
   `GET /api/internal/orders/{id}` (header `X-Internal-Api-Key`) →
   `{ "orderId": Long, "status": String, "clientId": Long, "restaurantId": Long, "courierUserId": Long|null }`
2. `reviews-service` → `catalog-service` / `delivery-service`
   `GET /api/internal/reviews/aggregates?targetType=TIENDA|REPARTIDOR&ids=1,2,3`
   (header `X-Internal-Api-Key`) →
   `{ "items": [ { "targetId": Long, "average": double, "count": long } ] }`
3. `reviews-service` → web
   `POST /api/reviews`, `GET /api/reviews/pending/{orderId}`,
   `GET /api/reviews/{targetType}/{targetId}`, `GET /api/users/me/reviews`.

## Orden de merge

El PR es único y va contra `main` (flujo `direct`, squash). Los work units se
commitean en este orden para que el diff sea legible:

1. WU-1 · docs (este change + doc de diseño)
2. WU-2 · esqueleto y persistencia de `reviews-service`
3. WU-3 · contrato con orders + dominio + puertos
4. WU-4 · caso de uso + API + seguridad
5. WU-5 · tests
6. WU-6 · CI/CD + gateway
7. WU-7 · agregados en catalog y delivery
8. WU-8 · web
9. WU-9 · cierre y evidencia
