# Feature: merchant-multi-local — una marca (merchant) con N locales

> Estado: **completado** (WU-1..WU-10 implementados y verificados; WU-10 listo para commit).
> Base: `main` @ `553aaf3`.
> Rama de trabajo: `feat/merchant-multi-local`.
> Change folder: `openspec/changes/merchant-multi-local/` (`proposal.md`,
> `design.md`, `spec.md` con FR-1..FR-16, `tasks.md` con T1..T10).

## Specs

### S1 (verbatim, usuario)

> neesitgo que revises, que pasa ctualmemnte cuando un cliente (tienda) tiene mas de un local o restaurante. como se maneja?

### S2 (verbatim, usuario)

> si, dale con el 1

Referencia: la opción 1 es **un grupo/merchant con N locales** (la alternativa
descartada era el modelo barato N:M usuario↔restaurante, quitando el `UNIQUE`
sin entidad de marca).

### S3 (verbatim, usuario)

> Q1-marca+sucursal. Q2- no, no se compartenb catalogos. Q5-si

### S4 (verbatim, usuario)

> Q3-solo el dueño. Q4-si Q6-si, pero se debe crear si se agregan mas locales

### S5-S15 (decididas con el usuario)

> Reglas confirmadas en la sesión de diseño. Si el usuario las cambia, se
> reescribe la S# y se reabre la tarea afectada. Los identificadores `D#`
> remiten a la tabla de decisiones de `openspec/changes/merchant-multi-local/proposal.md`.

- **S5** (D6) El marketplace muestra **"Marca — Sucursal"**. `RestaurantResponse`
  suma `merchantName`; el `name` actual del restaurante pasa a ser el nombre de
  la sucursal. Sin migración de datos.
- **S6** (D7) Los catálogos **no se comparten** entre locales de la misma
  marca: cada local tiene su propia carta, con sus precios y su disponibilidad.
- **S7** (D8) El resumen de ventas existe **por local y agregado de marca**:
  `restaurantId` opcional; ausente ⇒ agregado de todos los locales del merchant.
- **S8** (D9) **Sólo el `DUEÑO`** crea locales. Un `ENCARGADO` recibe `403`.
- **S9** (D10) El snapshot de pickup **suma `merchant_id`**: `delivery_routes`
  recibe la columna (migración `V11` de delivery-service).
- **S10** (D11) Un local sin marca se permite **sólo si no tiene dueño**. Al
  asignarle dueño, o al agregarle locales hermanos, la marca tiene que existir:
  al crear el segundo local, el merchant se crea automáticamente si el usuario
  todavía no tenía uno.
  **Invariante**: todo restaurante con `user_id` no nulo tiene `merchant_id` no nulo.
- **S11** (D3) El contexto de local viaja en el header
  **`X-My-Store-Restaurant`**, no en el JWT. Sin header y con un solo local, ese
  local; sin header y con varios, `400 LOCAL_CONTEXT_REQUIRED`.
- **S12** (D2) La pertenencia es **N:M entre `merchant` y usuario**
  (`merchant_members`, con rol `DUEÑO` | `ENCARGADO`). `restaurant.user_id`
  queda como dueño legacy y se deprecia en la fase 4.
- **S13** El `UNIQUE(user_id)` se elimina **sólo en la fase 3**, después del
  dual-write verificado. Es el único punto irreversible del change.
- **S14** El flujo de pedido del cliente **no cambia**: `POST /api/orders` sigue
  sin mandar `restaurantId` (se deriva de los productos).
- **S15** La pertenencia se valida **contra `catalog-service`**, nunca contra un
  claim del cliente: un token de un merchant no habilita locales ajenos.

## Design

Fuente única: `openspec/changes/merchant-multi-local/design.md`. Resumen operativo.

### 1. Vista de arquitectura

```
apps/web (React) / apps Flutter (tienda)
   │  GET  /api/catalog/my/restaurants          → locales del merchant
   │  GET  /api/catalog/my/restaurant           → local activo (compat)
   │  X-My-Store-Restaurant: <restaurantId>     → contexto de local por request
   ▼
gateway (Fastify) ── /api/catalog ──►  catalog-service
                                          │  owns merchants, merchant_members,
                                          │       restaurant, products, hours
                                          ▼
   orders-service  ── GET /api/internal/owner/restaurants?userId=  (X-Internal-Api-Key)
   reviews-service ── GET /api/internal/owner/restaurants?userId=  (X-Internal-Api-Key)
   delivery-service ── merchant_id en el snapshot de pickup (columna propia)
```

