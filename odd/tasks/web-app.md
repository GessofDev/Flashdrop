# Feature: apps/web — app web FlashDrop (3 roles)

Objetivo: implementar la app web de FlashDrop en `apps/web` replicando las maquetas seleccionadas en `references/stitch_flashdrop_ui_design_specs`.
Stack: **Vite + React 19 + TS + Tailwind v4**; tokens del design system Kinetic Delivery System.
Rama: `feat/web-app` · Entrega: `ask-on-risk` · Tests: Vitest + Testing Library, E2E Playwright · Alcance: **solo frontend con backend actual**.

> Decisión de diseño que origina esta feature: `docs/plans/2026-10-28-flashdrop-web-ui-mockup-selection.md`.

---

## Specs

### S1 — Alcance: solo frontend con el backend actual

Quote del usuario: **"Solo frontend con backend actual"**.
Requisito: este feature no modifica `services/` ni `gateway/`. Todo dato mostrado proviene de un endpoint existente.
Fuera: rating/reseñas, GPS del repartidor, PIN de entrega, serie horaria de ventas, liquidación bancaria, POS/boleta, chat, propina, cupones, loyalty, alertas de stock, IA de optimización.
Checks: `git diff --name-only main...HEAD` no contiene `services/` ni `gateway/`; la app corre sin tocar los servicios Java.

### S2 — Stack de `apps/web`

Quote del usuario: **"Vite + React 19 + TS + Tailwind v4"**.
Requisito: `apps/web` es un proyecto pnpm con Vite, React 19, TypeScript estricto, Tailwind CSS v4 y React Router. `"strict": true` sin `any` salvo en fronteras JSON con wrapper tipado.
Checks: `cd apps/web && pnpm build` verde; `cd apps/web && pnpm test` verde; `cd apps/web && pnpm exec tsc --noEmit` sin errores. (El proyecto es pnpm autónomo, sin workspace raíz: `pnpm --filter web` no aplica.)

### S3 — Las maquetas seleccionadas son la fuente de verdad

Quote (nombres exactos de carpeta): `flashdrop_shell_can_nica_gu_a_de_navegaci_n_por_rol`, `flashdrop_design_system_v2.0_gu_a_de_tokens_kit_de_componentes`, `iniciar_sesi_n_portal_unificado_flashdrop`, `perfil_multi_rol_y_centro_de_cuentas_flashdrop_v2.0_1`, `marketplace_y_cat_logo_de_clientes_flashdrop_v2.0`, `gesti_n_de_cat_logo_y_men_mi_tienda`, `monitor_de_cocina_y_gestor_de_pedidos_mi_tienda`, `consola_del_repartidor_flashdrop_courier`, `seguimiento_de_pedido_en_vivo_fd_0042_flashdrop_v2.0`, `anal_ticas_de_ventas_y_rendimiento_mi_tienda_v2.0`.
Requisito: cada pantalla se construye desde la maqueta listada; el contenido de la v1 se porta al shell canónico v2 sin cambiar su estructura ni sus estados.
Checks: cada ruta de la app declara en su README o header de archivo la maqueta de origen; paridad visual por captura contra la maqueta.

### S4 — Design system: un solo origen de verdad

Tokens adoptados (valores exactos): primary `#E84A0E`, hover `#FF5A1F`, primary surface `#FFF4ED`, texto `#0F172A`, secundario `#334155`, muted `#64748B`, borde `#E2E8F0`, canvas `#F8FAFC`, superficie `#FFFFFF`. Semánticos: success `#15803D`/`#DCFCE7`, info `#1D4ED8`/`#DBEAFE`, warning `#B45309`/`#FEF3C7`, destructive `#B91C1C`/`#FEE2E2`. Tipografía: `Plus Jakarta Sans` (headers) + `Inter` (datos). Radios: 8px controles, 16px contenedores, 24px overlays. Foco: `outline: 2px solid #E84A0E` con offset 2px.
Requisito: CLP y códigos `#FD-XXXX` con `font-variant-numeric: tabular-nums`; `label-code` en mayúsculas con tracking `0.05em`.
Checks: tokens definidos en un único archivo `@theme` de Tailwind v4; test que asserta el valor de `--color-primary`; ningún hex literal de marca fuera del archivo de tokens.

### S5 — Shell canónico y selector de rol

Quote de la maqueta: **"FlashDrop Architecture v2.0 — Especificación de Shell y Navegación"**, **"Las 5 Zonas Canónicas del Top Shell (68px)"**, **"3 Modos de Superficie y Footer"**.
Requisito: top bar de 5 zonas (marca, local, contexto, telemetría, identidad), selector de 3 segmentos **"Cliente" / "Mi Tienda" / "Repartidor"**, y navegación por rol sin re-login: Cliente (Marketplace & Consumo, Mis Pedidos), Mi Tienda (KDS Pantalla Cocina, Catálogo & Menú, Historial Despachos, Analíticas & Ventas), Repartidor (Ruta Activa & GPS, Pedidos Disponibles (FIFO), Historial de Entregas).
Checks: test del switcher — cambiar de rol actualiza la navegación sin refetch de sesión; el rol activo persiste al recargar; una URL de rol ajeno redirige a la home de su rol.

### S6 — Estados de pedido: la máquina real

