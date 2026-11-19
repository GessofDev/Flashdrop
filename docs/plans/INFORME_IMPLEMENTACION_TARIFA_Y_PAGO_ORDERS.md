# Informe de implementación: tarifa de envío parametrizable y pago con tarjeta (F8)

**Rama:** `feat/orders-delivery-tariff` (PR 1 de 2). El PR 2 (`feat/orders-unattended-timeouts`, F7) se apoya en este.
**Servicios:** `orders-service` y `catalog-service`.
**Diseño:** `docs/plans/2026-11-19-delivery-tariff-and-card-payment-design.md` · **Seguimiento:** `odd/tasks/orders-delivery-tariff-and-unattended.md`.

## Qué cambió

| Tema | Resultado |
| --- | --- |
| Medio de pago | Solo Tarjeta (valor por defecto si no viene). Efectivo, Transferencia o cualquier otro valor se rechazan con 400 "Medio de pago no disponible". Siguen en el modelo y los pedidos históricos se leen igual. |
| Estados | El pedido nace **Pendiente de pago** con su cobro abierto. Pasa directo a **Preparando** cuando el pago se aprueba: la tienda toma todo pedido pagado (ver F7). |
| Pasarela | `PaymentGatewayPort` + adaptador simulado `FakePaymentGateway` (aprobado / rechazado). La real se cambia con `orders.payment.provider` sin tocar casos de uso. |
| Al aprobarse el pago | El pedido pasa directo a Preparando (la tienda lo toma), se registra `accepted_at`, se **crea la ruta en Delivery** (con coordenadas del local, distancia y tiempo) y se publica `order.created`. Antes esto ocurría al crear el pedido. |
| Pago rechazado | El pago queda `RECHAZADO`, el pedido sigue pendiente y el cliente reintenta con el mismo pedido. |
| Envío | `costo por dimensión + costo por distancia`, desde dos tablas que edita el administrador. La dimensión del pedido es la suma de `cantidad × dimensión` y define el vehículo (MOTO, BICI o AUTO). |
| Respaldo | Solo cuando el administrador no cargó rangos de tarifa: **$2.500** (`orders.delivery-fee`). Desde la cobertura de despacho (PR #111) las coordenadas del destino son obligatorias y el local debe tener ubicación y radio de despacho; si no, el pedido se rechaza con un error claro. |
| Distancia | Línea recta (haversine) entre el pin del local (Catalog) y el destino, detrás de `DistanceCalculator` (reemplazable por mapas). |
| Catalog | `products.dimension` (1 a 10, opcional). Se expone en `/api/internal/products` y los dueños pueden fijarla al crear o editar. Sin valor, Orders usa `orders.default-product-dimension` (1). |
| Administrador | No hay rol Admin: lo es quien tiene Cliente, Restaurante y Repartidor a la vez (`AdminAccess`, un solo punto). Los endpoints `/api/admin/**` responden 403 al resto. |

## Endpoints nuevos o cambiados (Orders)

| Método y ruta | Quién | Qué hace |
| --- | --- | --- |
| `POST /api/orders` | Cliente | Cambia: crea pendiente de pago. Acepta `latitude`, `longitude` y el header `Idempotency-Key`. Responde `status`, `deliveryFee`, `paymentId`, `paymentUrl`, `paymentExpiresAt`. |
| `POST /api/orders/delivery-quote` | Cliente | Cotiza subtotal, envío y total antes de pagar. |
| `GET /api/orders/{id}/payments` | Cliente dueño | Estado del pedido y del último intento de pago. |
| `POST /api/orders/{id}/payments` | Cliente dueño | Abre un nuevo intento (o devuelve el abierto). |
| `POST /api/payments/webhook` | Pasarela | Resultado del pago, firmado con `X-Payment-Signature`. Idempotente. |
| `POST /api/payments/fake/{txId}/resolve` | Cliente dueño | Solo con la pasarela simulada: aprobar (`APPROVED`) o rechazar (`REJECTED`) desde la pantalla de prueba. |
| `GET` / `PUT /api/admin/delivery-tariff` | Administrador | Ver y reemplazar las tablas de tarifa. |

Contrato completo en `services/orders-service/openapi.yaml`.

## Migraciones

| Servicio | Archivo | Contenido |
| --- | --- | --- |
| Orders | `V11__order_destination_coordinates.sql` | `orders.delivery_latitude/longitude` (nullable). |
| Orders | `V12__delivery_tariff.sql` | Tablas `delivery_tariff_dimension_ranges` y `delivery_tariff_distance_ranges`. |
| Orders | `V13__order_route_estimates.sql` | `orders.distance_km` y `estimated_minutes`, para crear la ruta al aprobarse el pago. |
| Catalog | `V9__add_product_dimension.sql` | `products.dimension` con CHECK 1..10. |

Todas son idempotentes (`IF NOT EXISTS`) y no borran datos. `V10` de Orders ya existía en `main` (comuna).

## Configuración nueva

| Variable | Default | Uso |
| --- | --- | --- |
| `PAYMENT_PROVIDER` | `fake` | Pasarela. `fake` = simulada. |
| `PAYMENT_WEBHOOK_SECRET` | vacío | Secreto con que la pasarela firma el aviso. **Sin valor se rechaza todo aviso.** Secrets Manager: `flashdrop/payment-webhook-secret`. |
| `DEFAULT_PRODUCT_DIMENSION` | `1` | Dimensión que se asume para un producto sin clasificar. |
| `DELIVERY_FEE` | `2500` | Ahora es el envío de respaldo. |

## Pruebas

- Orders: **unitarias** (dominio, calculador de tarifa, casos de uso de pago, controladores, pasarela simulada, haversine) y **contra PostgreSQL real** (tablas de tarifa, confirmación de pago atómica, migraciones V1 a V13 sobre una base con pedidos antiguos).
- Catalog: dominio, caso de uso del dueño y `JpaProductRepositoryAdapterIT` (persistencia y CHECK de la dimensión).
- Hallazgo durante el desarrollo: la validación de esquema de Hibernate detectó que la columna `dimension` era `smallint` y la entidad espera `integer`; se corrigió a `integer`.

## Pendientes para otros equipos (fuera de este alcance)

### App (Javier)
1. Soportar el estado **"Pendiente de pago"** antes de que se active el cobro: hoy las apps no lo conocen. **Hay que desplegar la app antes, o a la vez, que este cambio.**
2. Enviar `latitude` y `longitude` del destino en `POST /api/orders` y en la cotización: son **obligatorias** (sin ellas se rechaza: "Selecciona una direccion con ubicacion para validar la cobertura").
3. Llamar a `POST /api/orders/delivery-quote` y mostrar subtotal, envío y total **antes** de pagar.
4. Pantalla de pago simulada: abrir `paymentUrl` (`fake://flashdrop-pay/{txId}`), mostrar dos botones y llamar a `POST /api/payments/fake/{txId}/resolve` con `APPROVED` o `REJECTED`.
5. Tras pagar, consultar `GET /api/orders/{id}/payments` hasta que `orderStatus` sea "Preparando" (aprobado; la tienda toma el pedido) o `paymentStatus` sea `RECHAZADO` (permitir reintentar con `POST /api/orders/{id}/payments`).
6. Enviar un `Idempotency-Key` por intento de compra, para que un reintento por red no duplique el pedido.
7. Quitar Efectivo y Transferencia de las opciones de pago.
8. (Etapa Catalog) Campo de dimensión 1 a 10 al crear o editar un producto.

### Web y gateway
- Enrutar `/api/payments/**` y `/api/admin/**` hacia Orders (hoy el gateway solo conoce `/api/orders`). `/api/payments/webhook` debe llegar sin JWT.
- Panel de administración: consumir `GET/PUT /api/admin/delivery-tariff` con el token del administrador.

### Infraestructura
- Crear el secreto `flashdrop/payment-webhook-secret` y referenciarlo en la task definition de Orders (`PAYMENT_WEBHOOK_SECRET`).
- Ensayar las migraciones de Orders V6 a V13 sobre una copia de la base real antes de desplegar (fuera de alcance, pero recomendado: `PaymentsMigrationUpgradeTest` ya cubre el escenario en CI).

### Fuera de alcance, queda anotado
- Pedidos sin pagar no vencen todavía (existe el plazo `payment_expires_at`, pero no hay tarea que los cancele).
- Un pago aprobado de un pedido que ya se canceló no se reactiva ni se devuelve (`ORDER_NOT_PENDING`, queda en el registro).
- Devoluciones, envío gratis, comisión y servicio de mapas.
