# Feature: paridad backend → app web

Cerrar la brecha entre lo que el backend ya expone y lo que la app web (`apps/web`) consume. El
alcance nace de la auditoría de endpoints del 2026-11-24: 33 endpoints de dominio y administración
implementados en el backend que la web nunca invoca, 7 endpoints cableados a medias y 0 endpoints
que la web invoque sin respaldo en el backend.

## Specs

S1 — Alcance
> "ver que cosas estan hechas en el back y aun no estan implementadas"

La lista sale de la comparación exhaustiva de los 5 servicios Spring, el gateway y el cliente API
de `apps/web`. Cada hueco se cierra con el endpoint que ya existe: **no se agregan endpoints
nuevos al backend** en esta feature.

S2 — Confirmación de entrega (única vía a ENTREGADO)
> "Es la única vía hacia ENTREGADO."

`PUT /api/orders/{id}/status` con `Entregado` está prohibido en modo SEGURO:
> "Transicion de estado no permitida: la entrega se confirma con el codigo de verificacion (POST /api/orders/{id}/deliver)"

La web confirma la entrega con `POST /api/orders/{id}/deliver` y body `{"code": "123456"}`.

S3 — Modo NORMAL
> "En modo NORMAL la app del repartidor envía 000000."

El código público del modo normal es `000000`; el repartidor no escribe nada y la app lo envía.

S4 — Modo SEGURO
> "Código correcto: 200. Incorrecto: 422 con los intentos restantes"

Errores literales del backend, mostrados tal cual en la UI:
- `"Codigo incorrecto. Quedan N intentos."`
- `"Codigo incorrecto. Se agotaron los intentos: se genero un codigo nuevo para el cliente. Quedan N intentos."`
- `"Entrega bloqueada por intentos agotados. El pedido fue cancelado: no se pudo verificar la identidad de quien recibe el pedido"`

El repartidor escribe el código de 6 dígitos que le dicta el cliente; el repartidor **nunca** ve el
código (el backend solo lo expone al cliente dueño).

S5 — Cotización de envío
> "Calcula el total del pedido (subtotal, tarifa de despacho por distancia/dimensiones, total) antes de pagar"

`POST /api/orders/delivery-quote` antes de `POST /api/orders`. La tarifa la configura el admin en
`PUT /api/admin/delivery-tariff`.

La cotización **exige** las coordenadas del destino: sin ellas el backend responde 400 con
`"Selecciona una direccion con ubicacion para validar la cobertura"`, y lo mismo ocurre al crear
el pedido. La web las obtiene de la geolocalización del navegador y, como fallback, de la
geocodificación de la dirección escrita (decisión del usuario, L6).

S6 — Pagos
> "Permite reintentar el pago de un pedido que fue rechazado"

`GET /api/orders/{id}/payments` (estado e historial de intentos) y `POST /api/orders/{id}/payments`
(reintento) en la vista de pago del pedido.

S7 — Ubicación en vivo
> "El cliente ve al repartidor en el mapa durante su pedido"

`POST /api/delivery/location` (el repartidor reporta su posición) y
`GET /api/delivery/routes/{routeId}/location` (el cliente la consulta). El polling ya está
definido en `odd/tasks/courier-live-location.md`.

S8 — Datos del pedido en la UI
El detalle y la lista del pedido muestran `deliveryCode` (solo cliente, modo SEGURO),
`cancelReason`, `cancelReasonMessage`, `cancelledAt` y `receptionMode`, que el backend ya
devuelve en `OrderDetailResponse` y `OrderListResponse`.

S9 — Perfil y cuenta
`POST|GET|DELETE /auth/profile/photo` (foto de perfil, multipart) y `DELETE /auth/account`
(baja de cuenta con borrado asíncrono). El backend ya expone el campo `photo` en el perfil.

S10 — Mi Tienda y cobertura
`PUT /api/catalog/my/restaurant/location` (dirección y coordenadas) y
`PUT /api/catalog/my/restaurant/delivery-radius` (radio de entrega). El marketplace envía
`latitude`/`longitude` a `GET /catalog/restaurants` para filtrar por cobertura.

S11 — Panel de administración
`GET /auth/admin/users`, `PUT /auth/admin/users/{userId}/roles`, `GET|POST /catalog/admin/restaurants`,
`PUT /catalog/admin/restaurants/{restaurantId}/owner`, `POST /api/delivery/admin/persons`,
`GET|PUT /api/admin/delivery-tariff`, `GET|PUT /api/admin/order-policy`. La web no tiene UI de
administración: se agrega una sección aparte, no dentro de las pantallas de rol.

S12 — Fuera de alcance
No se consumen en la web: `POST /api/payments/webhook` (callback del proveedor de pagos),
`POST /api/payments/fake/{providerTxId}/resolve` (simulador), `GET /auth/.well-known/jwks.json`,
`GET /auth/validate`, `POST /catalog/products` (legacy), `POST /catalog/products/validate`.
`GET /api/users/me/reviews` se implementa como "mis reseñas" en el perfil (T12).

## Tasks

T1 | S2,S3,S4 | inline | `fix(web): confirm courier delivery through the verification code` (`0fa0254`)
T2 | S5 | inline | `feat(web): quote the delivery fee and send the destination coordinates` (`a108758`)
T3 | S6 | inline | `feat(web): show the payment state and retry a rejected payment`
T4 | S8 | inline | `feat(web): show the reception mode and the cancellation reason of an order`
T5 | S9 | inline | `feat(web): manage the profile photo`
T6 | S10 | inline | `feat(web): configure the store location and the delivery radius` (`6dc2a8d`)
T7 | S10 | inline | `feat(web): filter the restaurants by delivery coverage`
T8 | S7 | inline | `feat(web): follow the courier live location`
T9 | S4 | inline | `feat(web): release an order and cancel it after the pickup`
T10 | S9 | inline | `feat(web): delete the account`
T11 | S12 | inline | `feat(web): list the reviews I wrote`
T12 | S11 | inline | `feat(web): add the administration section`