`merchant` **no** es un servicio nuevo: es una entidad nueva dentro de
`catalog-service`, que ya es el dueño del dominio de comercio (AGENTS.md §2).

### 2. Modelo de datos

- `merchants` (id, name, legal_name, tax_id, phone, email, status). `name` **no**
  es `unique`: dos marcas homónimas son legales.
- `merchant_members` (merchant_id, user_id, role) con `UNIQUE (merchant_id, user_id)`.
  Sin FK a `auth.users`: referencia lógica, igual que `restaurant.user_id` hoy.
- `restaurant.merchant_id` nullable, FK `on delete restrict` (borrar una marca no
  borra locales con historial de pedidos).

### 3. Migraciones

| Migración | Servicio | Contenido | Reversible |
|---|---|---|---|
| `V11__create_merchants.sql` | catalog | `merchants` + `merchant_members` | Sí |
| `V12__add_restaurant_merchant_id.sql` | catalog | `restaurant.merchant_id` nullable + índice | Sí |
| `V13__backfill_merchant.sql` | catalog | Backfill 1:1 | Sí |
| `V14__drop_restaurant_user_id_unique.sql` | catalog | Drop del `UNIQUE(user_id)` + retiro de los guards `OwnerAlreadyAssigned` | **No** (fase 3) |
| `V11__add_route_merchant_id.sql` | delivery | `delivery_routes.merchant_id` | Sí |

El backfill es 1:1 con el estado actual: un usuario con un restaurante produce
un merchant con un local. Sin pérdida de información y sin cambio observable.

**Queries de control obligatorios antes de `V14`** (cero filas esperadas):

```sql
select user_id from public.restaurant
where user_id is not null
group by user_id having count(*) > 1;

select count(*) from public.restaurant
where user_id is not null and merchant_id is null;
```

### 4. El punto que más duele: el contrato interno

`GET /api/internal/restaurants?userId=` devuelve **un** objeto y lo consumen
`orders-service` (`CatalogHttpClientAdapter.java:94-118`, 5 use cases) y
`reviews-service` (`HttpRestaurantLookupClientAdapter.java:37-47`). Estrategia
**aditiva primero**:

| Endpoint | Estado |
|---|---|
| `GET /api/internal/restaurants?userId=` | Se mantiene (un objeto, 404) hasta la fase 3 |
| `GET /api/internal/owner/restaurants?userId=` | Nuevo: lista de locales operables |
| `GET /api/internal/owner/restaurants/{id}/membership?userId=` | Nuevo: 204 / 404 |

Y las comparaciones de igualdad pasan a consultas de pertenencia:

| Consumidor | Hoy | Pasará a |
|---|---|---|
| `orders-service` `UpdateOrderStatusUseCase.java:164` | igualdad con el único local | `membership(...)` |
| `orders-service` `GetOrderDetailUseCase.java:78` | igualdad | `membership(...)` |
| `orders-service` `ListOrdersUseCase.java:43` | un solo `restaurantId` | local resuelto o todos |
| `orders-service` `GetRestaurantSalesSummaryUseCase.java:55` | un solo `restaurantId` | `restaurantId` opcional (S7) |
| `orders-service` `GetAccountDeletionBlockersUseCase.java:38` | un solo local | **todos** los locales |
| `reviews-service` `CreateReviewUseCaseImpl.java:181-185` | igualdad | `membership(...)` |

### 5. Lo que NO se toca

`orders.restaurant_id`, `order_policies` (una por local), `restaurant_hours`,
`delivery_radius_km`, `reviews.target_id`, el flujo de pedido del cliente
(S14) y `auth-service` (los roles siguen siendo de plataforma).

## Work units (cada uno cierra con commit + tests + docs)

- **WU-1** — T1: migraciones V11-V13 + backfill + test de migración (`7dec230`). *Riesgo bajo.*
- **WU-2** — T2: `StoreContextResolver`, `merchant_members`, `GET my/restaurants`,
  `GET my/merchant`, `POST my/restaurants`, `RestaurantResponse.merchantName`
  (`StoreContextResolverTest` 8/8 y `MyStoreMerchantControllerIT` 7/7 verdes).
  *Riesgo medio.*
