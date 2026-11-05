# Liberar pedidos asignados y anular pedidos ya retirados

El backend permitirá que un repartidor devuelva un pedido tomado antes de retirarlo y que el repartidor dueño o un usuario con rol `Soporte` anule un pedido ya retirado. Cada cambio exigirá un motivo y quedará registrado en una bitácora durable. El alcance es API y backend; no incluye interfaces web.

**Estado:** diseño aprobado  
**Feature ODD:** `odd/tasks/delivery-release-cancellation.md`  
**Servicios:** `auth-service`, `orders-service`, `delivery-service`

## Decisiones aprobadas

| Tema | Decisión |
| --- | --- |
| Autoridad del flujo | `orders-service` orquesta y conserva el estado del pedido y la bitácora. |
| Autoridad de la ruta | `delivery-service` conserva la asignación y se sincroniza por `/api/internal/*`. |
| Liberación | La puede hacer sólo el repartidor asignado, antes del retiro. |
| Anulación posterior al retiro | La puede hacer el repartidor dueño o un usuario con rol `Soporte`. |
| Estado final posterior al retiro | `Cancelado` o `Rechazado`, elegido por el actor. |
| Reasignación posterior al retiro | No permitida. |
| Motivo | Obligatorio, no vacío, con máximo 500 caracteres. |
| Interfaz | Fuera de alcance. |

## Por qué existe el cambio

`POST /api/delivery/claim` asigna un repartidor de forma atómica, pero no existe la operación inversa. Si el repartidor no puede llegar al local, el pedido conserva `delivery_id` y la ruta conserva `delivery_person_id`. `Order.isClaimable()` y el lock de la ruta impiden que otra persona lo tome.

La anulación posterior al retiro es un caso diferente: el repartidor ya tiene el pedido físico. No se puede liberar silenciosamente ni devolverlo a la cola. El procedimiento debe cerrar el pedido y la ruta, identificar al actor y conservar el motivo.

## API pública

Todos los endpoints viven en `orders-service`, usan el JWT existente y conservan el envelope `ApiResponse` del servicio.

### Liberar antes del retiro

```http
POST /api/orders/{orderId}/release
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "reason": "No puedo retirarme en tiempo"
}
```

Reglas:

- El actor debe tener rol `Repartidor` y ser el reparto asignado.
- El pedido debe estar en `LISTO_PARA_RETIRO`.
- `delivery_id` se limpia; el estado del pedido no cambia.
- La ruta queda sin repartidor y en `PENDIENTE`.
- El pedido vuelve a la cola FIFO de `GET /api/orders/available-for-delivery`.

### Anular después del retiro

```http
POST /api/orders/{orderId}/cancel-after-pickup
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "status": "Cancelado",
  "reason": "El cliente no se encuentra en el domicilio"
}
```

Reglas:

- Actores autorizados: el repartidor dueño de la asignación o un usuario con rol `Soporte`.
- El pedido debe estar en `RETIRADO` o en `EN_CAMINO` legacy.
- `status` sólo admite los valores exactos `Cancelado` y `Rechazado`.
- El pedido queda cerrado y la ruta se cierra con el mismo estado final.
- El endpoint genérico `PUT /api/orders/{orderId}/status` no habilita estas transiciones.

### Consultar la trazabilidad

```http
GET /api/orders/{orderId}/delivery-events
Authorization: Bearer <JWT con rol Soporte>
```

Devuelve la bitácora de operaciones del pedido, en orden cronológico. El repartidor no puede consultar la bitácora completa de pedidos ajenos.

## Matriz de autorización

| Actor | Liberar antes del retiro | Anular después del retiro | Consultar bitácora |
| --- | ---: | ---: | ---: |
| Repartidor dueño | Sí | Sí | No |
| Repartidor no dueño | No (`403`) | No (`403`) | No |
| `Soporte` | No | Sí, cualquier pedido | Sí |
| `Cliente` | No | No | No |
| `Restaurante` | No | No | No |