Values exactos de `OrderStatus`: **"Nuevo pedido"**, **"Preparando"**, **"Listo para retiro"**, **"Retirado"**, **"En camino"**, **"Entregado"**.
Transiciones por rol (`RoleTransitionPolicy`): RESTAURANTE → `PREPARANDO`, `LISTO_PARA_RETIRO`; REPARTIDOR → `RETIRADO`, `ENTREGADO`; CLIENTE → ninguna.
Requisito: `EN_CAMINO` es legacy en `orders-service` — **ningún rol puede fijarlo**. La fase "en camino" del cliente se deriva de `RouteStatus` (`PENDIENTE`, `ASSIGNED`, `RETIRAR_PEDIDO`, `EN_CAMINO`, `ENTREGADO`) en `delivery-service`, no de `order.status`.
Checks: test del stepper del cliente — 5 pasos, sin "En camino" como estado de pedido; test de la KDS — 3 columnas porque el restaurante solo fija 2 estados; test de que la UI no ofrece una transición prohibida al rol activo.

### S7 — Autorización y errores: mensajes literales del backend

**Forma de respuesta: los cuatro servicios NO comparten envelope.**
- `orders-service` y `delivery-service`: `{ success, message, data }` (`ApiResponse<T>`).
- `auth-service` y `catalog-service`: **DTO desnudo**, sin envelope (`AuthController.java:49` devuelve `AuthResult`; `CategoryController.java:24` devuelve `List<CategoryResponse>`; `MyStoreProductController.java:64` devuelve `List<ProductResponse>`).

Requisito: el cliente declara la forma **por endpoint**; no asume envelope. El cuerpo de error siempre trae `message` (más `status`/`error`; `timestamp` en delivery; `code`/`trace_id` en auth) y el cliente lo muestra **sin traducir ni inventar otro**.

**Status real por mensaje** (verificado en los handlers, no inferido):

| Status | Mensajes literales |
|---|---|
| 400 | **"Estado no valido: "**, **"El pedido debe contener productos de un mismo local"**, **"El pedido debe contener al menos un producto"**, **"Debe enviar al menos un campo para actualizar"**, **"No route found for orderId="**, **"Rango invalido: "** + `. Valores validos: day, week, month`, **"El Content-Type no coincide con el contenido de la imagen"** |
| 401 | **"No autenticado"** — solo en `delivery-service` (`GlobalExceptionHandler.java:145`); en `orders-service` el mismo `AccessDeniedException` se traduce a **403** (`GlobalExceptionHandler.java:90`) |
| 403 | **"No tienes permiso para esta vista de pedidos"**, **"No puedes consultar este pedido"**, **"Solo los repartidores pueden ver pedidos disponibles"**, **"Solo los restaurantes pueden consultar sus ventas"**, **"No puedes consultar las ventas de otro restaurante"**, **"El usuario no tiene perfil de repartidor"** |
| 404 | **"Pedido no encontrado"** |
| 409 | solo los de dominio cuyo texto matchea `"ya tienes"`, `"ya fueron tomados"`, `"alguien tomo"`, `"transicion de estado no permitida"` (`orders-service/.../GlobalExceptionHandler.java:38-52`) |

Requisito: 401 → redirige a login; 403 → vista de rol incorrecta; 409 → mensaje del backend con acción de reintento donde aplique.
Checks: test por cada status con el mensaje exacto; el mensaje mostrado es el del backend, no uno propio.

### S8 — Cliente: marketplace y catálogo público

Endpoints: `GET /catalog/restaurants`, `GET /catalog/products`, `GET /catalog/categories`, `GET /catalog/restaurants/{restaurantId}`.
Requisito: `RestaurantResponse` solo trae `id`, `name`, `address`, `phone`, `image` — la UI no muestra rating, número de reseñas, ETA por restaurante, costo de envío ni cupón. La maqueta `marketplace_y_cat_logo_de_clientes_flashdrop_v2.0` es la fuente: hero, chips de categoría, grid de restaurantes, "Platos Populares".
Checks: no existe componente que renderice estrellas, ETA de restaurante ni `#FLASH30`; test que renderiza el marketplace con datos reales del endpoint y no muestra campos inexistentes.

### S9 — Carrito y checkout

Endpoint: `POST /api/orders` con `address`, `payment_method`, `distance_km`, `estimated_minutes`, `items[]`.
Requisito: el checkout es **declarativo** (método de pago declarado por el cliente, sin pasarela real). El total se calcula en cliente desde los productos del carrito y se contrasta con el `total` que devuelve el backend.
Checks: test del POST — 2 locales distintos → la UI muestra **"El pedido debe contener productos de un mismo local"**; carrito vacío → **"El pedido debe contener al menos un producto"**; el `total` renderizado tras crear el pedido es el que devuelve el backend, no el cálculo local.

Nota de implementación (verificada): el caso del carrito vacío lo **valida el cliente antes de la request**. Un `items: []` cae en la rama legacy de un solo producto del controller (`OrderController.java:146-158`) y el backend respondería **"Uno o mas productos no existen"**, no el literal de dominio. La UI valida antes y muestra el literal del dominio —**"El pedido debe contener al menos un producto"** (`Order.validateSingleRestaurant`, `Order.java:70`)—, que es el mensaje canónico de esa condición. El caso de dos locales sí va al backend.

### S10 — Seguimiento del pedido

Requisito: pantalla `seguimiento_de_pedido_en_vivo_fd_0042_flashdrop_v2.0`: stepper de 5 pasos, bitácora de trazabilidad, resumen del pedido con `subtotal` / `deliveryFee` / `total`, y **"Garantía Térmica y de Tiempo"**. El chat queda como botón, no como panel abierto sobre el mapa. El mapa es **estático**: el backend expone `pickupAddress` y `deliveryAddress` (plan D-1: geocodificación client-side), no hay coordenadas ni telemetría.
Checks: no hay componente de chat, ni campo de PIN, ni lectura de coordenadas; test del stepper con los estados reales.

