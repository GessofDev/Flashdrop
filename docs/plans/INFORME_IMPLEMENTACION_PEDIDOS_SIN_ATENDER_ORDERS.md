# Informe de implementación: pedidos que nadie atiende (F7)

**Rama:** `feat/orders-unattended-timeouts` (PR 2 de 2). Se apoya en `feat/orders-delivery-tariff` (PR 1).
**Servicio:** `orders-service`.
**Diseño:** `docs/plans/2026-11-19-delivery-tariff-and-card-payment-design.md` · **Seguimiento:** `odd/tasks/orders-delivery-tariff-and-unattended.md`.

## Qué cambió

### 1. La tienda toma todo pedido pagado (sin aceptarlo a mano)

Cuando el pago se aprueba, el pedido pasa **directo de "Pendiente de pago" a "Preparando"**. Ya no existe la espera de que el restaurante acepte cada pedido, ni un plazo de respuesta de la tienda. Si el restaurante no puede preparar un pedido, lo **rechaza** (`Preparando → Rechazado`), que antes solo podía hacer sobre un "Nuevo pedido". Solo es posible mientras no hay repartidor asignado (con repartidor sigue valiendo "contacta a soporte").

> Este cambio **reemplaza** lo que describía el PR 1 (pago aprobado → "Nuevo pedido" con plazo de respuesta de la tienda). El PR 1 por sí solo deja el pedido en "Nuevo pedido"; este PR lo cambia a "Preparando". Por eso conviene fusionarlos juntos y en orden.

### 2. Pedido sin repartidor

Un pedido tomado por la tienda que sigue **sin repartidor** pasada la **espera de repartidor** se cancela solo y pasa a **Cancelado** con motivo `NO_COURIER`.

- La espera se cuenta **desde que el pedido entra** (el pago aprobado) y es parametrizable por el administrador (`courierWaitMinutes`, 20 min por defecto), igual para todos los locales.
- Solo se avisa al cliente cuando se cancela: ve "Cancelado" con `cancelReason`, `cancelReasonMessage` y `cancelledAt` (listado y detalle). No hay avisos de espera.
- **Sin carreras.** La cancelación es una sola sentencia condicional (sigue en el estado esperado y sin repartidor). Si un repartidor toma el pedido justo antes, no se cancela.
- **Delivery.** La ruta se cierra como "Cancelado" en la misma transacción; si Delivery falla se deshace la cancelación y se reintenta en el siguiente ciclo. Se publica `order.status.updated` con el campo nuevo `reason`.
- **Liberación.** Si un repartidor libera un pedido, la espera se reinicia desde ese momento.
- La tarea corre cada 60 s (`flashdrop.orders.timeouts.courier-wait-delay-ms`), hasta 50 pedidos por ciclo.
- Devoluciones: fuera de alcance. Solo se cancela y queda el motivo.

## Endpoints

| Método y ruta | Quién | Qué hace |
| --- | --- | --- |
| `GET` / `PUT /api/admin/order-policy` | Administrador (Cliente + Restaurante + Repartidor) | Ver y reemplazar los plazos globales: `paymentExpirationMinutes`, `customerCancelWindowMinutes` y `courierWaitMinutes`. |

La columna `order_policies.store_response_minutes` (creada en una migración anterior) sigue en la base pero **ya no se usa ni se expone**.

Contrato en `services/orders-service/openapi.yaml`.

## Migración

`V14__unattended_orders.sql` (idempotente): `order_policies.courier_wait_minutes` (CHECK 1..1440; la fila global queda en 20) y, en `orders`: `accepted_at` (cuándo la tienda tomó el pedido), `courier_due_at`, `cancel_reason`, `cancelled_at`, con un índice parcial para la tarea.

## ⚠️ Puntos a tener en cuenta

- **Espera de repartidor vs. tiempo de cocina.** Como el plazo corre desde el pago, un pedido que tarde más en prepararse que la espera de repartidor se cancelaría aunque la tienda lo esté haciendo. Hoy los repartidores solo ven pedidos en "Listo para retiro". Conviene fijar `courierWaitMinutes` por encima del tiempo típico de preparación, o decidir después si el repartidor debe ver los pedidos desde "Preparando".
- **Se activan las tareas programadas en Orders** (`@EnableScheduling`). Hasta ahora no estaban habilitadas, así que `PendingDeliveryOperationRecoveryService` (recuperación de liberaciones y anulaciones pendientes, cada 60 s) **no corría**. Con este PR empieza a correr.
- **Los pedidos que ya existen no se cancelan solos.** No tienen plazo guardado (`courier_due_at` nulo). Solo aplica a pedidos pagados desde este despliegue.
- **Devolución del dinero.** Cuando la tienda rechaza o el sistema cancela un pedido ya pagado, el pago no se devuelve (fuera de alcance, queda anotado).

## Configuración nueva

| Propiedad | Default | Uso |
| --- | --- | --- |
| `flashdrop.orders.timeouts.courier-wait-delay-ms` | `60000` | Cada cuánto se buscan pedidos sin repartidor. |

Los plazos (minutos) están en la base y los edita el administrador. Valores iniciales: pago 15, cancelación del cliente 5, espera de repartidor 20.

## Pruebas

- **Unitarias:** pago aprobado → Preparando con la espera de repartidor, tarea y cancelador (un fallo no frena a los demás; se deshace si Delivery falla), rechazo del restaurante desde Preparando (y bloqueado con repartidor asignado), liberación, controlador del administrador y la regla "plazos solo globales".
- **Contra PostgreSQL real:** el pago confirma una sola vez y deja `accepted_at` y `courier_due_at`; búsqueda de pedidos vencidos; cancelación condicional que no pisa a un repartidor que toma el pedido; reinicio del plazo al liberar.
- Totales: 541 unitarias y 19 de integración en verde.

## Pendientes para otros equipos (fuera de este alcance)

### App (Javier) y panel del restaurante
1. El pedido pagado llega al restaurante ya en **"Preparando"**: la pantalla de "aceptar / rechazar" desaparece. Debe poder **rechazar** un pedido en Preparando (`PUT /api/orders/{id}/status` con `Rechazado`) si no puede prepararlo, y marcarlo "Listo para retiro" cuando esté.
2. Cliente: mostrar "Cancelado" con `cancelReasonMessage` ("No se encontro un repartidor a tiempo"). No prometer tiempos de espera.
3. Tras pagar, el estado que se espera es "Preparando" (no "Nuevo pedido").

### Web / administración
- Pantalla del administrador para `GET/PUT /api/admin/order-policy`. El gateway debe enrutar `/api/admin/**` hacia Orders (anotado en el PR 1).

### Fuera de alcance, queda anotado
- Devolución del dinero de pedidos rechazados o cancelados.
- Vencimiento de pedidos sin pagar.
- Avisos por correo o notificación (basta con ver el estado y el motivo).
