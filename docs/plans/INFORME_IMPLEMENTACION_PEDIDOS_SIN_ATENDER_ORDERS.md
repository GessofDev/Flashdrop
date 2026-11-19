# Informe de implementación: pedidos que nadie atiende (F7)

**Rama:** `feat/orders-unattended-timeouts` (PR 2 de 2). Se apoya en `feat/orders-delivery-tariff` (PR 1).
**Servicio:** `orders-service`.
**Diseño:** `docs/plans/2026-11-19-delivery-tariff-and-card-payment-design.md` · **Seguimiento:** `odd/tasks/orders-delivery-tariff-and-unattended.md`.

## Qué cambió

### 1. La tienda toma todo pedido pagado (sin aceptarlo a mano)

Cuando el pago se aprueba, el pedido pasa **directo de "Pendiente de pago" a "Preparando"**. Ya no existe la espera de que el restaurante acepte cada pedido, ni un plazo de respuesta de la tienda.

**Verificar si hay repartidores disponibles es decisión del restaurante.** Desde ese momento puede:

- **Continuar** con el pedido, o
- **Rechazarlo** (`Preparando → Rechazado` o `Listo para retiro → Rechazado`) si decide que no puede atenderlo, **también cuando ya hay un repartidor asignado**. Al rechazarlo se **cancela la ruta** en Delivery (y con ella la del repartidor). Si Delivery falla, el rechazo se deshace para no dejar un pedido rechazado con la ruta abierta. Una vez retirado por el repartidor ya no puede rechazarlo (eso va por la anulación después del retiro).

El sistema no decide por él ni asigna repartidor a la fuerza.

> Este cambio **reemplaza** lo que describía el PR 1 (pago aprobado → "Nuevo pedido" con plazo de respuesta de la tienda). El PR 1 por sí solo deja el pedido en "Nuevo pedido"; este PR lo cambia a "Preparando". Por eso conviene fusionarlos juntos y en orden.

### 2. El flujo del repartidor no cambia

El repartidor **solo toma el pedido cuando está "Listo para retiro"** (el listado de disponibles ya funciona así). Si pasado el tiempo parametrizable **nadie lo toma**, el pedido se cancela solo y pasa a **Cancelado** con motivo `NO_COURIER`.

- **Cuándo empieza a correr la espera:** desde que el pedido queda **"Listo para retiro"**, que es cuando un repartidor puede tomarlo. Un pedido que sigue en "Preparando" **nunca** se cancela por falta de repartidor: el tiempo de cocina no cuenta.
- **Cuánto dura:** `courierWaitMinutes`, parametrizable por el administrador (20 min por defecto), igual para todos los locales.
- **Cuándo se detiene:** en cuanto un repartidor toma el pedido, deja de aplicarse.
- Solo se avisa al cliente cuando se cancela: ve "Cancelado" con `cancelReason`, `cancelReasonMessage` y `cancelledAt` (listado y detalle). No hay avisos de espera.
- **Sin carreras.** La cancelación es una sola sentencia condicional (sigue en "Listo para retiro" y sin repartidor). Si un repartidor lo toma justo antes, no se cancela.
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

`V14__unattended_orders.sql` (idempotente): `order_policies.courier_wait_minutes` (CHECK 1..1440; la fila global queda en 20) y, en `orders`: `accepted_at` (cuándo la tienda tomó el pedido), `courier_due_at` (hasta cuándo se espera un repartidor, desde que queda listo), `cancel_reason`, `cancelled_at`, con un índice parcial para la tarea.

## ⚠️ Puntos a tener en cuenta

- **Se activan las tareas programadas en Orders** (`@EnableScheduling`). Hasta ahora no estaban habilitadas, así que `PendingDeliveryOperationRecoveryService` (recuperación de liberaciones y anulaciones pendientes, cada 60 s) **no corría**. Con este PR empieza a correr.
- **Los pedidos que ya existen no se cancelan solos.** No tienen plazo guardado (`courier_due_at` nulo). Solo aplica a pedidos que queden "Listo para retiro" desde este despliegue.
- **Un pedido que el restaurante nunca deja listo** no tiene plazo propio: se queda en "Preparando" hasta que el restaurante lo marque listo o lo rechace. Es una decisión de este diseño (el criterio es del restaurante); si más adelante se quiere un tope, se agrega aparte.
- **Devolución del dinero.** Cuando la tienda rechaza o el sistema cancela un pedido ya pagado, el pago no se devuelve (fuera de alcance, queda anotado).

## Configuración nueva

| Propiedad | Default | Uso |
| --- | --- | --- |
| `flashdrop.orders.timeouts.courier-wait-delay-ms` | `60000` | Cada cuánto se buscan pedidos sin repartidor. |

Los plazos (minutos) están en la base y los edita el administrador. Valores iniciales: pago 15, cancelación del cliente 5, espera de repartidor 20.

## Pruebas

- **Unitarias:** pago aprobado → Preparando (sin fijar la espera de repartidor); dejar el pedido listo fija la espera; tarea y cancelador (un fallo no frena a los demás; se deshace si Delivery falla); rechazo del restaurante desde Preparando o Listo para retiro, con y sin repartidor asignado, que cierra la ruta (y se deshace si Delivery falla), y el cliente que sigue sin poder cancelar un pedido con repartidor; liberación; controlador del administrador y la regla "plazos solo globales".
- **Contra PostgreSQL real:** el pago confirma una sola vez y deja `accepted_at` sin `courier_due_at`; dejar listo fija el plazo y conserva la hora de toma; la tarea solo ve pedidos "Listo para retiro" sin repartidor (uno en "Preparando" nunca vence); la cancelación no pisa a un repartidor que toma el pedido; reinicio del plazo al liberar.
- Totales: 548 unitarias y 19 de integración en verde.