### S11 — Mi Tienda: catálogo, disponibilidad e imágenes

Endpoints: `GET /api/catalog/my/products`, `POST /api/catalog/my/products`, `PUT /api/catalog/my/products/{productId}`, `DELETE /api/catalog/my/products/{productId}`, `POST /api/catalog/my/products/image` (multipart), `GET /catalog/categories`.

Requisito: la tabla de `gesti_n_de_cat_logo_y_men_mi_tienda` (SKU, tags, precio, toggle de disponibilidad inline, acciones editar/duplicar/eliminar) y los filtros por categoría y por estado.

**No existe PATCH**: el toggle de disponibilidad es un **PUT con el producto completo** — `OwnerUpdateProductRequest` exige `categoryId`, `name`, `price` y `available` (`@NotNull`). El estado visual lo define la respuesta del backend: nada de toggle optimista sin rollback. `restaurantId` se deriva del JWT, no del body.

**La imagen es de dos pasos**: `POST /api/catalog/my/products/image` con la parte `file` responde 201 `{ objectKey, url }`, y el campo `image` del producto es el **objectKey** (patrón `^products/\d{4}/(0[1-9]|1[0-2])/[uuid].(jpg|jpeg|png|webp)$`).

**Sin ventas ni margen por producto**: `sales-summary` no expone series por producto, así que la columna de ventas 7d y el margen de la maqueta **no se pueden llenar**. Esa columna queda vacía hasta que exista el dato (T13/T14 lo resuelven contra el fixture canónico).

Checks: test del toggle — la UI refleja la respuesta del PUT (incluido 4xx sin cambio visual) y envía el producto completo; test de subida de imagen con Content-Type incorrecto → muestra **"El Content-Type no coincide con el contenido de la imagen"**; test de ownership — el `restaurantId` nunca viaja en el body; test del update vacío → muestra el mensaje literal que responda el backend.

**Nota sobre el update vacío**: el literal **"Debe enviar al menos un campo para actualizar"** pertenece al PUT público `PUT /catalog/products/{id}` (`UpdateProductUseCase`, `UpdateProductUseCase.java:32`). El PUT del dueño (`PUT /api/catalog/my/products/{id}`) exige el producto completo y un body vacío responde los mensajes de validación de Spring (`categoryId: must not be null`). La UI muestra lo que llegue, sin traducirlo.

### S12 — Monitor de cocina (KDS)

Requisito: 3 columnas **"POR ACEPTAR"**, **"EN COCINA"**, **"LISTO / RETIRO"**, con timer de elapsed time calculado desde `createdAt` (frontend-only), chime de cocina y atajos de teclado. El drawer de detalle muestra ítems, notas/alérgenos, totales y la línea de tiempo del pedido. La impresora POS, la boleta electrónica, el PIN de retiro, el SLA y el turno de almuerzo quedan fuera.
Checks: test de las 3 columnas vacías y con tarjetas; test que la acción disponible depende del rol (cliente no ve botones de estado); el timer deriva de `createdAt` y no de un dato inventado.

### S13 — Consola del repartidor

Endpoints: `GET /api/orders/available-for-delivery`, `POST /api/delivery/claim`, `GET /api/delivery/routes`, `PATCH /api/delivery/routes/{routeId}/status`, `PATCH /api/delivery/order/{orderId}/status`.
Requisito: lista FIFO de disponibles con `code`, `restaurantName`, `pickupAddress`, `deliveryAddress`, `total`, `distanceKm`, `estimatedMinutes`; claim atómico (el backend bloquea la reasignación); la UI **no** marca "tomado" localmente sin refetch. Sin PIN de entrega, sin chat, sin mapa GPS en vivo, sin propina.
Checks: test del claim — al perder el claim la UI recarga la lista y muestra el mensaje del backend; test de que la lista se ordena por llegada (FIFO) y no por confianza del cliente.

### S14 — Analíticas de tienda (recortadas al contrato real)

Endpoint: `GET /api/orders/restaurants/{id}/sales-summary?range=day|week|month`. Campos disponibles: `totalOrders`, `totalRevenue`, `averageTicket`, `topProducts` (`productId`, `productName`, `quantitySold`, `revenue`).
Requisito: la UI muestra **solo** esos indicadores y la tabla de top productos. No hay margen por producto, ni serie temporal diaria, ni curva horaria, ni liquidación bancaria, ni canales de pago, ni sugerencias de IA, ni alertas de stock, ni KPI de satisfacción o SLA de cocina.
Checks: test que renderiza con la respuesta real y no muestra ningún KPI fuera de la lista; un `range` inválido muestra **"Rango invalido: "** + `. Valores validos: day, week, month`.

### S15 — Perfil multi-rol y sesiones

Endpoints: `GET /auth/profile`, `PUT /auth/profile`, `GET /auth/validate`, roles por usuario.
Requisito: pantalla `perfil_multi_rol_y_centro_de_cuentas_flashdrop_v2.0_1`: identidad con RUT y badge "Cuenta Verificada", 3 tarjetas de rol (Cliente / Restaurante / Repartidor) con sus datos propios, y dispositivos conectados. `vehicle` **no** es editable desde la app (plan D-5, decisión D-5). Sin SII, sin POS, sin IP de sesión, sin telemetría.
Checks: test que el campo `vehicle` no es editable; test de que el perfil de cada rol muestra solo los datos que ese endpoint entrega.

### S16 — Fixture canónico único

