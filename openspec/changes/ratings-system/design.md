# Design: `ratings-system`

## 1. Arquitectura

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

   GET /api/internal/reviews/aggregates?targetType=&ids=   (X-Internal-Api-Key)
        ▲                                  ▲
        │ promedio de TIENDA               │ promedio de REPARTIDOR
   catalog-service                    delivery-service
   (cachea en su propia DB)            (cachea en su propia DB)
```

`reviews-service` es el único dueño del dato. `catalog-service` y
`delivery-service` **no leen su base**: lo consultan por endpoint interno por
lotes y cachean el resultado en su propia base (AGENTS.md §2).

## 2. Modelo de datos

Base `reviews_db`, migración `V1__reviews.sql`:

```sql
CREATE TABLE reviews (
  id              BIGSERIAL PRIMARY KEY,
  order_id        BIGINT      NOT NULL,
  author_user_id  BIGINT      NOT NULL,
  target_type     VARCHAR(16) NOT NULL,          -- TIENDA | REPARTIDOR | CLIENTE
  target_id       BIGINT      NOT NULL,
  rating          SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
  comment         VARCHAR(500),
  status          VARCHAR(16) NOT NULL DEFAULT 'PUBLICADO',
  created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT uq_review_participant UNIQUE (order_id, author_user_id, target_type)
);
CREATE INDEX idx_reviews_target ON reviews (target_type, target_id);
CREATE INDEX idx_reviews_author ON reviews (author_user_id, created_at DESC);
```

### 2.1 Espacio de ids

`IdConverter` de `orders-service` es una biyección `UUID ↔ Long`
(`new UUID(0, id)`): el **id numérico público es el espacio común** entre
`auth-service` (`Long`), `catalog-service` (`Long`) y `orders-service` (UUID
interno, Long público). `reviews-service` trabaja **solo en `Long`** y no
necesita conversiones propias.

| Dato | Tipo en `reviews-service` | Origen |
|---|---|---|
| `order_id` | `Long` | id público de la orden (orders) |
| `author_user_id` | `Long` | claim `sub` del JWT (auth) |
| `target_id` (TIENDA) | `Long` | `catalog.restaurant.id` |
| `target_id` (REPARTIDOR) | `Long` | `auth.users.id` del repartidor |
| `target_id` (CLIENTE) | `Long` | `auth.users.id` del cliente |

El destino se identifica por el **usuario** y no por el perfil de repartidor:
un repartidor puede tener más de un perfil de `delivery_person`, y la identidad
que el resto del sistema reconoce es la del usuario en auth.

### 2.2 `target_id` polimórfico

No hay FK posible cross-servicio. `target_type` discrimina el espacio de ids
del `target_id`. Es una decisión deliberada: agregar una tabla por tipo
multiplicaría las migraciones y obligaría a los consumidores a hacer join
contra tres fuentes.

## 3. Contrato con `orders-service`

Nuevo endpoint interno (protegido por `X-Internal-Api-Key`):

```
GET /api/internal/orders/{id}
→ 200 { "orderId": 501, "status": "ENTREGADO", "clientId": 12,
        "restaurantId": 7, "courierUserId": 33 }
