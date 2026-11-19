# Informe de implementación: pedidos que nadie atiende (F7)

**Rama:** `feat/orders-unattended-timeouts` (PR 2 de 2). Se apoya en `feat/orders-delivery-tariff` (PR 1): el plazo de la tienda empieza cuando el pago se aprueba.
**Servicio:** `orders-service`.
**Diseño:** `docs/plans/2026-11-19-delivery-tariff-and-card-payment-design.md` · **Seguimiento:** `odd/tasks/orders-delivery-tariff-and-unattended.md`.

## Qué cambió

Ningún pedido queda esperando para siempre. Hay dos casos y los dos terminan igual: el pedido pasa a **Cancelado** con su motivo.

| Caso | Cuándo se cancela | Motivo |
| --- | --- | --- |
| La tienda no responde | El pedido está pagado y sigue en "Nuevo pedido" pasado el **plazo de respuesta de la tienda**. | `STORE_NO_RESPONSE` |
| Nadie lo toma | La tienda aceptó el pedido (Preparando o Listo para retiro) y sigue sin repartidor pasada la **espera de repartidor**, contada **desde que la tienda aceptó**. | `NO_COURIER` |

- **Plazos globales.** Los fija solo el administrador y son iguales para todos los locales. Si una tienda tiene valores propios para estos dos plazos, se ignoran.
- **Sin aviso previo.** El cliente no recibe avisos de espera: solo ve el pedido en "Cancelado" con el motivo (`cancelReason`, `cancelReasonMessage`, `cancelledAt` en el listado y el detalle).
- **Cada minuto** se revisan los pedidos vencidos (hasta 50 por ciclo y por tarea; intervalo en `flashdrop.orders.timeouts.*-delay-ms`).
- **Sin carreras.** La cancelación es una sola sentencia condicional (sigue en el estado esperado y sin repartidor). Si la tienda acepta, o un repartidor toma el pedido, justo antes, no se cancela.
- **Ruta de Delivery.** Se cierra como "Cancelado" dentro de la misma transacción: si Delivery falla se deshace la cancelación y se reintenta en el siguiente ciclo, para no dejar un pedido cancelado con su ruta abierta.
- **Liberación.** Si un repartidor libera un pedido, la espera de repartidor se reinicia desde ese momento (si no, el pedido quedaría vencido y se cancelaría de inmediato).
- **Evento.** Se publica `order.status.updated` (a "Cancelado") con el campo nuevo `reason`.
- **Devoluciones.** Fuera de alcance: solo se cancela y queda el motivo registrado.

## Endpoints nuevos (Orders)

| Método y ruta | Quién | Qué hace |
| --- | --- | --- |
| `GET` / `PUT /api/admin/order-policy` | Administrador (Cliente + Restaurante + Repartidor) | Ver y reemplazar los plazos globales: pago, respuesta de la tienda, ventana de cancelación del cliente y espera de repartidor. |

Contrato en `services/orders-service/openapi.yaml`.

## Migración

`V14__unattended_orders.sql` (idempotente): `order_policies.courier_wait_minutes` (CHECK 1..1440; la fila global queda en 20), y en `orders`: `accepted_at`, `courier_due_at`, `cancel_reason`, `cancelled_at`, con un índice parcial para la tarea de repartidor.

## ⚠️ Cambio de comportamiento a tener en cuenta

- **Se activan las tareas programadas en Orders** (`@EnableScheduling`). Hasta ahora no estaban habilitadas, así que `PendingDeliveryOperationRecoveryService` (recuperación de liberaciones y anulaciones pendientes, cada 60 s) **no corría**. Con este PR empieza a correr, que es lo que se pretendía al escribirla.
- **Los pedidos que ya existen no se cancelan solos.** No tienen plazo guardado (`store_response_due_at` y `courier_due_at` nulos). Solo aplica a pedidos pagados y aceptados desde este despliegue.

## Configuración nueva

| Propiedad | Default | Uso |
| --- | --- | --- |
| `flashdrop.orders.timeouts.store-response-delay-ms` | `60000` | Cada cuánto se buscan pedidos con la tienda sin respuesta. |
| `flashdrop.orders.timeouts.courier-wait-delay-ms` | `60000` | Cada cuánto se buscan pedidos sin repartidor. |

Los plazos (minutos) no son configuración del servicio: están en la base y los edita el administrador. Valores iniciales: pago 15, tienda 10, cancelación del cliente 5, repartidor 20.

## Pruebas

- **Unitarias:** tareas (un fallo no frena a los demás), cancelador (cierra la ruta, publica el motivo, deshace si Delivery falla), aceptación de la tienda, liberación, controlador del administrador (403 sin los tres roles, validación de rangos) y la regla "plazos solo globales".
- **Contra PostgreSQL real:** búsqueda de vencidos, cancelación condicional (no pisa una aceptación ni a un repartidor que toma el pedido), reinicio del plazo al liberar y la espera global sembrada por la migración.
- Totales: 543 unitarias y 19 de integración en verde.

## Pendientes para otros equipos (fuera de este alcance)

### App (Javier)
1. Mostrar el pedido "Cancelado" con `cancelReasonMessage` (por ejemplo "La tienda no respondio a tiempo" o "No se encontro un repartidor a tiempo"). El campo viene en el listado y en el detalle.
2. No prometer al cliente un tiempo de espera: el sistema no avisa antes de cancelar.
3. Panel del restaurante: indicar que debe responder dentro del plazo (`storeResponseMinutes`); si no, el pedido se cancela.

### Web / administración
- Pantalla del administrador para `GET/PUT /api/admin/order-policy` (con el token del administrador). El gateway debe enrutar `/api/admin/**` hacia Orders (ya anotado en el PR 1).

### Fuera de alcance, queda anotado
- Devolución del dinero de los pedidos cancelados (no hay cobro que devolver en pedidos "Pendiente de pago"; sí en los ya pagados).
- Vencimiento de pedidos sin pagar.
- Avisar al cliente por correo o notificación (basta con ver el estado y el motivo).
- Si el restaurante quiere rechazar el pedido sigue disponible el estado "Rechazado" por su cuenta (sin cambios).