## Pendientes para otros equipos (fuera de este alcance)

### App (Javier) y panel del restaurante
1. El pedido pagado llega al restaurante ya en **"Preparando"**: desaparece la pantalla de "aceptar / rechazar". El restaurante debe poder **rechazar** un pedido en Preparando o Listo para retiro (`PUT /api/orders/{id}/status` con `Rechazado`) si decide que no puede atenderlo, incluso con repartidor asignado, y marcarlo "Listo para retiro" cuando esté. La app del repartidor debe tolerar que su ruta pase a "Rechazado" (el pedido deja de estar activo para él).
2. Panel del restaurante: orientarlo a **revisar si hay repartidores disponibles** al recibir el pedido (hoy el backend no expone un conteo de repartidores disponibles; si se quiere mostrar, hay que agregarlo en Delivery).
3. Cliente: mostrar "Cancelado" con `cancelReasonMessage` ("No se encontro un repartidor a tiempo"). No prometer tiempos de espera.
4. Tras pagar, el estado que se espera es "Preparando" (no "Nuevo pedido").

### Web / administración
- Pantalla del administrador para `GET/PUT /api/admin/order-policy`. El gateway debe enrutar `/api/admin/**` hacia Orders (anotado en el PR 1).

### Fuera de alcance, queda anotado
- Devolución del dinero de pedidos rechazados o cancelados.
- Vencimiento de pedidos sin pagar y tope para pedidos que quedan en "Preparando".
- Avisos por correo o notificación (basta con ver el estado y el motivo).

## Validación y limitaciones conocidas

Antes de entregar se validó el estado confirmado (checkout limpio) con: pruebas unitarias, pruebas contra PostgreSQL real, una prueba que **levanta la aplicación completa** con las 14 migraciones y recorre el flujo de punta a punta (cotización, pago rechazado y reintento, pago aprobado, la tienda toma el pedido, "Listo para retiro", cancelación automática, rechazo con repartidor, endpoints del administrador) y otra sobre la **cadena de seguridad real** de los endpoints nuevos (el webhook no pide JWT y sí firma; el resto exige token). Además hubo una revisión de código; lo que resultó relevante se corrigió y lo demás queda como decisión o limitación:

**Corregido tras la revisión**
- Liberar un pedido anterior a F7 (sin plazo) ya no le crea uno: seguiría sin cancelarse solo.
- Comentarios desactualizados de la política y del caso de uso del pago.
- La tarea procesa hasta 200 pedidos por ciclo y registra los fallos sin repetir la traza completa.
- La pasarela simulada escribe una **advertencia clara al arrancar** (deja que el cliente apruebe su propio pago: no debe usarse en producción).

**Decisiones de diseño (no son defectos, pero conviene saberlas)**
- **Las coordenadas de destino son obligatorias** (PR #111, cobertura de despacho, fusionado antes que este PR): sin ellas el pedido se rechaza, y el local debe tener ubicación y radio de despacho. El envío de respaldo ($2.500) solo se cobra cuando el administrador no cargó rangos de tarifa. Esto reemplaza lo que se había acordado inicialmente (aceptar pedidos sin coordenadas con el respaldo).
- **Un pago aprobado activa el pedido aunque haya pasado `payment_expires_at`**: si la pasarela cobró, el pedido se activa. El vencimiento solo impide abrir intentos nuevos. No existe todavía una tarea que expire pedidos sin pagar (fuera de alcance).
- **Reembolsos:** un pedido pagado que se rechaza o se cancela solo queda con el pago `APROBADO` y no se devuelve el dinero (fuera de alcance, ya anotado).
- **Llave de idempotencia:** repetir la misma llave devuelve el pedido original sin comparar el contenido, y dos peticiones simultáneas con la misma llave podrían chocar con el índice único (error en una de ellas). Las apps deben usar una llave nueva por intento de compra.
- **La firma del webhook de la pasarela simulada es un secreto compartido**, no un HMAC del cuerpo. Es aceptable para la simulación; el adaptador de la pasarela real debe verificar la firma que esa pasarela defina.
- **Varias réplicas:** si hay más de una instancia de Orders, todas corren la tarea; la cancelación es una sentencia condicional y cerrar la ruta es idempotente, así que no hay doble cancelación, solo trabajo repetido.

## Reglas de otros equipos con las que esta entrega convive (no se relajan)

Estas reglas ya están en `main` y **esta entrega se adapta a ellas**, no al revés:

- **Cobertura de despacho (Javier, PR #111, F11).** Las coordenadas del destino son obligatorias y la cobertura se valida siempre (con o sin tarifa). El local debe tener **pin y radio de despacho** (1 a 50 km); sin ellos "no puede recibir pedidos nuevos". Un destino fuera del radio se rechaza. Orders revalida antes de crear el pedido y en la cotización. El envío de respaldo ($2.500) solo se cobra si el administrador no cargó rangos de tarifa.
  - **Antes de activar esta versión:** cada local debe configurar su pin y su radio (`PUT /api/catalog/my/restaurant/delivery-radius`). Un local sin radio rechaza todos sus pedidos.
  - **La app antigua** que no envíe coordenadas recibirá un rechazo: requiere una actualización coordinada, no una relajación de la validación.
  - Las migraciones de Catalog son independientes por servicio: V9 (dimensión del producto, este trabajo) y V10 (radio, Javier).
- **Foto de perfil (Nicolás, PR #110, F10).** Pertenece a Auth y no interactúa con esta entrega; solo comparte el despliegue (`docker-compose.stack.yml` y `env.stack.template` de gateway).
- **Despliegue:** las migraciones de cada servicio se aplican por el procedimiento de Sebastián.