Si un token contiene `Repartidor` y `Soporte`, la autorización de `Soporte` prevalece y no se exige ownership de la ruta.

## Transiciones

### Pedido

| Operación | Estado anterior | Estado posterior | `delivery_id` |
| --- | --- | --- | --- |
| Liberación | `LISTO_PARA_RETIRO` | `LISTO_PARA_RETIRO` | Se limpia |
| Anulación | `RETIRADO` o `EN_CAMINO` legacy | `Cancelado` o `Rechazado` | Se conserva para historial |

`ENTREGADO`, `CANCELADO` y `RECHAZADO` son terminales.

### Ruta de Delivery

| Operación | Estado anterior | Estado posterior | `delivery_person_id` |
| --- | --- | --- | --- |
| Liberación | `ASSIGNED` o `RETIRAR_PEDIDO` | `PENDIENTE` | Se limpia |
| Anulación posterior al retiro | Ruta activa | `Cancelado` o `Rechazado` | Se conserva para historial |

## Contratos internos

### Liberar una ruta

```http
POST /api/internal/routes/order/{orderId}/release
X-Internal-Api-Key: <shared secret>
Content-Type: application/json

{
  "expectedDeliveryId": 42
}
```

La operación:

1. bloquea la ruta;
2. verifica el repartidor esperado;
3. limpia `delivery_person_id`;
4. cambia el estado a `PENDIENTE`;
5. responde éxito si la operación ya quedó aplicada.

Una ruta asignada a otro repartidor o en estado terminal produce `409`.

### Cerrar una ruta anulada

El endpoint existente se amplía para aceptar `Cancelado` y `Rechazado`:

```http
PATCH /api/internal/routes/order/{orderId}/status
X-Internal-Api-Key: <shared secret>
Content-Type: application/json

{
  "status": "Cancelado"
}
```

La actualización es idempotente cuando la ruta ya tiene el mismo estado final. Un estado final diferente produce `409`.

## Consistencia entre servicios

No existe una transacción distribuida entre `orders_db` y `delivery_db`. Por eso la operación usa una saga corta con una operación durable.

### Preparación

En una transacción de `orders-service`:

1. se bloquea el pedido;
2. se validan actor, ownership y estado;
3. se crea una operación `PENDING`;
4. se confirma la operación sin modificar todavía el pedido.

El pedido conserva su asignación mientras la ruta se sincroniza. Por eso otro repartidor no puede tomarlo durante la ventana intermedia.

### Sincronización

1. `orders-service` llama al endpoint interno de Delivery.
2. Delivery aplica el cambio de forma condicional e idempotente.
3. ante `4xx` definitivo, la operación queda `FAILED` y el pedido no cambia;
4. ante error de red o `5xx`, la operación queda `PENDING` y se reintenta.

### Finalización

En una segunda transacción de `orders-service`:

1. se bloquea nuevamente el pedido;
2. se revalidan el reparto esperado y el estado;
3. se actualiza `delivery_id` o el estado del pedido;
4. se marca la operación `SUCCEEDED`;
5. se confirma la transacción.

Si Delivery aplicó el cambio pero falla el commit final de Orders, el reinteno observa que Delivery ya está en el estado esperado, completa el pedido y marca la operación `SUCCEEDED`.

Un reconciliador periódico recupera operaciones `PENDING`. La misma API puede reanudarlas de forma idempotente.

## Bitácora durable

Nueva tabla `order_delivery_operations` en `orders-service`:

| Columna | Uso |
| --- | --- |
| `id` | Identificador de la operación. |
| `order_id` | Pedido afectado. |
| `operation_type` | `RELEASE` o `CANCEL_AFTER_PICKUP`. |
| `state` | `PENDING`, `SUCCEEDED` o `FAILED`. |
| `actor_user_id` | Usuario autenticado que ejecutó la acción. |
| `actor_role` | Rol con el que acted. |
| `reason` | Motivo obligatorio. |
| `target_status` | Estado final solicitado, cuando corresponde. |
| `previous_delivery_id` | Reparto anterior. |
| `resulting_delivery_id` | Reparto posterior. |
| `previous_status` | Estado previo del pedido. |
| `resulting_status` | Estado posterior. |
| `correlation_id` | Identificador de correlación de la solicitud. |
| `failure_code` | Causa resumida si la operación falla. |
| `created_at`, `updated_at` | Tiempos de auditoría. |

