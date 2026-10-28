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

### S10 — Seguimiento del pedido

Requisito: pantalla `seguimiento_de_pedido_en_vivo_fd_0042_flashdrop_v2.0`: stepper de 5 pasos, bitácora de trazabilidad, resumen del pedido con `subtotal` / `deliveryFee` / `total`, y **"Garantía Térmica y de Tiempo"**. El chat queda como botón, no como panel abierto sobre el mapa. El mapa es **estático**: el backend expone `pickupAddress` y `deliveryAddress` (plan D-1: geocodificación client-side), no hay coordenadas ni telemetría.
Checks: no hay componente de chat, ni campo de PIN, ni lectura de coordenadas; test del stepper con los estados reales.

### S11 — Mi Tienda: catálogo, disponibilidad e imágenes

Endpoints: `GET /api/catalog/my/products`, `POST /api/catalog/my/products`, `PUT /api/catalog/my/products/{productId}`, `PATCH /api/catalog/my/products/{productId}`, upload multipart de imagen, `GET /catalog/categories`.
Requisito: la tabla de `gesti_n_de_cat_logo_y_men_mi_tienda` (SKU, tags, precio, ventas, toggle de disponibilidad inline, acciones editar/duplicar/eliminar). El toggle llama al backend y **no** es optimista sin rollback: el estado visual lo define la respuesta. `restaurantId` se deriva del JWT, no del body.
Checks: test del toggle — la UI refleja la respuesta del PATCH (incluido 4xx sin cambio visual); test de subida de imagen con Content-Type incorrecto → muestra **"El Content-Type no coincide con el contenido de la imagen"**; test de update vacío → **"Debe enviar al menos un campo para actualizar"**.

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
| T3 | Shell canónico de 5 zonas + selector de 3 roles con navegación por rol y guard de sesión | S5 | inline | pending | `feat(web): add canonical shell with role switcher` |
| T4 | Login y registro: tabs, RUT/email, ClaveÚnica, módulo de destino preferente, panel de marca | S3, S5 | inline | pending | `feat(web): add unified login with role destination` |
| T5 | Marketplace: hero, chips de categoría, grid de restaurantes, platos populares, sin features sin backend | S8 | inline | pending | `feat(web): add customer marketplace` |
| T6 | Carrito y checkout: estado local, `POST /api/orders`, contraste del total con el backend | S9 | inline | pending | `feat(web): add cart and declarative checkout` |
| T7 | Mis pedidos + seguimiento: stepper de 5 pasos, bitácora, resumen, mapa estático | S6, S10 | inline | pending | `feat(web): add order list and live tracking` |
| T8 | Mi Tienda: tabla de catálogo, CRUD de producto, upload de imagen, toggle de disponibilidad con rollback | S11, S16 | inline | pending | `feat(web): add store catalog management` |
| T9 | KDS: 3 columnas, timers, chime, atajos, drawer de detalle, transiciones por rol | S6, S12 | delegated | pending | `feat(web): add kitchen display board` |
| T10 | Consola repartidor: disponibles FIFO, claim atómico, rutas activas, historial | S13 | delegated | pending | `feat(web): add courier console` |
| T11 | Analíticas recortadas: 4 KPIs de `sales-summary` + top productos | S14 | delegated | pending | `feat(web): add store sales analytics` |
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
| 3 | PR 3 | T3 + T4 — shell canónico + login | — | ~600 |
| 4 | PR 4 | T5 + T6 — marketplace + checkout | — | ~700 |
| 5 | PR 5 | T7 — pedidos + seguimiento | — | ~350 |
| 6 | PR 6 | T8 + T9 — catálogo de tienda + KDS | — | ~900 |
| 7 | PR 7 | T10 + T11 — repartidor + analíticas | — | ~700 |
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