Inconsistencias a corregir en un único fixture del repo (por ejemplo `apps/web/src/test/fixtures/flashdrop.fixture.ts`): RUT de empresa **"77.492.120-K"** vs **"76.892.410-K"**; pedido **"#FD-0042"** con 2 ítems / **"$12.970"** vs 3 ítems / **"$10.526"** vs **"$13.970"**; cliente del mismo pedido **"Matías Valenzuela"** vs **"Carlos M."**; PIN **"5821"** vs **"4082"**; precios de "Papas Rústicas Trufadas" **"$3.490"** vs **"$4.490"** vs **"$2.990"**.
Requisito: ninguna pantalla hardcodea datos de maqueta; todas leen del fixture canónico.
Checks: test que falla si aparece un hex, un RUT o un código `#FD-` literal fuera del fixture.

### S17 — Lo que no puede aparecer en la UI

Lista cerrada: rating y reseñas, "envío gratis" y costo de envío en el marketplace, ETA por restaurante, cupón `#FLASH30` y campañas FlashHour, FlashPoints, PIN de entrega, chat repartidor↔cliente, POS e impresora térmica, boleta electrónica SII, propina, mapa GPS en vivo con velocidad, SLA de cocina y turno almuerzo, curva horaria, liquidación bancaria, canales y métodos de pago, margen por producto, serie 7d por producto, sugerencias de IA, alertas de stock.
Checks: grep sobre `apps/web/src` sin esos literales; revisión visual del diff.

---

## Tasks

| ID | Task | Specs | Route | Status | Commit / Evidencia |
|---|---|---|---|---|---|
| T1 | Bootstrap `apps/web`: Vite + React 19 + TS estricto + Tailwind v4, tokens del design system, router, context de sesión | S2, S4 | delegated (worker) | done | `75a64fb` — 29 tests verdes, build/tsc/lint OK |
| T2 | Cliente HTTP + manejo de errores: 401/403/409 con mensajes literales del backend, refresh de token, wrapper tipado de JSON | S7 | delegated (worker) | done | `2b77571` — 81 tests verdes (52 nuevos), build/tsc/lint OK |
| T3 | Shell canónico de 5 zonas + selector de 3 roles con navegación por rol y guard de sesión | S5 | delegated (worker) | done | `33ee2d5` — 9 tests de shell/guard |
| T4 | Login y registro: tabs, RUT/email, ClaveÚnica, módulo de destino preferente, panel de marca | S3, S5 | delegated (worker) | done | `33ee2d5` — 10 tests de login; 102 tests en total |
| T5 | Marketplace: hero, chips de categoría, grid de restaurantes, platos populares, sin features sin backend | S8 | delegated (worker) | done | `0f4868e` — 8 tests de marketplace |
| T6 | Carrito y checkout: estado local, `POST /api/orders`, contraste del total con el backend | S9 | delegated (worker) | done | `0f4868e` — 8 tests de carrito + 1 de código no inventado |
| T7 | Mis pedidos + seguimiento: stepper de 5 pasos, bitácora, resumen, mapa estático | S6, S10 | delegated (worker) | done | `0b7861c` — 19 tests de pedidos |
| T8 | Mi Tienda: tabla de catálogo, CRUD de producto, upload de imagen, toggle de disponibilidad con rollback | S11, S16 | delegated (worker) | done | `7ac5c2a` — 15 tests de catálogo |
| T9 | KDS: 3 columnas, timers, chime, atajos, drawer de detalle, transiciones por rol | S6, S12 | delegated (worker) | done | `40e4e52` — 24 tests de KDS |
| T10 | Consola repartidor: disponibles FIFO, claim atómico, rutas activas, historial | S13 | delegated | done | `030d864` — 12 tests de repartidor; 191 en total |
| T11 | Analíticas recortadas: 4 KPIs de `sales-summary` + top productos | S14 | inline | done | `606fcd7` — 7 tests de analíticas; 198 en total |
| T12 | Perfil multi-rol: identidad, 3 tarjetas de rol, dispositivos, `vehicle` read-only | S15 | inline | pending | `feat(web): add multi-role profile` |
| T13 | Fixture canónico único + datos de desarrollo derivados de él | S16 | inline | pending | `test(web): add canonical flashdrop fixture` |
| T14 | Barrido de features fuera de alcance + guard que falle si reaparecen | S17 | inline | pending | `test(web): guard against unsupported features` |
| T15 | Hardening: a11y (focus rings, contraste AA), responsive (4/8/12 columnas), E2E Playwright de los 3 flujos | S4, S5 | inline | pending | `test(web): add e2e flows and accessibility pass` |

**Orden:** T1 → T2 → T3 → T4 → T5 → T6 → T7 → T8 → T9/T10/T11 (independientes entre sí, superficies disjuntas) → T12 → T13 → T14 → T15.
**Forecast de líneas authored:** ~3.500–4.500 (excluye generados). Supera el umbral de ~400 → la estrategia elegida es `chain_strategy=feature-branch-chain` (elegida por el usuario el 2026-10-28): la rama tronco es `feat/web-app` (desde `main` @ `1cfd0ba`) y cada slice se abre como PR encadenado con squash-merge.

**Slice boundaries** (rama `feat/web-app`):