- **WU-3** — T3: los use cases de dueño operados sobre el local resuelto
  (`GetRestaurantByUserIdUseCase` conservado para compatibilidad interna de
  `InternalCatalogController`; 38 suites / 199 tests verdes). *Riesgo medio.*
- **WU-4** — T4: `owner/restaurants` + `membership` + `merchantId` en
  `InternalRestaurantResponse` (203/203 tests verdes). *Riesgo bajo.*
- **WU-5** — T5: `orders-service` a pertenencia (IDOR, scopes, ventas, borrado de
  cuenta) + `merchantId` en el snapshot de pickup (`e80ae17`). *Riesgo **alto** — commit propio.*
- **WU-6** — T6: `delivery-service` — `merchant_id` en `delivery_routes`
  (`V11__add_route_merchant_id.sql`, `DeliveryMigrationIT` 3/3 y
  `JpaRouteRepositoryIT` 16/16 verdes). *Riesgo medio.*
- **WU-7** — T7: `reviews-service` a pertenencia (`CreateReviewUseCaseImplTest`
  y `HttpRestaurantLookupClientAdapterContractTest`, 98/98 verdes). *Riesgo bajo.*
- **WU-8** — T8: selector de local en el panel web + CORS `X-My-Store-Restaurant`
  en gateway (`apps/web` typecheck + 352 tests y `gateway` 355 unit tests
  verdes). *Riesgo bajo.*
- **WU-9** — T9: `V14` (drop del `UNIQUE`) + retiro de los guards
  `OwnerAlreadyAssigned` (`e70b8d4`). *Riesgo **alto** — commit propio, único punto irreversible.*
- **WU-10** — T10: fase 4, deprecar `restaurant.user_id` (`V15__drop_restaurant_user_id.sql`,
  `EraseRestaurantOwnerUseCase` sobre `merchant_members`, 39 suites / 208 tests verdes). *Riesgo medio.*

WU-5 y WU-9 van sueltos, cada uno en su propio commit, para que la revisión
nativa los tome por separado: WU-5 toca autorización (un error ahí es un IDOR)
y WU-9 es irreversible.

Detalle de archivos, verificación y mensaje de commit por tarea:
`openspec/changes/merchant-multi-local/tasks.md`.

## Log

- **L1** (usuario, verbatim): "neesitgo que revises, que pasa ctualmemnte cuando un cliente (tienda) tiene mas de un local o restaurante. como se maneja?"
- **L2** (hallazgo): el soporte multi-local **no existe**. El bloqueo está en
  cuatro capas: `UNIQUE(user_id)` en `public.restaurant`
  (`V1__create_schema.sql:11`, conservado explícitamente por
  `V6__restaurant_owner_nullable.sql`); guards
  `OwnerAlreadyAssignedException("El usuario ya tiene asignado otro comercio")`
  en `CreateRestaurantUseCase.java:37-39` y
  `AssignRestaurantOwnerUseCase.java:27-31`; queries que devuelven
  `Optional<RestaurantEntity>`; y el contrato interno que devuelve un objeto.
- **L3** (hallazgo): no existe ningún concepto de sucursal, marca, cadena ni
  grupo en el monorepo. Los roles son N:M (`user_has_roles`) y `Restaurante` es
  el único rol de negocio: no hay "gerente de sucursal" que modelar.
- **L4** (hallazgo): `delivery-service` nunca sabe de qué restaurante viene un
  pedido — guarda un snapshot desnormalizado de pickup copiado por
  `orders-service` al confirmar el pago (`ConfirmPaymentUseCase.java:140-157`).
- **L5** (decisión de sesión): el usuario eligió la opción 1 — entidad
  `merchant` nueva con N locales — sobre el modelo barato N:M
  usuario↔restaurante.
- **L6** (decisiones del usuario): "Q1-marca+sucursal. Q2- no, no se compartenb
  catalogos. Q5-si" → S5, S6, S7.
- **L7** (decisiones del usuario): "Q3-solo el dueño. Q4-si Q6-si, pero se debe
  crear si se agregan mas locales" → S8, S9, S10.
- **L8** (hallazgo de diseño): `POST /api/orders` **no** manda `restaurantId`; se
  deriva de los productos (`Order.validateSingleRestaurant`). El marketplace y
  el checkout no cambian con diez locales.
