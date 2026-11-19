# Tarifa de envío parametrizable y pago con tarjeta (F8) y pedidos sin atender (F7)

Resuelve dos brechas de `FlashDrop-1.0-Analisis-Brechas`: **F8** (pago solo declarativo y envío fijo) y **F7** (pedidos que nadie atiende). Solo toca `orders-service` y, para la dimensión del producto, `catalog-service`.

**Estado:** plan aprobado
**Feature ODD:** `odd/tasks/orders-delivery-tariff-and-unattended.md`
**Entrega:** dos PRs encadenados — (1) Tarifas y pago, (2) Pedidos sin tomar.

## Decisiones aprobadas

| Tema | Decisión |
| --- | --- |
| Medio de pago | Solo Tarjeta. Efectivo y Transferencia siguen en el modelo, pero el sistema los rechaza al llegar. |
| Pasarela | Interfaz `PaymentGatewayPort` con una versión simulada (aprobado / rechazado). La real la reemplaza sin tocar casos de uso. |
| Estados | El pedido nace "Pendiente de pago" y pasa a "Nuevo pedido" al aprobarse el pago. |
| Pago rechazado | El cliente reintenta con el mismo pedido. |
| Envío | Costo = costo por dimensión + costo por distancia, ambos desde tablas editables por el administrador. |
| Dimensión | Escala 1 a 10 por producto. La del pedido es la suma de cantidad × dimensión. Define el vehículo (moto, bici, auto). |
| Distancia | Línea recta (haversine) entre local y destino, detrás de `DistanceCalculator`, reemplazable por un servicio de mapas. |
| Respaldo | Sin rangos cargados, o destino sin coordenadas: envío de $2.500 (`orders.delivery-fee`). Local sin pin con tarifa activa: error claro. |
| Cuándo se calcula | Antes de pagar (cotización) y recalculado por el servidor al crear el pedido. |
| Administrador | No existe un rol Admin: es quien tiene Cliente, Restaurante y Repartidor a la vez (igual que Auth). Los endpoints `/api/admin/**` usan esa regla en un solo punto. |
| Plazos (F7) | Globales, solo los fija el administrador. Tienda sin respuesta y sin repartidor cancelan el pedido. Sin aviso previo al cliente. |
| Fuera de alcance | Ejecución de devoluciones, vencimiento de pedidos sin pagar, pedidos fuera de rango, envío gratis, comisión, mapas, apps y web. |

## Cálculo del envío

```text
dimensión del pedido = Σ (cantidad × dimensión del producto)      (sin dato: orders.default-product-dimension)
vehículo + costo dimensión = rango de dimensión que contiene el total
costo distancia            = rango de km que contiene la distancia
envío = costo dimensión + costo distancia
```

Reglas de las tablas: el primer rango parte en 0, no hay solapes ni huecos y el último queda abierto hacia arriba. Con ambas tablas vacías se usa el respaldo.

## Flujo de pago

1. `POST /api/orders` valida, calcula el envío y crea el pedido en "Pendiente de pago" con su pago abierto. Responde con el enlace de pago.
2. El cliente paga en la pantalla de la pasarela. La pasarela avisa a `POST /api/payments/webhook` (con firma).
3. Aprobado: el pedido pasa a "Nuevo pedido", se fija `store_response_due_at`, se crea la ruta en Delivery y se publica `order.created`.
4. Rechazado: el pago queda rechazado y el pedido sigue pendiente. `POST /api/orders/{id}/payments` abre otro intento.
5. Con la pasarela simulada, `POST /api/payments/fake/{txId}/resolve` permite elegir aprobar o rechazar desde la app.

## Migraciones (Orders)

Parten en V11 porque `main` ya trae `V10__add_commune_to_orders.sql`. Todas son idempotentes (`IF NOT EXISTS`).

## Pendientes para otros equipos

- **App (Javier):** estado "Pendiente de pago", pantalla simulada de pago, cotización antes de pagar, enviar coordenadas de destino, mostrar "Cancelado" con motivo. Debe adaptarse antes del despliegue.
- **Web / gateway:** enrutar `/api/payments/**` y `/api/admin/**` hacia Orders.
- **App de administración:** consumir los endpoints `/api/admin/**` con el token del administrador.