| Slice | PR | Contenido | Commit | Líneas authored |
|---|---|---|---|---|
| 1 | PR 1 | T1 — bootstrap + design tokens | `75a64fb` | 1.451 |
| 2 | PR 2 | T2 — cliente HTTP tipado + taxonomía de errores | `2b77571` | 2.080 |
| 3 | PR 3 | T3 + T4 — shell canónico + login | `33ee2d5` | 1.896 |
| 4 | PR 4 | T5 + T6 — marketplace + checkout | `0f4868e` | 2.062 |
| 5 | PR 5 | T7 — pedidos + seguimiento | `0b7861c` | 1.632 |
| 6 | PR 6 | T8 — catálogo de tienda | `7ac5c2a` | 1.607 |
| 7 | PR 7 | T9 — KDS | `40e4e52` | 1.997 |
| 8 | PR 8 | T12 + T13 + T14 + T15 — perfil, fixture, guard, a11y/E2E | — | ~500 |

El slice 2 quedó en 2.080 líneas (el forecast inicial de ~600 era para T2+T3+T4 juntos): T2 resultó ser una unidad más gruesa de lo previsto porque el transporte, el ciclo de refresh, la taxonomía de errores y la integración con la sesión son un solo bloque inseparable. Se partió el slice en vez de achicar la unidad.

---

## Log

**L1 (2026-10-28)** — "D:\desarrollo\2027\flashdrop_backend\references\stitch_flashdrop_ui_design_specs el diseñador web hizo estas maquetas para la app web del desarrollo. puedes elegir las que mejor se adapten segun tu criterio?"

**L2 (2026-10-28)** — "arma el web-app.md"

**L3 (2026-10-28)** — Respuestas del usuario a las dos decisiones de stack y alcance: "Vite + React 19 + TS + Tailwind v4" y "Solo frontend con backend actual".

**L4 (2026-10-28)** — Selección de maquetas (12 de 18) documentada en `docs/plans/2026-10-28-flashdrop-web-ui-mockup-selection.md`. Criterio: encaje con el contrato HTTP real > coherencia de shell > construibilidad sin servicios ausentes. Descartadas: `marketplace_y_cat_logo_de_clientes` (hero oscuro, carrito duplicado), `gesti_n_de_cat_logo_y_men_mi_tienda_v2.0` (columna derecha superpuesta sobre la tabla), `monitor_de_cocina_y_gestor_de_pedidos_mi_tienda_kds_v2.0` (features sin backend + texto partido), `consola_del_repartidor_flashdrop_courier_v2.0` (shell de "Mi Tienda" sobre cuerpo de repartidor), `seguimiento_de_pedido_en_vivo_fd_0042` (chat abierto sobre el mapa), `anal_ticas_de_ventas_y_rendimiento_mi_tienda_1` y `_2` (idénticos entre sí, shell vieja), `perfil_multi_rol_y_centro_de_cuentas_flashdrop_v2.0_2` (SII/POS/IP/telemetría inexistentes), `login_y_perfil_de_usuario_multi_rol_flashdrop` (es perfil, no login), `kinetic_courier_culinary` (design system duplicado).

**L5 (2026-10-28)** — Design system: se adopta `kinetic_delivery_system` (primary `#E84A0E`) como fuente única de tokens. De `kinetic_courier_culinary` se conserva solo la tipografía (`Plus Jakarta Sans` + `Inter`) y las reglas de `tabular-nums` / `label-code`, que no chocan.

**L6 (2026-10-28)** — Evidencia de backend verificada antes de escribir las specs:
- `orders-service`: `OrderStatus` = "Nuevo pedido" | "Preparando" | "Listo para retiro" | "Retirado" | "En camino" | "Entregado". `RoleTransitionPolicy`: RESTAURANTE → PREPARANDO, LISTO_PARA_RETIRO; REPARTIDOR → RETIRADO, ENTREGADO; CLIENTE → ninguna. `OrderStatus.EN_CAMINO` marcado legacy: ningún rol puede fijarlo; la fase "en camino" vive en `RouteStatus` de `delivery-service`.
- `catalog-service`: `RestaurantResponse` = id, name, address, phone, image (sin rating, sin ETA, sin deliveryFee). `Product` = categoryId, restaurantId, price, available. Ownership por JWT en `/api/catalog/my/products`.
- `orders-service`: `GET /api/orders/{restaurantId}/sales-summary?range=day|week|month` → totalOrders, totalRevenue, averageTicket, topProducts. `GET /api/orders/available-for-delivery` → code, restaurantName, pickupAddress, deliveryAddress, total, distanceKm, estimatedMinutes. `POST /api/orders` → address, payment_method, distance_km, estimated_minutes, items[].
- `auth-service`: `POST /auth/login`, `/auth/refresh`, `GET|PUT /auth/profile`, `GET /auth/validate`, JWKS.
- Plan `docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md`: D-1 mapa solo frontend (geocodificación cliente, sin coordenadas); D-5 `vehicle` no editable; D-6 `restaurantId` derivado del JWT.

**L7 (2026-10-28)** — Próximo paso: T1 (bootstrap) con TDD de los tokens antes de cualquier pantalla. Estrategia de entrega `ask-on-risk`: al superar ~400 líneas authored acumuladas se abre el menú de delivery (feature-branch-chain / stacked-to-main / single-pr).

**L8 (2026-10-28)** — T1 cerrado y commiteado. Evidencia: RED (4 suites sin implementación, exit 1) → GREEN (29 tests, exit 0); `pnpm build` exit 0; `tsc --noEmit` exit 0; `pnpm lint` exit 0; check de mutación (bajar el `--color-primary` a `#FF5A1F` y quitar el anillo de foco) rompe los tests, así que muerden. Commit `75a64fb` en `feat/web-app` (rama creada desde `main` @ `1cfd0ba`; la rama previa de trabajo `feat/auth-role-assignment` quedó intacta). Slice 1 = 1.451 líneas authored. El cliente HTTP quedó para T2 (S7), como ya advertía el handoff. El usuario eligió `chain_strategy=feature-branch-chain`: la rama tronco es `feat/web-app` y cada slice se abre como PR encadenado con squash-merge; los boundaries quedan en la tabla de slices.