El pedido y el estado final de la operación se escriben en la misma transacción. Los intentos fallidos conservan actor, motivo, error y correlation id.

## Concurrencia e idempotencia

- `orders-service` bloquea el pedido antes de validar y finalizar.
- La liberación actualiza `delivery_id` sólo cuando todavía coincide con el reparto esperado.
- Delivery bloquea la ruta y compara el reparto esperado antes de liberar.
- Un `claim` y una `release` concurrentes tienen un único ganador.
- Repetir una liberación completada no produce efectos adicionales.
- Repetir una anulación con el mismo estado final devuelve éxito.
- Repetir una anulación con un estado final diferente devuelve `409`.

## Errores

| HTTP | Condición |
| ---: | --- |
| `400` | Body inválido, motivo vacío o estado final desconocido. |
| `401` | JWT ausente o inválido. |
| `403` | Rol insuficiente o pedido asignado a otro repartidor. |
| `404` | Pedido inexistente. |
| `409` | Estado incompatible, ruta terminal, conflicto de ownership o carrera perdida. |
| `503` | Delivery no está disponible; el pedido no se modifica. |

## Rol `Soporte`

- `auth-service` incorpora `Soporte` como rol canónico y lo emite en `roles[]` del JWT.
- `orders-service` reconoce `SOPORTE` en su dominio y política de autorización.
- `delivery-service` no recibe el JWT del actor en los endpoints internos; confía en la llamada autenticada por `X-Internal-Api-Key`.
- El body nunca acepta `actorUserId` ni `actorRole`: ambos salen del JWT.

## Estrategia de pruebas

### `orders-service`

- liberation exitosa conserva el estado y limpia `delivery_id`;
- repartidor ajeno, `Soporte` sin permiso de liberación y rol incorrecto producen `403`;
- liberación después del retiro produce `409`;
- anulación por repartidor dueño y por `Soporte` produce los estados permitidos;
- anulación por repartidor ajeno produce `403`;
- motivo ausente produce `400` y no modifica datos;
- Delivery caído conserva el pedido y deja una operación recuperable;
- reintento idempotente completa una operación parcialmente aplicada;
- la bitácora devuelve actor, motivo, estados y correlation id.

### `delivery-service`

- liberación condicional contra el repartidor esperado;
- liberación repetida sin efectos secundarios;
- rechazo de ruta de otro repartidor, inexistente o terminal;
- aceptación y normalización de `Cancelado` y `Rechazado`.

### Persistencia y regresión

- carrera `claim` versus `release`: sólo una operación gana;
- el claim existente conserva su comportamiento;
- `Retirado → Entregado` y la verificación por código no cambian;
- los contratos OpenAPI y los códigos HTTP coinciden con la implementación.

## Fuera de alcance

- Cambios en `apps/web` u otras interfaces.
- Reasignación de pedidos ya retirados.
- Notificaciones al cliente, restaurante o repartidor.
- Reemake automático del pedido.
- Reintegro o refund.
- Una consola de Soporte.

## Criterios de aceptación

- [ ] Un repartidor puede liberar su pedido `LISTO_PARA_RETIRO` con un motivo.
- [ ] El pedido liberado vuelve a estar disponible y la ruta queda `PENDIENTE`.
- [ ] El repartidor dueño o `Soporte` puede anular un pedido `RETIRADO`/`EN_CAMINO` con `Cancelado` o `Rechazado`.
- [ ] Toda operación aceptada conserva actor, motivo, estados y timestamp.
- [ ] Los intentos rechazados no modifican pedidos, rutas ni contadores.
- [ ] Un fallo de Delivery deja el pedido intacto y una operación recuperable.
- [ ] Las carreras de claim, release y cancelación no producen doble asignación.
