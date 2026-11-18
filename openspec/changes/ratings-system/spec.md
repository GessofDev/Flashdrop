# Spec: `ratings-system`

## 1. Overview

Este spec formaliza el sistema de calificaciones de FlashDrop: reseñas
ancladas a una orden, con una matriz de autorización entre cliente, repartidor
y tienda, promedios públicos y un servicio nuevo (`reviews-service`) como
dueño del dato.

El cambio es **aditivo**: agrega el servicio `reviews-service`, agrega un
endpoint interno a `orders-service`, y agrega consumo de promedios en
`catalog-service`, `delivery-service`, el gateway y la app web. No modifica el
comportamiento de ningún endpoint existente.

## 2. Functional Requirements

### FR-1 — Creación de una calificación

`POST /api/reviews` crea una calificación sobre una orden.

Request:

```json
{ "orderId": 501, "targetType": "REPARTIDOR", "rating": 5, "comment": "opcional" }
```

- `orderId` (Long, obligatorio): id **público** de la orden.
- `targetType` (enum, obligatorio): `TIENDA` | `REPARTIDOR` | `CLIENTE`.
- `rating` (int, obligatorio): entero de 1 a 5. Fuera de rango → 400.
- `comment` (string, opcional): máximo 500 caracteres. Si se manda vacío, se
  guarda como `null`.

**El cliente NUNCA envía el id del actor que califica.** El servidor lo
deriva de la orden: `TIENDA` → `restaurantId` de la orden, `REPARTIDOR` →
`deliveryInfo.userId` del repartidor, `CLIENTE` → `clientId` de la orden.

Respuestas:

| Código | Cuándo |
|---|---|
| 201 | Calificación creada. Devuelve la review (id, orderId, authorUserId, targetType, targetId, rating, comment, createdAt). |
| 400 | `rating` fuera de rango, body mal formado o `targetType` desconocido. |
| 401 | Sin JWT o JWT inválido. |
| 403 | El autor no tiene el rol o no es participante de la orden. |
| 404 | La orden no existe. |
| 409 | El autor ya calificó a ese tipo de destino en esa orden. |
| 422 | La orden no está en `ENTREGADO`. |

### FR-2 — Ventana de calificación

Solo se puede calificar una orden en estado `ENTREGADO`. El estado lo verifica
`reviews-service` consultando a `orders-service` por su endpoint interno; no
se persiste estado duplicado.

`reviews-service` no guarda nada si la orden no está entregada (422) ni si la
llamada interna falla (5xx por degradación, sin escritura parcial).

### FR-3 — Matriz de autorización

| Autor \ Destino | TIENDA | REPARTIDOR | CLIENTE |
|---|---|---|---|
| **CLIENTE** | ✅ | ✅ | ❌ |
| **REPARTIDOR** | ✅ | ❌ | ✅ |
| **TIENDA** | ❌ | ✅ | ✅ |

- El rol se toma del claim `roles` del JWT (`Cliente`, `Repartidor`,
  `Restaurante`). Un usuario puede tener varios roles: se valida **pertenencia**
  al conjunto, nunca el primer elemento.
- La participación se valida contra la orden: el autor debe ser el cliente, el
  repartidor o el dueño de la tienda de esa orden.
- No hay auto-calificación: el repartidor no se califica a sí mismo y la tienda
  no se califica a sí misma.

### FR-4 — Idempotencia

`UNIQUE (order_id, author_user_id, target_type)`. Un segundo intento de
calificar lo mismo devuelve **409** y no crea una fila nueva. El reintento de
red del cliente no duplica reseñas.

### FR-5 — Consulta de pendientes

`GET /api/reviews/pending/{orderId}` devuelve, para el usuario autenticado, las
calificaciones que todavía puede hacer en esa orden (derivadas de la matriz y
de la participación) y las que ya hizo.

Respuesta:

```json
{
  "orderId": 501,
  "orderStatus": "ENTREGADO",
  "pending": [ { "targetType": "REPARTIDOR", "targetId": 33, "targetLabel": "..." } ],
  "completed": [ { "targetType": "REPARTIDOR", "targetId": 33, "rating": 5, "comment": null } ]
}
```