```

- Acepta id numérico **o** UUID (mismo criterio que `OrderIdResolver`).
- `courierUserId` sale de `Order.deliveryInfo.userId` (UUID interno) convertido
  con `IdConverter`; `null` si la orden no tiene repartidor.
- **No expone** dirección, teléfono, nombre del repartidor, items ni importes:
  es el mínimo que `reviews-service` necesita para validar la participación y
  derivar el destino.

## 4. Matriz de autorización

| Autor \ Destino | TIENDA | REPARTIDOR | CLIENTE |
|---|---|---|---|
| **CLIENTE** | ✅ | ✅ | ❌ |
| **REPARTIDOR** | ✅ | ❌ | ✅ |
| **TIENDA** | ❌ | ✅ | ✅ |

Reglas:

- El autor debe tener el rol del claim `roles` (`Cliente` / `Repartidor` /
  `Restaurante`; **multi-rol**, nunca el primero) **y** ser participante de esa
  orden.
- Solo `OrderStatus.ENTREGADO` habilita calificar.
- No hay auto-calificación.
- El `targetId` se **deriva de la orden**: el body del cliente solo declara el
  `targetType`. Esto elimina por construcción la posibilidad de calificar a un
  tercero sin relación.

## 5. API

### 5.1 Pública (JWT)

| Método | Path | Respuesta |
|---|---|---|
| `POST` | `/api/reviews` | 201 con la review creada |
| `GET` | `/api/reviews/pending/{orderId}` | pendientes + ya hechas por el autor |
| `GET` | `/api/reviews/{targetType}/{targetId}` | lista paginada + agregado |
| `GET` | `/api/users/me/reviews` | historial del autor |

Body de `POST /api/reviews`:

```json
{ "orderId": 501, "targetType": "REPARTIDOR", "rating": 5, "comment": "opcional" }
```

Códigos de error: 400 id inválido · 403 rol o relación no permitida · 404 orden
o destino inexistente · 409 ya calificado · 422 orden no entregada. Sobre
`ApiError` de `shared-observability`.

### 5.2 Interna (`X-Internal-Api-Key`)

```
GET /api/internal/reviews/aggregates?targetType=TIENDA&ids=1,2,3
→ 200 { "items": [ { "targetId": 1, "average": 4.5, "count": 12 } ] }
```

Contrato **estable**: lo consumen `catalog-service` y `delivery-service`.

## 6. Agregados y degradación

- `catalog-service`: tabla `restaurant_rating_stats(restaurant_id PK, average,
  count, updated_at)` + pull por lotes con TTL de ~5 min. Expone
  `ratingAverage` y `ratingCount` en la ficha del restaurante.
- `delivery-service`: tabla `delivery_person_rating_stats(...)` + mismo pull con
  `targetType=REPARTIDOR`. Expone el promedio en el perfil del repartidor.
- **Si el pull falla**, cada consumidor sirve el último valor cacheado y
  loguea el fallo. La disponibilidad del marketplace no depende de
  `reviews-service`.

## 7. Seguridad y anti-abuso

- JWT verificado localmente contra el JWKS de `auth-service` (patrón de
  `delivery-service`): `JwtAuthenticationFilter` + `RoleAuthoritiesExtractor` +
  `CurrentUserResolver`.
- `X-Internal-Api-Key` en todos los endpoints `/api/internal/**`
  (`InternalApiKeyFilter` de `shared-observability`); nunca se loguea.
- `comment` ≤ 500 caracteres, sanitizado a la salida, renderizado como texto
  plano.
- Rate limit de escritura en el gateway para los paths de calificación.
- Anti-spam v1: unicidad por (orden, autor, destino) + rate limit. La detección
  de spam coordinado y el bloqueo de cuentas quedan fuera de alcance.

## 8. Alternativas descartadas

| Alternativa | Por qué no |
|---|---|
| Tabla de reviews dentro de `orders-service` | Acopla el dominio de calificación al ciclo de vida de la orden y hace que `orders-service` cargue con moderación y agregados públicos que no le corresponden. Descartada por D1. |
| Una tabla por tipo de destino | Multiplica migraciones y obliga a los consumidores a unir contra tres fuentes. La tabla única con `target_type` mantiene la consulta simple. |
| Solo estrellas, sin texto | No diferencia "malo servicio" de "mala comida" (D3). |
| Moderación en v1 | Multiplica el alcance (denuncias, panel admin, permisos). Se difiere a T17 con el campo `status` ya preparado. |

## 9. Rollout

1. Esqueleto del servicio + migración + health + CI verde (sin endpoints de
   negocio): el servicio existe y es monitoreable.
2. Endpoint interno de orders + feature flag `reviews.enabled=false`.
3. API de calificaciones + seguridad + tests.
4. Pull de agregados en `catalog-service` y `delivery-service`.
5. Web: formulario y promedios.
6. Flag en `true` en dev, validación manual y decisión conjunta del equipo para
   producción (el PR lo mergea el reviewer, no el autor).