- **L9** (hallazgo de diseño): el radio de impacto real no es la base de datos
  sino el contrato interno `GET /api/internal/restaurants?userId=`, con dos
  consumidores independientes. Por eso la estrategia es aditiva
  (`owner/restaurants` + `membership`) y la ruptura se posterga a la fase 3.
- **L10** (hallazgo de diseño): `GetAccountDeletionBlockersUseCase.java:38` es
  el punto que más fácil se pasa por alto — hoy mira un solo local; si no mira
  todos, el borrado de cuenta puede cancelar pedidos de un local que se elimina.
- **L11** (artefactos): change folder creado en
  `openspec/changes/merchant-multi-local/` — `proposal.md` (D1–D11),
  `design.md` (modelo, migraciones, contratos, riesgos, Q1–Q6 cerradas),
  `spec.md` (FR-1..FR-16 con códigos de estado) y `tasks.md` (T1..T10 con
  riesgo y verificación).
- **L12** (tracking): este documento (`odd/tasks/merchant-multi-local.md`) y su
  mirror en Engram (`odd/merchant-multi-local/tasks`).
- **L13** (WU-1): rama `feat/merchant-multi-local` creada desde `origin/main`
  (`553aaf3`). Creadas `V11__create_merchants.sql`,
  `V12__add_restaurant_merchant_id.sql`, `V13__backfill_merchant.sql` (itera por
  restaurante para no fusionar dueños distintos con locales homónimos) y
  `MerchantMigrationIT.java`. Verificado con Testcontainers PostgreSQL 16.15
  (`MerchantMigrationIT` y `JpaRestaurantRepositoryAdapterIT` verdes). Commit `7dec230`.
- **L14** (WU-2): creados `Merchant`, `MerchantMember`, `MerchantRole`,
  `LocalContextRequiredException`, puertos `MerchantRepositoryPort` /
  `MerchantMemberRepositoryPort`, adaptadores JPA (`postgres`) y en memoria
  (`local`), `StoreContextResolver`, `MyStoreMerchantController` (`GET my/merchant`,
  `GET my/restaurants`, `POST my/restaurants` solo `DUEÑO` con auto-creación de
  merchant y `acceptingOrders=false`), `RestaurantResponse.merchantName` y
  `LocalContextRequiredResponse`. Verificado con `./gradlew.bat test --no-daemon`
  en `catalog-service` (`StoreContextResolverTest` 8/8,
  `MyStoreMerchantControllerIT` 7/7). Commit `0d10392`.
- **L15** (WU-3): migrados los 8 use cases de dueño/productos a `Long restaurantId`
  (`SetStoreOpenStatusUseCase`, `SetStoreHoursUseCase`, `SetStoreLocationUseCase`,
  `SetStoreDeliveryRadiusUseCase`, `OwnerCreateProductUseCase`,
  `OwnerListProductsUseCase`, `OwnerUpdateProductUseCase`,
  `OwnerDeleteProductUseCase`); `GetRestaurantByUserIdUseCase` se mantiene para
  el endpoint interno legacy `GET /api/internal/restaurants?userId=`.
  `MyStoreRestaurantController` y `MyStoreProductController` resuelven el local
  activo vía `StoreContextResolver` con `X-My-Store-Restaurant`, y
  `OwnerUpdateProductUseCase`/`OwnerDeleteProductUseCase` devuelven 403 si el
  producto pertenece a otro local. Verificado con `./gradlew.bat test --no-daemon`
  (38 suites, 199 tests, 0 fallos, 0 skipped). Commit `a560b46`.
- **L16** (WU-4): agregado `merchantId` a `InternalRestaurantResponse` y nuevos
  endpoints internos en `InternalCatalogController` (`GET /api/internal/owner/restaurants?userId=`
  y `GET /api/internal/owner/restaurants/{restaurantId}/membership?userId=`),
  manteniendo `GET /api/internal/restaurants?userId=`. Verificado con RED→GREEN
  en `InternalCatalogControllerTest` y suite completa de `catalog-service`
  (203 tests, 0 fallos, 0 ignorados). Commit `d20e0c2`.