Merge commits (PR base realignment, not spec work):

M1 | S1-S12 | inline | `Merge remote-tracking branch 'origin/main' into feat/web-courier-delivery-code` (`9818262`) — trae el PR #115 (`fix(orders): restore manual store acceptance after payment`), que es solo backend y no toca `apps/web`.

## Log

L1 — 2026-11-24 — Original request (verbatim):

> puedes revisar la app web y ver que cosas estan hechas en el back y aun no estan implementadas? haz una lista

L2 — 2026-11-24 — Auditoría (bucket C = 33 endpoints de dominio/admin sin uso en la web, bucket B =
7 endpoints cableados a medias, bucket D = 0). El bug de T1 es el único que rompe un flujo que hoy
funciona: la consola del repartidor confirma la entrega con `PUT /api/orders/{id}/status`, que el
backend rechaza en modo SEGURO.

L3 — 2026-11-24 — Decisión: T1 primero y sola en su work unit. Es el único hueco que rompe un
camino de usuario existente; el resto agrega capacidades nuevas y conviene de a uno para no
inflar la revisión.

L4 — 2026-11-24 — T1 cerrada (`0fa0254`, rama `feat/web-courier-delivery-code`): la consola del
repartidor confirma la entrega con `POST /api/orders/{id}/deliver` (modo SEGURO: pide el código
de 6 dígitos; modo NORMAL: envía `000000`), y `PUT /api/orders/{id}/status` queda solo para el
retiro. El modo de recepción sale de la ruta y, cuando una ruta vieja no lo declara, del detalle
del pedido. Verificación: 287 tests verdes (27 archivos), `tsc --noEmit` limpio, `eslint` limpio.

L5 — 2026-11-24 — Hallazgo que bloquea T2 y T3: `DeliveryFeeCalculator.calculate` exige lat/lng
del destino — `"Selecciona una direccion con ubicacion para validar la cobertura"` — y lo exige
tanto en `POST /api/orders/delivery-quote` como en `POST /api/orders`. El checkout de la web no
manda coordenadas, así que la creación del pedido falla con 400 y la cotización no se puede
pedir. Además exige que el local tenga coordenadas y radio de despacho
(`"El local no tiene ubicacion configurada"`, `"El local no tiene radio de despacho configurado"`),
y la web no tiene forma de configurarlos (T6).

L6 — 2026-11-24 — Decisión del usuario sobre las coordenadas del destino: "geolocalizacion por
navegador y geocodificacion como fallback". O sea: primero `navigator.geolocation`; si el cliente
la rechaza o no está disponible, la dirección escrita se resuelve con un geocodificador. El
proveedor elegido es Nominatim (OpenStreetMap), sin clave y sin dependencia nueva, aislado en un
módulo propio para poder cambiarlo. Nota: `POST /api/orders` rechaza `NORMAL` y asume `SEGURO`
cuando el campo no viene, así que la web no ofrece elegir el modo de recepción: todo pedido nuevo
nace seguro y el repartidor siempre pide el código (T1).

L7 — 2026-11-24 — T2 cerrada (`a108758`): el checkout resuelve la ubicación con el navegador
primero y con la geocodificación de la dirección escrita como fallback (Nominatim, sin clave, en
`apps/web/src/lib/geocode.ts`), manda `latitude`/`longitude` a `POST /api/orders`, deja de mandar
`distance_km` cuando la ubicación está confirmada y muestra el desglose de
`POST /api/orders/delivery-quote` con los montos del backend (o el mensaje literal cuando la
cotización falla). Verificación: 293 tests verdes (28 archivos), `tsc --noEmit` limpio, `eslint`
limpio.

L8 — 2026-11-24 — Decisión de la revisión de T2: las coordenadas no se invalidan cuando el cliente
edita la dirección, pero la app lo dice ("Cambiaste la dirección después de confirmar la
ubicación: puede que no coincidan") en vez de fingir que ambas cosas van juntas. La lógica de
ubicación quedó en el hook `useDeliveryLocation` para que el formulario no crezca.

L9 — 2026-11-24 — T6 cerrada (`6dc2a8d`): la pantalla "Ubicación & Despacho" (`/tienda/ubicacion`)
del rol tienda muestra las coordenadas y el radio que guarda el backend y deja guardarlos por
separado (`PUT /api/catalog/my/restaurant/location` y `PUT /api/catalog/my/restaurant/delivery-radius`).
Verificación: 300 tests verdes (29 archivos), `tsc --noEmit` limpio, `eslint` limpio.

L10 — 2026-11-24 — Regla del backend que condiciona T6: `catalog-service` rechaza el radio si el
local todavía no tiene ubicación ("Configura primero la ubicacion del local"), así que el dueño
tiene que guardar primero las coordenadas. Los dos formularios siguen siendo independientes y
muestran el mensaje literal del backend.

L11 — 2026-11-24 — T3 en curso: `GET /api/orders/{id}/payments` (estado del pago, `paymentStatus`
es el NOMBRE del enum, `paymentUrl` es un esquema falso `fake://flashdrop-pay/...` que la web no
puede enlazar) y `POST /api/orders/{id}/payments` (nuevo intento, 201). El reintento solo tiene
sentido con el pedido en "Pendiente de pago" y sin un pago abierto.