**L9 (2026-10-28)** — Desviación registrada en S2: el check `pnpm --filter web build` no es ejecutable porque `apps/web` es un proyecto pnpm autónomo y el repo no tiene workspace raíz (crearlo está fuera del alcance autorizado). Se corrigió el check a `cd apps/web && pnpm build`. Nota operativa: `apps/web/pnpm-workspace.yaml` lo genera pnpm 11 (excepción de release-age para `react-router-dom@7.18.5`); se commitea para que los installs sean reproducibles. Las maquetas de `references/stitch_flashdrop_ui_design_specs/` siguen **sin trackear** (preexistente, decisión del usuario pendiente): un clone nuevo no las tiene, así que S3 queda referencia local hasta que se trackeen.

**L10 (2026-10-28)** — T2 cerrado y commiteado (`2b77571`). Evidencia: RED (3 suites sin implementación + 3 tests de sesión rojos) → GREEN (81 tests, exit 0; los 29 de T1 intactos); `pnpm build` / `tsc --noEmit` / `pnpm lint` exit 0; check de mutación (reemplazar el mensaje del backend por uno genérico → 23 tests rojos; quitar el reintento post-refresh → 2 rojos). Verificado por el orquestador: 81/81 tests, build y tsc verdes en ejecución propia.

**L11 (2026-10-28)** — **Corrección de S7.** La agrupación por status que yo había escrito era incorrecta; el worker la contrastó con los handlers y el orquestador lo verificó en el código. Realidad: `orders-service` mapea `OrderDomainException` **por texto** (`GlobalExceptionHandler.java:38-52`): `"no encontrado"`→404, `"perfil de repartidor"`→403, `"ya tienes"/"ya fueron tomados"/"alguien tomo"/"transicion de estado no permitida"`→409, resto→**400**. `AccessDeniedException`→403 en `orders-service` (`:90`) y →**401** en `delivery-service` (`GlobalExceptionHandler.java:145`). Consecuencia: `"Estado no valido: "`, `"El pedido debe contener productos de un mismo local"`, `"El pedido debe contener al menos un producto"`, `"Debe enviar al menos un campo para actualizar"` y `"No route found for orderId="` son **400**, no 409. Se reescribió **solo S7** con la tabla real. T2 queda done: su evidencia se midió contra la tabla corregida.

**L12 (2026-10-28)** — **Forma de respuesta no uniforme** (verificada): `orders-service` y `delivery-service` envuelven en `{success, message, data}`; `auth-service` y `catalog-service` devuelven el **DTO desnudo** (`AuthController.java:49` → `AuthResult`; `CategoryController.java:24` → `List<CategoryResponse>`; `MyStoreProductController.java:64` → `List<ProductResponse>`). El cliente declara la forma por endpoint. Queda anotado en S7 y aplica a T5, T8, T11 y T12 cuando tipen sus DTOs.

**L13 (2026-10-28)** — T3 y T4 cerrados y commiteados (`33ee2d5`). Evidencia: RED (21 tests rojos en 3 suites) → GREEN (102 tests en total, exit 0); `pnpm build` / `tsc --noEmit` / `pnpm lint` exit 0; check de mutación (switcher sin `navigate`, `roleOwningPath` siempre `null`, destino fijo a Cliente → 5 tests rojos). Verificado por el orquestador: 102/102, build/tsc/lint verdes, cero hex literales en `src/components` y `src/app`.

**L14 (2026-10-28)** — **Hallazgo de T4: el login con RUT no puede funcionar contra el backend actual.** La columna `login` de `credentials` guarda el **email** (`RegisterUserService` → `credentials.save(..., email)`), así que la UI normaliza el RUT a `16.482.915-K` y lo envía, pero `auth-service` responderá 401 **"Credenciales inválidas"** hasta que Soporte RUT exista. Se mantiene el campo RUT porque la maqueta y el PRD lo piden, y se registra como limitación conocida: **arreglarlo es backend** y queda fuera del alcance de este feature (S1). El backend sigue siendo la fuente de verdad.

**L15 (2026-10-28)** — Otras decisiones de T3/T4, registradas para que los siguientes slices no las re-litiguen:
- **Rutas nuevas**: el borrador apuntaba "Historial Despachos" y "Analíticas & Ventas" a `/tienda/analiticas` y los 3 ítems de Repartidor a `/repartidor`. Se agregaron `/tienda/despachos`, `/repartidor/pedidos` y `/repartidor/historial` para que cada ítem de navegación tenga destino propio.
- **"Recordar mi sesión"**: la preferencia vive en `localStorage` (`flashdrop.rememberSession.v1`); los tokens **no** salen de `sessionStorage` y mueren con la pestaña. Se sacó "por 30 días" del label porque no es cierto.
- **ClaveÚnica / Google / Apple**: sin backend, quedan inactivos. Un botón que promete un flujo inexistente es una mentira en la UI.
- **El switcher ofrece los 3 roles** aunque la cuenta no los tenga, como en la maqueta: el backend responde 403 → vista de rol incorrecto (S7).
- **Copy de maqueta eliminado a propósito** (S17): estrellas, cards flotantes de restaurante con precio/ETA, columna "Métodos de Pago", datos de ruta/latencia/batería, botón SOS 133, "MFA Activado". Se conservaron la propuesta de valor y los datos de confianza ("24 min", "98.4%", "3 Regiones Activas", TLS 1.3, Ley 19.628).

