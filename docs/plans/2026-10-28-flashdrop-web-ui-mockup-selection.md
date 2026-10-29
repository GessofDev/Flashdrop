# Selección de maquetas UI para la app web FlashDrop

**Fecha:** 2026-10-28
**Estado:** Decisión de diseño. Referencia para la implementación de `apps/web` (hoy vacío).
**Insumo:** `references/stitch_flashdrop_ui_design_specs/` (18 maquetas Stitch + 2 documentos de design system)
**Contraste:** contrato HTTP real de `auth-service`, `catalog-service`, `orders-service`, `delivery-service`
y plan `docs/plans/2026-09-22-flashdrop-delivery-and-store-features-design.md`.

---

## 1. Criterio de selección

Una maqueta entra al conjunto de referencia si cumple tres condiciones:

1. **Encaje con el contrato real.** Cada dato y cada acción que la pantalla muestra se puede
   obtener de un endpoint existente, o se puede derivar en cliente sin inventar un endpoint.
2. **Coherencia de sistema.** Comparte el shell canónico (top bar de 5 zonas + selector de rol
   de 3 segmentos) y los tokens del design system elegido.
3. **Construible.** No depende de hardware o servicios ausentes (POS fiscal, telemetría GPS,
   chat, pago real) sin marcar explícitamente como fase 2.

Cuando existen dos versiones de la misma pantalla, gana la que **mapea estados y acciones sobre
los endpoints reales**, no la que tiene más features.

---

## 2. Decisión por pantalla

| Pantalla | Elegida | Descartada | Razón de la elección |
|---|---|---|---|
| Shell / navegación | `flashdrop_shell_can_nica_gu_a_de_navegaci_n_por_rol` | — | Es el contrato de navegación: 5 zonas, role switcher, jerarquía de targets. Todo lo demás se monta sobre este shell. |
| Design system | `flashdrop_design_system_v2.0_gu_a_de_tokens_kit_de_componentes` + `kinetic_delivery_system/DESIGN.md` | `kinetic_courier_culinary/DESIGN.md` | Un solo origen de verdad. Ver §3. |
| Login | `iniciar_sesi_n_portal_unificado_flashdrop` | `login_y_perfil_de_usuario_multi_rol_flashdrop` (no es login, es perfil) | Split panel con panel de marca, tabs y **"Módulo de destino preferente"** (Cliente/Mi Tienda/Repartidor): es el selector de rol real, no un adorno. RUT + ClaveÚnica. |
| Perfil multi-rol | `perfil_multi_rol_y_centro_de_cuentas_flashdrop_v2.0_1` | `..._v2.0_2` | _1 es legible y refleja el modelo real (roles por usuario + perfil + sesiones activas). _2 mete SII, POS, IP y telemetría que no existen y ensucia la lectura. Se le roba a _2 solo la nomenclatura de los 3 planos (Consumidor / Comercio & KDS / Flota & Logística). |
| Marketplace cliente | `marketplace_y_cat_logo_de_clientes_flashdrop_v2.0` | `marketplace_y_cat_logo_de_clientes` | v2 tiene hero limpio, chips de categoría, grid de restaurantes legible, carrito mejor estructurado y footer consistente. v1 tiene un hero oscuro que se come el fold y un carrito duplicado/redundante abajo a la derecha. |
| Catálogo Mi Tienda | `gesti_n_de_cat_logo_y_men_mi_tienda` | `gesti_n_de_cat_logo_y_men_mi_tienda_v2.0` | v1 es una tabla densa con SKU, tags, precio+margen, ventas, toggle de disponibilidad inline y acciones por fila: mapea 1:1 a `/api/catalog/my/products` + PATCH de disponibilidad. v2.0 tiene **defecto de layout**: la columna derecha (RUT/Turno/footer) se superpone sobre la tabla y tapa la última fila; además el breadcrumb "PANEL OPERATIVO B28" no pertenece a la shell. |
| Monitor de cocina (KDS) | `monitor_de_cocina_y_gestor_de_pedidos_mi_tienda` | `monitor_de_cocina_y_gestor_de_pedidos_mi_tienda_kds_v2.0` | El v1 tiene **3 columnas = 3 estados reales** del restaurante (`NUEVO_PEDIDO` → `PREPARANDO` → `LISTO_PARA_RETIRO`), tal como define `RoleTransitionPolicy`. El KDS v2.0 agrega PIN de retiro, boleta electrónica, SLA y turno de almuerzo (sin backend) y se rompe visualmente (títulos partidos en dos líneas, productos con tachado, desbordes). |
| Consola repartidor | `consola_del_repartidor_flashdrop_courier` | `consola_del_repartidor_flashdrop_courier_v2.0` | El v2.0 tiene un **shell mal compuesto**: muestra "Mi Tienda" activo con navegación de tienda (KDS / Catálogo / Analíticas) encima de una consola de repartidor. El v1 sí es coherente y su cuerpo mapea 1:1 con `available-for-delivery` + `/claim` + `/routes`. |
| Seguimiento cliente | `seguimiento_de_pedido_en_vivo_fd_0042_flashdrop_v2.0` | `seguimiento_de_pedido_en_vivo_fd_0042` | v2 separa el chat en botón (v1 lo deja abierto sobre el mapa) y suma bitácora de trazabilidad y resumen de pedido más limpio. Ambos necesitan la corrección de estados de §4. |
| Analíticas tienda | `anal_ticas_de_ventas_y_rendimiento_mi_tienda_v2.0` | `anal_ticas_..._1` / `_2` (idénticos entre sí, sin PNG el _1) | Usa la shell canónica v2 (la _1 usa la shell vieja "FlashDrop Kitchen Operations"). Su curva horaria con picos es la lectura que realmente decide staffing. Hay que recortar a lo que devuelve `sales-summary` (§5). |

