# Feature: courier live location

Feature specification for storing the courier's real-time location in the backend, letting the customer follow the courier on a map during an active order, and letting couriers sort claimable orders by distance. Store coordinates are introduced as a prerequisite.

## Specs

S1 — Usos
> El cliente ve al repartidor en el mapa durante su pedido, y el repartidor ve los pedidos reclamables ordenados por cercanía (dispatch). Auditoría e historial del recorrido quedan para una etapa posterior.

S2 — Almacenamiento
> Opción B: la última posición vive en un Redis dedicado de `delivery-service` (`redis-delivery`, separado del `redis-cache` del gateway), con TTL. El historial a futuro se agrega con un Redis Stream volcado a Postgres por lotes.

S3 — Transporte hacia el cliente
> Polling: la app del cliente consulta cada 5–10 s. No se agrega SSE ni WebSocket.

S4 — Dispatch por cercanía
> Se mantiene el modelo pull (el repartidor reclama). La lista de reclamables expone las coordenadas de retiro y la app del repartidor la ordena con su propio GPS. El dispatch push queda para el futuro si hace falta.

S5 — Coordenadas de la tienda
> Pin manual: la tienda marca su ubicación en un mapa. Sin geocoding automático.

## Tasks

T1 | S1-S5 | inline | `docs(delivery): document courier live location design` (`7eca519`)
T2 | S5 | inline | `feat(catalog): add manual store coordinates` (PR 1 · WU-1)
T3 | S1,S5 | inline | `feat(delivery): store pickup coordinates and client user id on routes` (PR 2 · WU-2)
T4 | S4,S5 | inline | `feat(orders): propagate pickup coordinates for claimable orders` (PR 2 · WU-3)
T5 | S2 | inline | `chore(infra): add dedicated redis-delivery container and gateway rate limits` (PR 3 · WU-4)
T6 | S2 | inline | `feat(delivery): add redis courier location store and erasure hook` (PR 3 · WU-5)
T7 | S1,S3 | inline | `feat(delivery): add courier location report and customer polling endpoints` (PR 3 · WU-6)

## Log

L1 — 2026-11-19 — Original request (verbatim):

> necesito planear un cambio. quisiera entender las opciones que tenemos para poder guardar en backend la ubicacion en tiempo real de repartidor

L2 — 2026-11-19 — Discovery: there are no latitude/longitude fields anywhere in the backend. Store and route addresses are free text only (`catalog` store `address`, `delivery_routes.pickup_address` / `delivery_address`). Redis exists only as a plain container (`redis-cache`) used by the gateway rate limiter; it is not ElastiCache.

L3 — 2026-11-19 — Usage decision: "1 y 2" (customer map view + dispatch by proximity). The user notes that option B looks like the sweet spot and that history/audit can come later.

L4 — 2026-11-19 — Storage decision: option B with a dedicated `redis-delivery` container. Reusing `redis-cache` was rejected to respect service data ownership (AGENTS.md §2) and to isolate the gateway rate limiter from location write traffic.

L5 — 2026-11-19 — Transport decision: "1" (polling). The gateway does not need changes.

L6 — 2026-11-19 — Discovery: `DeliveryPerson` has no availability/shift concept, and dispatch today is pull via `ClaimDeliveryOrdersUseCase`. `EraseDeliveryDataUseCase` must also purge the location data.

L7 — 2026-11-19 — Dispatch decision: "1 por ahora esta bien. en el futuro se modificara el comporrtamiento si es necesario." Pull is kept, with claimable orders sorted by distance.

L8 — 2026-11-19 — Correction: with pull dispatch, Redis `GEOSEARCH` is not required (haversine over a short list is enough). Option B remains justified by high-frequency writes, TTL-based staleness, and a future push dispatch.

L9 — 2026-11-19 — Store coordinates decision: "1" (manual pin). Coordinates are copied into the order/route at creation time, the same way `pickupAddress` is copied today.

L10 — 2026-11-19 — The claimable order list is served by `orders-service` (`AvailableDeliveryOrderResponse`). Sorting is done client-side by the courier app to avoid an orders → delivery call per listing.

L11 — 2026-11-19 — Design approved: "si". Design document: `docs/plans/2026-11-19-courier-live-location-design.md`.

L12 — 2026-11-19 — Implementation plan written in `docs/plans/2026-11-19-courier-live-location-implementation.md` (3 chained PRs, WU-1 through WU-6). Key codebase findings incorporated: (a) `orders-service` has no `delivery_routes` table so no Orders migration is needed; (b) `internal.delivery_routes` lacks `client_user_id`, so WU-2 adds `client_user_id varchar(64)` to authorize 5–10s customer polling locally in `delivery-service` without a per-poll internal HTTP call to `orders-service`.

L13 — 2026-11-19 — All 6 work units implemented and verified on `feat/courier-live-location` (T2 `227c09a`, T3 `bed67fa`, T4 `cb53b5a`, T5 `560837a`, T6 `0388935`, T7).
