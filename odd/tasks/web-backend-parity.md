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
T3 | S6 | inline | `feat(web): show the payment of an order and retry a rejected one` (`d8411cd`)
T4 | S8 | inline | `feat(web): show the reception mode and the cancellation of an order` (`d652797`)
T5 | S9 | inline | `feat(web): upload, show and delete the profile photo` (`99f175b`)
T6 | S10 | inline | `feat(web): configure the store location and the delivery radius` (`6dc2a8d`)
T7 | S10 | inline | `feat(web): filter the restaurants by delivery coverage` (`0d3df07`)
T8 | S7 | inline | `feat(web): follow the courier live location` (`8415363`, `de5a759`)
T9 | S4 | inline | `feat(web): release an order and cancel it after the pickup` (`4011769`)
T10 | S9 | inline | `feat(web): delete the account` (`e4f0a07`)
T11 | S12 | inline | `feat(web): list the reviews I wrote` (`12369b6`)
T12 | S11 | inline | `feat(web): add the administration section` (`b644819`)

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

L12 — 2026-11-24 — T3 cerrada (`d8411cd`): el seguimiento del pedido muestra el estado del último
intento de pago (el NOMBRE del enum `PaymentStatus`: PENDIENTE, APROBADO, RECHAZADO, EXPIRADO),
su id y el vencimiento de la ventana de pago, con el botón "Reintentar pago" solo cuando el
pedido sigue en "Pendiente de pago" y no hay un intento abierto. La `paymentUrl` del backend es un
esquema falso (`fake://flashdrop-pay/...`) y la web nunca la muestra como enlace.

L13 — 2026-11-24 — La primera corrida del suite con T3 falló con 3 tests de otros archivos
(`ratings.test.tsx` entre ellos): no era T3, era la espera default de Testing Library (1 segundo)
en una máquina que corría el suite entero. El panel de pago suma una llamada asíncrona a la
pantalla de seguimiento, y con la máquina saturada el primer render no llegaba a tiempo. Se
subió `asyncUtilTimeout` a 5 segundos en `src/test/setup.ts` (`1d470b2`) y el mock compartido de
los pedidos pasó a responder el endpoint de pagos con una vista de pago y no con el detalle del
pedido. Verificación posterior: dos corridas seguidas del suite completas en verde (30 archivos,
311 tests), `tsc --noEmit` limpio, `eslint` limpio.

L14 — 2026-11-24 — T4 cerrada (`d652797`): el seguimiento del pedido muestra el modo de recepción
("Entrega normal" / "Entrega segura"), el código de verificación cuando el backend lo manda (solo
para el cliente dueño de un pedido seguro que no está entregado) y el motivo de la cancelación; la
lista muestra el motivo de los pedidos cancelados. Nada se inventa: cada campo se dibuja solo
cuando la respuesta lo trae.

L15 — 2026-11-24 — El suite siguió siendo frágil con la máquina saturada: Vitest mata un test a
los 5 segundos por defecto y los tests de la app manejan las pantallas con los timers reales del
navegador, así que un test correcto puede cruzar ese límite. Se subió `testTimeout` a 30 segundos
en `apps/web/vitest.config.ts` (`7a44e50`), con el porqué en el comentario. Verificación: tres
corridas seguidas del suite completas en verde (31 archivos, 320 tests).

L16 — 2026-11-24 — Se probó volver fiel el mock compartido de los pedidos (el backend siempre manda
`receptionMode`) y se revirtió: rompe el test defensivo "shows no reception block when the backend
sends no reception data", que cubre una rama real (el backend puede omitir el bloque completo).
Los cuatro campos se ejercitan con fixtures propios en `src/test/orderReception.test.tsx`.

L17 — 2026-11-24 — T5 cerrada (`99f175b`): `GET /auth/profile/photo` exige el header
`Authorization`, así que un `<img src>` no puede cargar la foto: los bytes viajan con la sesión y
se muestran con una object URL que se revoca al reemplazarla y al desmontar. Un perfil sin foto no
es un error (el backend responde 404 y la app no muestra nada). El `DELETE` usa la forma "bare"
porque responde 204 sin cuerpo.