> Nota: `anal_ticas_de_ventas_y_rendimiento_mi_tienda_1/code.html` y `_2/code.html` son el mismo
> archivo (53.381 bytes, diff vacío). No son dos alternativas.

---

## 3. Sistema de diseño: una sola fuente de verdad

Hay **dos** documentos de design system en conflicto:

| | `kinetic_courier_culinary` | `kinetic_delivery_system` |
|---|---|---|
| Primary | `#FF5A1F` | `#E84A0E` (core) + `#FF5A1F` (hover) |
| Canvas | `#f8f9ff` | `#faf8ff` / `#F8FAFC` |
| Max width | 1280px | 1440px |
| Grid | 12 col, 2.5rem margin | 12 col, 2rem margin, rail 240-280px |
| Badge radius | 9999px (pill) | 6-8px |
| Modales | `rounded-xl` 24px | `rounded-3xl` 24px |
| Foco | — | `outline: 2px solid #E84A0E` |

**Se adopta `kinetic_delivery_system`**:
- Cubre los tres roles en un solo producto (el otro también, pero con naming de courier-first).
- Sus tokens semánticos de 4 niveles (success/info/warning/destructive) coinciden con el badge kit
  del "FlashDrop Design System v2.0" que acompaña las maquetas v2.
- El grid de 1440px + rail fijo + cola de telemetría ya está dibujado en la Shell Canónica.
- Define focus ring explícito (requisito de accesibilidad que el otro no fija).

El `kinetic_courier_culinary` queda como fuente secundaria: **tipografía** (`Plus Jakarta Sans` para
headers + `Inter` para datos) y **reglas de estado** (`label-code` para `#FD-XXXX`, tabular-nums
para CLP) se conservan, porque son mejores y no chocan con Kinetic Delivery System.

**Regla dura:** ninguna pantalla nueva arranca con un shell distinto al canónico. Las pantallas
elegidas cuyo HTML usa la shell vieja (KDS v1, catálogo v1, analíticas v1) se portan al shell
canónico conservando su contenido y sus estados.

---

## 4. Estados: qué muestra la máquina real

`orders-service` define 6 estados y `RoleTransitionPolicy` define quién puede fijar cada uno:

| Estado | Puede fijarlo | Pantalla que lo muestra |
|---|---|---|
| `NUEVO_PEDIDO` | (creación) | KDS → "POR ACEPTAR" |
| `PREPARANDO` | RESTAURANTE | KDS → "EN COCINA" |
| `LISTO_PARA_RETIRO` | RESTAURANTE | KDS → "LISTO / RETIRO" |
| `RETIRADO` | REPARTIDOR | Tracking → "Retirado" |
| `EN_CAMINO` | **legacy, nadie lo puede fijar** | ⚠️ ver abajo |
| `ENTREGADO` | REPARTIDOR | Tracking → "Entregado" |

`EN_CAMINO` existe como **`RouteStatus` en `delivery-service`**
(`PENDIENTE → ASSIGNED → RETIRAR_PEDIDO → EN_CAMINO → ENTREGADO`), no como estado de pedido
asignable. Consecuencia para el frontend:

- El **stepper del cliente** debe ser: Confirmado → En cocina → Listo para retiro → Retirado →
  Entregado (5 pasos, **sin "En camino"**). La fase "en camino" se deriva del `route.status`
  cuando viene disponible, no del `order.status`.
- El **KDS** tiene exactamente 3 columnas porque el restaurante solo puede fijar 2 estados.

---

## 5. Qué recortar: features de maqueta sin backend

Nada de esto se puede construir hoy. La columna "origen" dice de qué endpoint sale ladata real.