Con `orderStatus` distinto de `ENTREGADO`, `pending` viene vacío.

### FR-6 — Consulta pública de un destino

`GET /api/reviews/{targetType}/{targetId}` devuelve la lista paginada de
reseñas públicas del destino y su agregado:

```json
{
  "targetType": "RESTAURANTE",
  "targetId": 7,
  "average": 4.5,
  "count": 12,
  "distribution": { "1": 0, "2": 1, "3": 2, "4": 4, "5": 5 },
  "reviews": [ { "id": 1, "authorUserId": 12, "rating": 5, "comment": "...", "createdAt": "..." } ]
}
```

- Solo se listan las reseñas con `status = PUBLICADO`.
- El `average` es el promedio de las reseñas publicadas; `count` su cantidad.
- `distribution` cuenta por estrella.
- `authorUserId` se expone (es la identidad del autor, no un dato de contacto).
  No se expone email, teléfono ni nombre.

### FR-7 — Historial del autor

`GET /api/users/me/reviews` devuelve las reseñas escritas por el usuario
autenticado, ordenadas de más reciente a más antigua, paginadas.

### FR-8 — Agregados internos (consumo de otros servicios)

`GET /api/internal/reviews/aggregates?targetType=TIENDA|REPARTIDOR&ids=1,2,3`
(protegido por `X-Internal-Api-Key`) devuelve los agregados de varios
destinos en una sola llamada:

```json
{ "items": [ { "targetId": 1, "average": 4.5, "count": 12 } ] }
```

- `ids` es una lista separada por comas, máximo 200 ids por llamada.
- `targetType` inválido → 400. `ids` vacío o malformado → 400.
- Los destinos sin reseñas aparecen con `average = 0.0` y `count = 0`.

### FR-9 — Endpoint interno de orden (orders-service)

`GET /api/internal/orders/{id}` (protegido por `X-Internal-Api-Key`) devuelve
el resumen de la orden para servicios internos:

```json
{ "orderId": 501, "status": "ENTREGADO", "clientId": 12, "restaurantId": 7, "courierUserId": 33 }
```

- Acepta id numérico público o UUID.
- `courierUserId` es `null` si la orden no tiene repartidor asignado.
- **No expone** dirección, teléfono, nombres, items ni importes.
- 404 si la orden no existe.

### FR-10 — Exposición de promedios en catalog y delivery

- `catalog-service`: la ficha del restaurante expone `ratingAverage` y
  `ratingCount`, servidos desde su propia tabla `restaurant_rating_stats`,
  refrescada por pull por lotes contra FR-8. Si el pull falla, sirve el último
  valor cacheado.
- `delivery-service`: el perfil del repartidor expone su promedio, servido
  desde `delivery_person_rating_stats` con la misma regla de degradación.

### FR-11 — Gateway

- Ruta `- prefix: /api/reviews` → `target: http://reviews-service:8085`,
  `stripPrefix: false`.
- Rate limit de escritura aplicado a los paths de creación de calificación.

### FR-12 — Web

- En la pantalla de seguimiento/detalle de orden, con la orden en `ENTREGADO`,
  el cliente ve el formulario de calificación para los destinos pendientes que
  devuelve FR-5: estrellas (1..5, botones con `aria-label`) y comentario
  opcional con contador de 500 caracteres.
- El cliente ve su calificación ya hecha como readonly (no hay edición en
  v1).
- Los promedios se muestran en la tarjeta de tienda del marketplace y en el
  perfil del repartidor **solo si** el dato viene en la respuesta del backend;
  la UI no inventa ni calcula promedios.

## 3. Non-goals

- Edición o eliminación de reseñas.
- Moderación, denuncias y panel administrativo (tarea posterior T17; el campo
  `status` queda preparado).
- Respuestas del actor calificado.
- Imágenes, "me gusta" y reportes de reseña.
- Analítica histórica y series de tiempo.
- Notificaciones (push, email) al recibir una calificación.
- Calificación sin orden (reseñas de perfil).