L18 — 2026-11-24 — T10 cerrada (`e4f0a07`): la baja de cuenta pide la contraseña antes de borrar
(no es un clic) y, cuando el backend acepta, limpia la sesión y manda al login. La llamada va con
sesión "pública" y el header `Authorization` a mano: un 401 ahí significa dos cosas (contraseña
incorrecta o token vencido) y el cliente trata todo 401 de una llamada autenticada como sesión
vencida, lo que borraría la sesión tras una contraseña incorrecta. Consecuencia: en ese endpoint el
token no se renueva solo y el cliente ve el 401 literal del backend.

L19 — 2026-11-24 — Decisiones del usuario para T7 y T8:
- T8 (tracking en vivo): mapa real con **Leaflet + teselas de OpenStreetMap** (atribución visible
  y uso moderada del servidor de teselas).
- T7 (cobertura del marketplace): las coordenadas se mandan **siempre** que el cliente las tenga,
  con una salida explicada ("no hay locales con cobertura en tu zona") y la opción de ver todos.

L20 — 2026-11-24 — T7 cerrada (`0d3df07`): el marketplace incorpora la barra de cobertura
(`CoverageLocationBar`) para obtener coordenadas del navegador o geocodificar una dirección y
enviar `latitude`/`longitude` a `GET /catalog/restaurants`. Si ningún local cubre la zona,
muestra `"No hay locales con cobertura en tu zona"` con el botón `"Ver todos los locales"` y
permite alternar de vuelta a `"Filtrar por mi zona"` o quitar la ubicación. Verificación: 334 tests
verdes (34 archivos), `tsc --noEmit` limpio, `eslint` limpio.

L21 — 2026-11-24 — T8 cerrada (`8415363` backend, `de5a759` web):
- Backend: `delivery-service` agrega `GET /api/internal/routes/order/{orderId}` (`InternalRoutesController`)
  y `orders-service` (`DeliveryHttpClientAdapter` + `OrderEnricher` + `OrderDetailResponse.RouteDto`)
  expone `route.id` (`routeId`) en `GET /api/orders/{id}` con degradación graciosa si Delivery no
  responde o aún no hay ruta. Verificación en Docker JDK 21: `InternalRoutesControllerGetByOrderTest`
  verde y 28/28 tests unitarios de `orders-service` verdes.
- Web: `CourierConsolePage` permite compartir/detener la ubicación en vivo cada 10s vía
  `POST /api/delivery/location`; `OrderTrackingPage` renderiza `LiveRouteMap` (Leaflet + teselas de
  OpenStreetMap con atribución visible) haciendo polling cada 8s a `GET /api/delivery/routes/{routeId}/location`
  mientras el pedido está activo y `route.id` existe, cayendo a `StaticRouteMap` cuando no hay `route.id`
  o el pedido ya terminó. Verificación: 35 archivos / 338 tests verdes, `tsc --noEmit` + `vite build`
  limpios, `eslint .` limpio.

L22 — 2026-11-24 — T9 cerrada (`4011769`): la consola del repartidor agrega `"Liberar pedido"`
antes del retiro (`POST /api/orders/{id}/release` con motivo) y `"Anular tras retiro"` después
del retiro (`POST /api/orders/{id}/cancel-after-pickup` con estado final `Cancelado`/`Rechazado` y
motivo) vía `RouteIncidentForm`. Verificación: 36 archivos / 341 tests verdes, `tsc --noEmit` limpio,
`eslint .` limpio.

L23 — 2026-11-24 — T11 cerrada (`12369b6`): el perfil (`/perfil`) incorpora `MyReviewsSection`
consumiendo `GET /api/users/me/reviews`, con estado vacío (`"Todavía no escribiste reseñas."`) y
manejo de errores sin romper los guards de `profile.test.tsx`. Verificación: 37 archivos / 344
tests verdes, `tsc --noEmit` limpio, `eslint .` limpio.

L24 — 2026-11-24 — T12 cerrada (`b644819`): se agrega la sección `/admin` (fuera de las
pantallas de rol, accesible solo a cuentas con `CLIENTE`, `RESTAURANTE` y `REPARTIDOR`), con
enlace en `TopBar` y 5 paneles (`AdminUsersPanel`, `AdminRestaurantsPanel`, `AdminCouriersPanel`,
`AdminTariffPanel`, `AdminPolicyPanel`) cubriendo los 7 endpoints administrativos de S11.
Verificación: 38 archivos / 346 tests verdes, `tsc --noEmit` + `vite build` limpios, `eslint .`
limpio. Feature `web-backend-parity` (T1-T12) completa.