| Feature de maqueta | Pantalla | Estado real | Qué hacer |
|---|---|---|---|
| Rating estrellas (4.9 ★, 1.512 reseñas) | Marketplace | `RestaurantResponse` = id, name, address, phone, image | **Fuera.** No hay reseñas ni rating. |
| Costo de envío / "Envío gratis" | Marketplace | `GET /catalog/restaurants` sin filtros; el fee vive en el pedido | Mostrar solo después de crear el pedido (`OrderListResponse.deliveryFee`). |
| ETA por restaurante (20-30 min) | Marketplace | No hay endpoint de ETA por restaurante | **Fuera** o derivar de `estimatedMinutes` del pedido. |
| Cupón `#FLASH30` / FlashHour | Marketplace | No existe | **Fuera.** El checkout es "método de pago declarativo" (`CreateOrderRequest.paymentMethod`). |
| FlashPoints / loyalty | Perfil | No existe | **Fuera.** |
| Mapa GPS en vivo, velocidad, GPS activo | Courier, Tracking | El plan D-1 define mapa **solo frontend**: se exponen `pickupAddress` y `deliveryAddress`, la geocodificación es cliente | Render estático/estimado con direcciones. **No** hay coordenadas ni telemetría. |
| PIN de entrega (5821 / 4082) | Courier, Tracking, KDS | No existe | **Fuera.** El repartidor confirma con `PATCH /api/delivery/order/{orderId}/status`. |
| Chat repartidor ↔ cliente | Tracking | No existe | **Fuera.** |
| POS / impresora térmica / boleta electrónica SII | KDS, Courier, Tracking | No existe | **Fuera.** |
| Pro-tip / propina | Courier | No existe | **Fuera.** |
| SLA cocina, turno almuerzo, alertas de stock, IA de optimización, liquidación bancaria, canales de pago | Analíticas | `sales-summary` devuelve solo `totalOrders`, `totalRevenue`, `averageTicket`, `topProducts(name, quantitySold, revenue)` | **Fase 2.** Requeriría un endpoint agregado nuevo (curva horaria, settlement). |
| Margen y tendencia 7d **por producto** | Catálogo | `topProducts` no trae margen ni serie temporal | **Fuera** o mostrar `quantitySold` + `revenue` y nada más. |
| Importes en USD ("$ · $8.000") | Marketplace, Perfil | El sistema es CLP | Solo CLP con `tabular-nums`. |

Lo que **sí** es frontend-only y se puede construir sin tocar backend: timers de elapsed time
(desde `createdAt`), chime de cocina, atajos de teclado en KDS, calculadora de propinas, mapa con
geocodificación cliente, y el switcher de rol.

---

## 6. Inconsistencias entre maquetas (hay que fijar un único fixture)

Si estas maquetas pasan a ser la referencia de implementación, el frontend va a heredar datos que
se contradicen. Hay que corregir antes de construir:

- **RUT de empresa:** `77.492.120-K` (KDS, analíticas v2.0, perfil _2) vs `76.892.410-K`
  (analíticas v1, tracking v2.0, perfil _1).
- **Pedido `#FD-0042`:** 2 ítems / $12.970 (marketplace v2) vs 3 ítems / $10.526 (tracking v2.0) vs
  $13.970 (tracking v1).
- **Cliente del `#FD-0042`:** "Matías Valenzuela" (KDS) vs "Carlos M." (courier).
- **PIN de entrega:** 5821 (tracking v2.0) vs 4082 (courier v1) vs "4 dígitos" (courier v2.0).
- **Códigos de pedido en KDS:** `#FD-0042` con `#FD-0040`, `#FD-0041`, `#FD-0044`… y en courier
  `#FD-0049`, `#FD-0050`, `#FD-0051`. Ninguna serie es consistente.
- **Precios:** "Papas Rústicas Trufadas" a $3.490 (marketplace) vs $4.490 (catálogo) vs $2.990
  (tracking v2.0).

Recomendación: definir **un fixture canónico** en el repo (por ejemplo
`apps/web/src/test/fixtures/flashdrop.fixture.ts`) y derivar de él cualquier captura o demo.

---

## 7. Orden de construcción sugerido

Cada slice es un flujo completo de punta a punta, en el orden en que el backend ya lo soporta:

1. **Shell + login + selector de rol** — `auth-service` (`login`, `refresh`, `profile`, roles).
   Es la base de todo lo demás.
2. **Marketplace + carrito + checkout** — `catalog-service` (`restaurants`, `products`,
   `categories`) + `POST /api/orders`. Cierra el flujo de cliente.
3. **Mis pedidos + seguimiento** — `GET /api/orders`, `GET /api/orders/{id}`.
4. **Catálogo Mi Tienda + KDS** — `/api/catalog/my/products` + `PUT /api/orders/{id}/status`.
5. **Consola repartidor** — `available-for-delivery` + `/claim` + `/routes`.
6. **Analíticas de tienda** — `sales-summary` (recortado a los 4 KPIs reales).
7. **Perfil multi-rol** — `GET/PUT /auth/profile` + roles.

---

## 8. Follow-up de backend (si se quiere completar las maquetas)

Ordenado por valor, estos son los endpoints que faltan y que desbloquean las features recortadas:

1. `GET /api/orders/restaurants/{id}/sales-summary?range=hour` o una serie diaria → curva horaria.
2. Rating y reseñas por restaurante → marketplace.
3. GPS del repartidor (posiciones + timestamp) → mapa en vivo en courier y tracking.
4. PIN de entrega generado por el pedido → validación de entrega por repartidor.
5. Endpoint de checkout con cálculo de `deliveryFee` y `estimatedMinutes` (hoy los declara el cliente).

Ninguno es bloqueante para empezar: el 80% de las pantallas seleccionadas se construye con lo que
ya existe.