**L16 (2026-10-28)** — T5 y T6 cerrados y commiteados (`0f4868e`). Evidencia del worker: RED (2 suites con 1 fallo cada una tras la primera implementación + 3 fallos de la suite completa porque el marketplace ahora carga datos al montar) → GREEN (118 tests); check de mutación (total→0 y "★ 4.9" → 3 tests rojos). **Corrección del orquestador**: el worker había reimplementado en cliente el formato de `Order.code()` (`orderCode.ts`, `FD-%02d` sobre el least significant bits del UUID). Eso es lógica de negocio duplicada y podía divergir del backend. Se borró el módulo y la confirmación ahora lee el **código real** con `GET /api/orders/{id}` (`OrderDetailResponse.code`); si ese GET falla, muestra el id del pedido y el mensaje literal del backend, **sin inventar un código**. Test agregado para ese caso. Verificado por el orquestador: 119 tests verdes, build/tsc/lint exit 0, cero referencias a `deriveOrderCode`.

**L17 (2026-10-28)** — Ajustes de S9 y de la implementación de T5/T6:
- El caso del **carrito vacío** lo valida el cliente antes de la request (ver la nota en S9): un `items: []` cae en la rama legacy de un solo producto del controller y el backend respondería otro literal. Se aclaró la spec en vez de dejar el test manda sobre ella.
- El worker añadió **validación de forma** en la capa de API (`asList` en `src/api/catalog.ts`): si el catálogo devuelve algo que no es lista, la UI muestra aviso en vez de romper. No estaba en el encargo; se acepta porque sin eso el marketplace crashea con respuestas no-lista.
- El hero de la maqueta conserva su estructura pero sin el bloque de cupón (`#FLASH30`), y sus números son **datos reales del catálogo** (locales, platos, categorías) en vez de los "108 locales" de maqueta.
- Se quitó de la maqueta todo lo que no tiene backend: estrellas, ETA por restaurante, costo de envío, "envío gratis", cupón, WebPay/Transbank, FPS, FlashHour, banner de pedido en camino (es T7) y modal de personalización.

**L18 (2026-10-28)** — T7 cerrado y commiteado (`0b7861c`). Evidencia: RED (17 tests, las pantallas no existían) → GREEN (19 tests de pedidos; suite completa **138/138**, los 119 previos intactos); `pnpm build` / `tsc --noEmit` / `pnpm lint` exit 0; check de mutación (agregar "En camino" como 6º paso → 3 tests rojos; `roleOwningPath` con match exacto → 1 rojo; total calculado en cliente → 1 rojo). Verificado por el orquestador: 138/138, build/tsc/lint verdes.

**L19 (2026-10-28)** — **Hallazgo de T7: `route` y `delivery` llegan siempre `null`.** `OrderEnricher.enrich()` hace `order.setDeliveryInfo(null)` y `order.setRoute(null)` (`services/orders-service/.../OrderEnricher.java:52-53`), con el comentario de que se mantienen null hasta que exista el endpoint. Consecuencia: contra el backend actual **el mapa y la tarjeta del repartidor nunca se muestran**; la UI degrada sin inventar nada y hay test para las dos formas (con y sin ruta). Queda anotado para cuando exista ese endpoint: la pantalla ya está lista y solo necesita los datos. Además, `OrderDetailResponse.RouteDto` **no tiene `id`** (el brief lo decía): el DTO se tipó según el repo.

**L20 (2026-10-28)** — Decisiones de T7:
- **Guard de rol endurecido**: `roleOwningPath` es dueño también de las rutas que **continúan** una de sus entradas (`/pedidos/:id` → CLIENTE). Antes, un rol RESTAURANTE podía abrir el tracking de un pedido. Hay test.
- `GET /api/orders` se llama con `view=client` y **nunca** con `user_id` (la identidad sale del JWT). Hay test que lo fija.
- `Skeleton` se movió a `src/components/ui/` porque lo usan dos pantallas; quedó un re-export en `src/components/marketplace/Skeleton.tsx` que se puede borrar en T13/T14.
- **Pendiente para T14**: el shell conserva **"Ruta Activa & GPS"** y el panel de marca **"98.4% entregas a tiempo"** (preexistentes de T3/T4). El guard de S17 tiene que decidir si se quedan o se sacan: son copy de maqueta, no datos del backend.

**L21 (2026-10-28)** — T8 cerrado y commiteado (`7ac5c2a`). El primer worker **falló a mitad** y dejó los tests RED de T8 escritos (437 líneas en `storeCatalog.test.tsx` + el test de `FormData` en `apiClient.test.ts`); un segundo worker los tomó como especificación e implementó la aplicación. Evidencia: RED (15 tests rojos) → GREEN (**155/155**); `pnpm build` / `tsc --noEmit` / `pnpm lint` exit 0; check de mutación (toggle optimista, `available` viejo en el body, URL en vez de objectKey → 3 tests rojos). Verificado por el orquestador: 155/155, build/tsc/lint verdes.

**L22 (2026-10-28)** — **Correcciones de spec en S11** (verificadas contra el código):
- **No existe `PATCH`**: el toggle es un **PUT de reemplazo completo** (`OwnerUpdateProductRequest` con `categoryId`, `name`, `price` y `available` todos `@NotNull`). La spec decía PATCH.
- **La imagen es de dos pasos**: `POST /api/catalog/my/products/image` (parte `file`) → `{ objectKey, url }`, y el `image` del producto es el **objectKey**.
- **Sin ventas ni margen por producto**: no hay endpoint que los dé; las columnas de la maqueta quedan vacías.
- El literal **"Debe enviar al menos un campo para actualizar"** es del PUT **público**, no del del dueño: un body vacío en el PUT del dueño responde la validación de Spring.

