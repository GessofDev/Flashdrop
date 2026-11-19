# Feature: delivery tariff, card payment and unattended orders

Feature specification for F8 (declarative payment, fixed shipping) and F7 (orders nobody attends). Design: `docs/plans/2026-11-19-delivery-tariff-and-card-payment-design.md`.

## Specs

S1 — Medio de pago
> Solo Tarjeta. Efectivo y Transferencia no se eliminan del modelo, pero el sistema no los acepta.

S2 — Pasarela
> Simulada por ahora, con casos aprobado y rechazado. El backend maneja las respuestas desde y hacia la pasarela; la pantalla de pago simulada es del front.

S3 — Estados
> Pendiente de pago al crear; al aprobarse el pago la tienda lo toma de inmediato (Preparando), sin aceptarlo a mano. Pago rechazado: mismo pedido, nuevo intento.

S4 — Tarifa de envío
> Tabla de dimensión (vehículo y precio) más tabla de distancia (precio), parametrizables por el administrador. Envío = suma de ambos, calculado antes de pagar. Sin rangos o sin coordenadas de destino: $2.500.

S5 — Dimensión y distancia
> Dimensión 1 a 10 por producto, guardada en Catalog. Distancia en línea recta entre coordenadas del local y del destino.

S6 — Pedidos sin atender
> La tienda toma todo pedido pagado de inmediato (Preparando), sin aceptarlo a mano. Verificar si hay repartidores disponibles es decision del restaurante: puede rechazar el pedido o continuar. El repartidor solo toma el pedido cuando esta listo para retiro; si pasada la espera global (contada desde que queda listo) nadie lo toma, se cancela solo. Plazo parametrizable por el administrador. Solo se avisa al cliente cuando se cancela. Las devoluciones están fuera de alcance.

## Tasks

PR 1 — Tarifas y pago (`feat/orders-delivery-tariff`)

T1 | S1-S6 | inline | `docs(orders): plan tariff, card payment and unattended orders` (`6fcff01`)
T2 | S4 | inline | `feat(orders): add admin access rule and delivery destination coordinates` (`397ac5f`)
T3 | S4,S5 | inline | `feat(orders): add parametric delivery tariff and quote endpoint` (`4209b70`)
T4 | S1-S3 | inline | `feat(orders): add card payment flow with simulated gateway` (`7fa8b43`, tests `1dcd815`)
T5 | S5 | inline | `feat(catalog): add product dimension` (`c860663`)

PR 2 — Pedidos sin tomar (`feat/orders-unattended-timeouts`)

T6 | S6 | inline | `feat(orders): cancel orders nobody attends (F7)` (`56f4a04`): scheduling, courier-wait policy, store acceptance, admin endpoints
T7 | S6 | inline | store response timeout job and no-courier timeout job (same commit)

## Log

L1 — 2026-11-19 — Scope fixed with the user: only F7 and F8. Refunds, unpaid-order expiry, out-of-range handling, free shipping, commission, maps, app and web are out of scope.

L2 — 2026-11-19 — `main` already carries store coordinates (Catalog V8) and pickup coordinates on the route (courier live location, PR #106-#108), and Orders `V10__add_commune_to_orders.sql`. New Orders migrations start at V11. Destination coordinates do not exist yet and are added here.

L3 — 2026-11-19 — No Admin role exists: the administrator is whoever holds Cliente, Restaurante and Repartidor together (`AdminAccess` in auth-service). Orders mirrors the rule in one place.

L4 — 2026-11-19 — Fallback: no tariff ranges loaded, or destination without coordinates, charges the current $2.500 (`orders.delivery-fee`). Store without a pin while a tariff is active returns a clear error.

L5 — 2026-11-19 — PR 1 implemented on `feat/orders-delivery-tariff`. Orders unit suite 517+ green; DB tests against real Postgres cover the tariff tables, the atomic payment confirmation and V1-V13 on a base with legacy orders. Catalog tests green (Gradle, from `services/catalog-service`). Report: `docs/plans/INFORME_IMPLEMENTACION_TARIFA_Y_PAGO_ORDERS.md`.

L6 — 2026-11-19 — Decision: the delivery route is created when the payment is approved (not at order creation), so Delivery never sees unpaid orders. To keep what the app sent (distance, minutes) the order now stores `distance_km` and `estimated_minutes` (V13).

L7 — 2026-11-19 — Found while testing: Hibernate schema validation caught `smallint` vs `integer` on `products.dimension`; migration fixed before commit.

L8 — 2026-11-19 — PR 2 implemented on `feat/orders-unattended-timeouts` (based on PR 1). Store response time and courier wait are global only (`/api/admin/order-policy`); store-level values are ignored. The courier wait counts from the store acceptance (`accepted_at`). Cancellation is one conditional UPDATE; the Delivery route is closed in the same transaction. Report: `docs/plans/INFORME_IMPLEMENTACION_PEDIDOS_SIN_ATENDER_ORDERS.md`.

L9 — 2026-11-19 — Enabling `@EnableScheduling` also starts `PendingDeliveryOperationRecoveryService`, which existed but never ran in orders. Orders created before this change have no deadlines and are never auto-cancelled. A courier release resets the courier deadline so the released order is not cancelled right away.

L10 — 2026-11-19 — Change of direction (user): the restaurant no longer accepts each order by hand. When the payment is approved the order goes straight from "Pendiente de pago" to "Preparando" (`accepted_at` and `courier_due_at` are set in the same UPDATE); if the store cannot prepare it, it rejects it (`Preparando -> Rechazado`, only without an assigned courier). The store-response timeout job, the `STORE_NO_RESPONSE` reason and the `storeResponseMinutes` policy field were removed from the API (the DB column stays unused). The courier wait now counts from the payment. Known tradeoff: couriers only see "Listo para retiro", so a long preparation can exceed the courier wait.

L11 — 2026-11-19 — Refinement (user): the courier flow does not change — a courier can only take an order that is "Listo para retiro" — so the courier wait is no longer counted from the payment but from the moment the order becomes "Listo para retiro" (`courier_due_at` is set by `markReadyForPickup`, not by the payment). An order still in "Preparando" never expires for lack of a courier. Whether couriers are available is the restaurant's call: it can reject the order (Preparando -> Rechazado, only without an assigned courier) or continue. Supersedes the "courier wait counts from the payment" part of L10 and removes its known tradeoff.

L12 — 2026-11-19 — Last change (user): the restaurant can reject an order even when a courier is already assigned (`Preparando` or `Listo para retiro` -> `Rechazado`); the route is then closed in Delivery (`closeRoute(..., "Rechazado")`, which also cancels the courier's route and is idempotent), inside the same transaction so a Delivery failure undoes the rejection. The client still cannot cancel an order that has a courier (support handles it). After pickup (`Retirado`) the restaurant cannot reject. Every restaurant rejection now closes the route, with or without courier. Supersedes the "only without an assigned courier" restriction from L10/L11.