- **L17** (WU-5): migrado `orders-service` a pertenencia contra `catalog-service`
  (`CatalogPort.checkRestaurantMembership` y `findOperableRestaurantIdsByUserId`
  en `CatalogHttpClientAdapter`): `UpdateOrderStatusUseCase`,
  `GetOrderDetailUseCase`, `ListOrdersUseCase` (con filtro opcional
  `X-My-Store-Restaurant`), `GetRestaurantSalesSummaryUseCase` +
  `GET /api/orders/store/summary` (por local o agregado),
  `GetAccountDeletionBlockersUseCase` (suma en todos los locales) y
  `merchantId` en `InternalRestaurantDto` → `RestaurantInfo` → `DeliveryRoute` →
  `DeliveryHttpClientAdapter.saveRoute`. Verificado con `.\mvnw.cmd test` (JDK 21):
  600 tests, 0 fallos, 0 errores, 0 skipped (incluye
  `CatalogHttpClientAdapterContractTest` con `MockRestServiceServer`).
  `gentle-ai-verify` reportado como no disponible. Commit `e80ae17`.
- **L18** (WU-6): agregada columna nullable `merchant_id` en `internal.delivery_routes`
  (`V11__add_route_merchant_id.sql`) y propagada en `DeliveryRoute`,
  `DeliveryRouteJpaEntity`, `CreateDeliveryRouteRequest`, `RouteResponse`,
  `JpaRouteRepositoryAdapter`, `InternalRoutesController`,
  `ListDeliveryRoutesUseCaseImpl` y `UpdateRouteStatusUseCaseImpl`. Verificado
  con `.\gradlew.bat :delivery-service:test --no-daemon` (BUILD SUCCESSFUL,
  `DeliveryMigrationIT` 3/3 y `JpaRouteRepositoryIT` 16/16).
- **L19** (WU-7): migrado `reviews-service` (`RestaurantLookupPort`,
  `HttpRestaurantLookupClientAdapter.checkRestaurantMembership`,
  `CreateReviewUseCaseImpl.ownsRestaurant`) al endpoint de membership de
  `catalog-service` (`204`/`404`, fail-closed 503 en error). Verificado con
  `.\gradlew.bat :reviews-service:test --no-daemon` (98/98 tests verdes,
  incluyendo `HttpRestaurantLookupClientAdapterContractTest` con `MockRestServiceServer`).
  Commit `e5d9cf8`.
- **L20** (WU-8): agregado `X-My-Store-Restaurant` a `cors.allowedHeaders` en
  `gateway/config/gateway.yaml` y `gateway/docker/gateway.yaml`; implementados
  `storeContextStore`, `StoreContext` + selector en `RoleNavigation`, prompt
  ante `400 LOCAL_CONTEXT_REQUIRED` en `AppShell`, `formatRestaurantName`
  ("Marca — Sucursal") en marketplace/panel, `ordersApi.getStoreSummary` en
  `StoreAnalyticsPage`, y recarga por cambio de local en catálogo, horarios,
  ubicación, analíticas y KDS. Verificado con `pnpm --dir apps/web typecheck`,
  `pnpm --dir apps/web test` (39 archivos, 352 tests) y
  `pnpm --dir gateway test:unit` (31 archivos, 355 tests). Commit `ab7eccf`.
- **L21** (WU-9): creada `V14__drop_restaurant_user_id_unique.sql` con los dos
  pre-checks obligatorios en bloque `DO $$` antes de dropear
  `restaurant_user_id_key`; extraído `MerchantOwnershipSupport` (invariante D11);
  retirados los guards `OwnerAlreadyAssignedException` en
  `CreateRestaurantUseCase` y `AssignRestaurantOwnerUseCase`. Verificado con
  `.\gradlew.bat test --no-daemon` en `catalog-service`: 208 tests, 0 fallos,
  0 ignorados (incluye Testcontainers `MerchantMigrationIT`). Commit `e70b8d4`.
- **L22** (WU-10): creada `V15__drop_restaurant_user_id.sql` (drop de
  `idx_restaurant_user_id` y `restaurant.user_id`), migrado
  `EraseRestaurantOwnerUseCase` a `MerchantMemberRepositoryPort.deleteByUserId`
  (preservando merchant, locales y catálogo), `GetRestaurantByUserIdUseCase`
  resuelto vía `StoreContextResolver`, y removidos `findByUserId` /
  `assignOwner` / `clearOwner` de `RestaurantRepositoryPort` y adaptadores.
  Verificado con `.\gradlew.bat test --no-daemon --console=plain` en
  `catalog-service`: 39 suites, 208 tests, 0 fallos, 0 ignorados.