**L23 (2026-10-28)** — **Asimetría de `image` en el backend**: el GET resuelve y devuelve la **URL pública** (`ProductImageUrlResolver`), pero el PUT exige el **objectKey** (`@Pattern`). La implementación manda `image` solo cuando se subió un archivo en la sesión y nunca reenvía la URL de la respuesta al backend (reventaría el `@Pattern`). Si alguien agrega "editar imagen" sin subir archivo nuevo, hay que convertir URL→objectKey.

**L24 (2026-10-28)** — Otras decisiones de T8:
- Se quitó de la maqueta lo que no tiene backend: columna "Ventas 7D", columna "Margen", badges "Top #1"/"Favorito" (el DTO no tiene tags ni ranking), "Pausar Menú" (no hay endpoint bulk), "Exportar CSV", banner "Catálogo v2.4 Activo / REST ID", paginación (el endpoint no pagina) y el footer de telemetría ("Impresora POS Conectada" — POS está prohibido por S17).
- Tres aserciones del test RED se acotaron a la fila (`within(row)`) porque el fixture tiene varios "Disponible" y "Agotado": la aserción quedó **más precisa**, no más débil.

**L25 (2026-10-28)** — T9 cerrado y commiteado (`40e4e52`). Evidencia: RED (módulo inexistente) → GREEN (23 tests de KDS; suite completa 178/178); `pnpm build` / `tsc --noEmit` / `pnpm lint` exit 0; check de mutación (quitar el check de rol y mandar el PUT en mayúsculas → 5 tests rojos; apuntar la columna EN COCINA a "Nuevo pedido" y timer fijo → 5 rojos). **Corrección del orquestador**: el board **no tenía forma de recargar** (solo releía la lista tras cambiar un estado), así que un pedido nuevo de un cliente nunca llegaba a la pantalla y el chime no tenía nada que anunciar. Se agregó el botón **"Actualizar"** con su test. Verificado por el orquestador: 179/179, build/tsc/lint verdes.

**L26 (2026-10-28)** — Decisiones de T9:
- **Carga de los ítems por tarjeta, con caché por board**: `OrderListResponse` no trae platos, así que la tarjeta lee `GET /api/orders/{id}` una vez y el drawer reutiliza esa respuesta (0 requests extra, con test). Se descartó el bajo demanda puro porque obligaría a abrir cada pedido para saber qué cocinar.
- **El backend permite `NUEVO_PEDIDO → LISTO_PARA_RETIRO` y la UI no lo expone**: la tabla de transiciones de la UI es el camino lineal (Aceptar → Preparando → Listo para retiro). Queda anotado por si la cocina lo necesita.
- **El board no hace polling**: descubre pedidos nuevos cuando relee la lista (al cargar o al pulsar "Actualizar"). Detección en vivo exigiría un refresh periódico; quedó como decisión del orquestador no hacerlo por ahora.
- Los pedidos con estado fuera de las tres columnas ("Retirado", "Entregado", el legacy "En camino") **no aparecen** en el board: no hay columna que los contenga.
- Se quitó de la maqueta lo que no tiene backend: "Prep Promedio", "En Meta Tiempo"/SLA, filtros "Flash Delivery"/"Retiro Local", "Pausar Cocina", badge "Urgente", "Chef asignado", "ENTREGA A REPARTIDOR" (el restaurante no puede fijar Retirado/Entregado), "ESTANTE"/PIN, bloque de courier con ETA, y "ALERTA ALÉRGENOS & NOTAS" (**orders-service no tiene campo de notas ni alérgenos**: la única nota real es la descripción del ítem, que sí se muestra).

**L27 (2026-10-28)** — T10 cerrado y commiteado (`030d864`). Evidencia: 12 tests de consola de repartidor en `courier.test.tsx`, suite completa en **191/191** tests verdes; `pnpm build` / `tsc --noEmit` / `pnpm lint` exit 0 (0 errores, 0 warnings). Corrección del orquestador: se sincronizó el estado de carga entre el catálogo de restaurantes y la cola de órdenes en `CourierAvailablePage` eliminando el parpadeo de estado vacío, y se eliminó la variable no utilizada `COURIER_VEHICLES` en `CourierProfileForm`. Se omitieron según S13/S17 todos los elementos sin backend (GPS en vivo, PIN, chat, propinas).

**L28 (2026-10-28)** — T11 cerrado y commiteado (`606fcd7`). Evidencia: RED (6 fallos por ruta placeholder) → GREEN (7 tests en `storeAnalytics.test.tsx`, suite completa en **198/198** tests verdes); `pnpm build` / `tsc --noEmit` / `pnpm lint` exit 0 (0 errores, 0 warnings). La pantalla consume `GET /api/orders/restaurants/{id}/sales-summary?range=day|week|month`, resuelve el `restaurantId` mediante `storeCatalogApi.listMyProducts()` (o fallback de catálogo), renderiza los 4 KPIs canónicos y la tabla de top 5 productos, maneja rangos `day/week/month` y el error literal de rango inválido (400) o de permiso/ownership (403). Quedaron rigurosamente excluidos todos los ítems prohibidos por S14/S17 (curvas horarias, series diarias, márgenes, liquidación, sugerencias de IA, alertas de stock y satisfacción/rating). Se ajustó además la aserción de llamada en `login.test.tsx` ante la navegación al home de repartidor.


