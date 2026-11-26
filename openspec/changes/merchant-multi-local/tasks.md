# Tasks: `merchant-multi-local`

> Cada slice es un commit revisable (≈400 líneas cambiadas o menos) con tests
> y documentación en el mismo commit. Conventional Commits, sin atribución de
> IA (AGENTS.md §1).
>
> Specs de referencia: `proposal.md` (D1–D5, alcance, rollout) y `design.md`
> (§2 modelo de datos, §3 migraciones, §5 contratos, §7 supuestos a romper).

## Leyenda de riesgo

- **Bajo**: cambio aditivo, rollback trivial, sin superficie de autorización.
- **Medio**: toca comportamiento de negocio existente pero es reversible.
- **Alto**: toca autorización, o es irreversible en producción.

---

## T1 · Migraciones V11–V13 (aditivas + backfill)

**Objetivo**: crear `merchants` y `merchant_members`, agregar
`restaurant.merchant_id` y hacer el backfill. Ningún cambio de comportamiento.

**Spec**: design.md §2.1, §2.2, §2.3, §3 (V11, V12, V13)

**Archivos**
- `services/catalog-service/src/main/resources/db/migration/V11__create_merchants.sql` (nuevo)
- `services/catalog-service/src/main/resources/db/migration/V12__add_restaurant_merchant_id.sql` (nuevo)
- `services/catalog-service/src/main/resources/db/migration/V13__backfill_merchant.sql` (nuevo)
- test de migración (Testcontainers) que verifica el backfill

**Verificación**
- `count(merchants) == count(distinct user_id)` sobre los restaurantes con dueño
- `count(merchant_members) == count(distinct user_id)`
- `restaurant.merchant_id` no queda null en ningún restaurante con `user_id` no nulo
- Los restaurantes sin dueño quedan con `merchant_id` null
- Migración aplicada y revertida sobre una base de prueba sin residuo

**Riesgo**: Bajo · **Commit**: `feat(catalog): add merchants and restaurant ownership backfill`

---

## T2 · `StoreContextResolver` + endpoints de merchant y de lista de locales

**Objetivo**: resolver el local activo a partir del header
`X-My-Store-Restaurant` y exponer la lista de locales del usuario.

**Spec**: design.md §5.2 (regla de resolución y tabla de endpoints)

**Archivos**
- `.../application/usecase/StoreContextResolver.java` (nuevo)
- `.../application/port/outbound/MerchantRepositoryPort.java` (nuevo)
- `.../application/port/outbound/MerchantMemberRepositoryPort.java` (nuevo)
- `.../infrastructure/adapter/outbound/persistence/jpa/entity/MerchantEntity.java` (nuevo)
- `.../infrastructure/adapter/outbound/persistence/jpa/entity/MerchantMemberEntity.java` (nuevo)
- `.../infrastructure/adapter/inbound/rest/MyStoreMerchantController.java` (nuevo)
- `MyStoreRestaurantController.java` (modificado: `GET /my/restaurants`)
- `.../dto/response/RestaurantResponse.java` (campo aditivo `merchantName`, D6)

**Verificación**
- Header con local ajeno → `403`
- Sin header y un solo local → ese local (compat)
- Sin header y varios locales → `400 LOCAL_CONTEXT_REQUIRED` con la lista
- `GET /api/catalog/my/restaurants` devuelve todos los locales del merchant
- `POST /api/catalog/my/restaurants` crea un local dentro del merchant (sólo `DUEÑO`)
- Sólo un `DUEÑO` puede crear locales; un `ENCARGADO` recibe `403` (D9)
- Al crear un local, si el usuario todavía no tenía merchant, se crea
  automáticamente (D11)
- Todo local creado con dueño queda con `merchant_id` no nulo (D11)
- `RestaurantResponse` incluye `merchantName` y el nombre visible se arma como
  `"<merchantName> — <restaurant.name>"` (D6); el JSON viejo sigue deserializando

**Riesgo**: Medio · **Commit**: `feat(catalog): resolve store context and expose merchant endpoints`

---

## T3 · Migrar los use cases de dueño al local resuelto

**Objetivo**: que los 9 use cases que resuelven el local por `userId` reciban
el `restaurantId` ya resuelto, con un solo lugar de resolución.

**Spec**: design.md §5.2 (lista de use cases)

**Archivos**
- `GetRestaurantByUserIdUseCase`, `SetStoreOpenStatusUseCase`,
  `SetStoreHoursUseCase`, `SetStoreLocationUseCase`,
  `SetStoreDeliveryRadiusUseCase`, `OwnerCreateProductUseCase`,
  `OwnerListProductsUseCase`, `OwnerUpdateProductUseCase`,
  `OwnerDeleteProductUseCase` (modificados)
- `MyStoreProductController.java` (modificado)

**Verificación**
- Cada endpoint `/api/catalog/my/*` opera **sólo** sobre el local del header
- Producto de otro local no se puede leer, editar ni borrar con un header ajeno
- Un usuario con dos locales ve productos distintos según el header
- Tests existentes de los 9 use cases actualizados al nuevo contrato

**Riesgo**: Medio · **Commit**: `refactor(catalog): operate owner use cases on the resolved store`

---

## T4 · Contrato interno de ownership

**Objetivo**: exponer la lista de locales operables y la consulta de
pertenencia, sin romper el contrato actual.

**Spec**: design.md §5.4

**Archivos**
- `InternalCatalogController.java` (nuevos endpoints)
- `InternalRestaurantResponse.java` (campo aditivo `merchantId`)
- `.../application/usecase/ListOwnerRestaurantsUseCase.java` (nuevo)
- `.../application/usecase/CheckRestaurantMembershipUseCase.java` (nuevo)

**Verificación**
- `GET /api/internal/owner/restaurants?userId=` devuelve la lista
- `GET /api/internal/owner/restaurants/{id}/membership?userId=` → 204 / 404
- El endpoint viejo `GET /api/internal/restaurants?userId=` sigue respondiendo
  igual (test de contrato)
- `InternalRestaurantResponse` incluye `merchantId` y el JSON viejo sigue
  deserializando en `orders-service` y `reviews-service`

**Riesgo**: Bajo · **Commit**: `feat(catalog): expose owner restaurant listing and membership`

---

## T5 · `orders-service`: autorización y scopes por pertenencia (riesgo alto)

**Objetivo**: reemplazar las comparaciones de igualdad con un único local por
consultas de pertenencia, y corregir los scopes que hoy miran un solo local.

**Spec**: design.md §5.4 (tabla de consumidores), §7 (supuestos 5 y 7)

**Archivos**
- `CatalogHttpClientAdapter.java` (nuevo `checkRestaurantMembership`)
- `UpdateOrderStatusUseCase.java:164` (autorización)
- `GetOrderDetailUseCase.java:78` (autorización)
- `ListOrdersUseCase.java:43` (scope: local resuelto o todos los locales)
- `GetRestaurantSalesSummaryUseCase.java:55` (scope: `restaurantId` opcional)
- `GetAccountDeletionBlockersUseCase.java:38` (scope: **todos** los locales)
- DTOs de la ruta de reparto: campo `merchantId` que viaja al snapshot de pickup (D10)

**Verificación**
- Un operador con dos locales **no** puede tocar un pedido de un tercero
- Un operador **sí** puede tocar pedidos de cualquiera de sus locales
- El resumen de ventas sin `restaurantId` agrega todos los locales del merchant
  (D8); con `restaurantId` devuelve las métricas de ese local
- El borrado de cuenta detecta pedidos activos en **todos** los locales
- El snapshot de pickup incluye `merchantId` y coincide con el `merchantId` del
  restaurante consultado en catalog (D10)
- Test de contrato con `MockRestServiceServer` fijando path, headers y body

**Riesgo**: **Alto** (autorización) · **Commit**: `feat(orders): authorize store owners by membership`

> Slice suelto en su propio commit para que la revisión nativa lo tome aislado.

---

## T6 · `delivery-service`: `merchant_id` en la ruta de reparto

**Objetivo**: que la ruta de reparto registre a qué marca pertenece el local
del pickup, para poder auditar el origen del pedido (D10).

**Spec**: design.md §6 (fila `delivery-service`), §9 (riesgo de desalineación)

**Archivos**
- `services/delivery-service/src/main/resources/db/migration/V11__add_route_merchant_id.sql` (nuevo)
- `.../domain/model/DeliveryRoute.java` (campo `merchantId`)
- `.../infrastructure/adapter/outbound/persistence/jpa/entity/DeliveryRouteJpaEntity.java`
- `.../infrastructure/adapter/outbound/client/OrderServiceClientAdapter.java` (leer el merchant del pedido)

**Verificación**
- La ruta guardada incluye `merchant_id` del local del pickup
- Una ruta creada antes de la migración tiene `merchant_id` null y la lectura no rompe
- El repartidor puede auditar la marca de origen del pedido

**Riesgo**: Medio · **Commit**: `feat(delivery): record merchant id on delivery routes`

## T7 · `reviews-service`: verificación de ownership por pertenencia

**Objetivo**: que el dueño pueda calificar pedidos de cualquiera de sus locales.

**Spec**: design.md §5.4, §7 (supuesto 6)

**Archivos**
- `HttpRestaurantLookupClientAdapter.java` (nuevo `checkRestaurantMembership`)
- `CreateReviewUseCaseImpl.java:181-185` (usa la pertenencia)

**Verificación**
- El dueño de dos locales puede calificar un pedido de cualquiera de los dos
- Un usuario que no opera el local recibe `403`
- Test de contrato con `MockRestServiceServer`

**Riesgo**: Bajo · **Commit**: `feat(reviews): verify store ownership by membership`

---

## T8 · Selector de local en el panel web

**Objetivo**: que la tienda elija con qué local está operando.

**Spec**: design.md §5.2, §6 (web)

**Archivos**
- `apps/web/**` (selector de local + envío del header `X-My-Store-Restaurant`)

**Verificación**
- El selector lista los locales del merchant
- Cada local se muestra como "Marca — Sucursal" (D6)
- Cambiar de local recarga catálogo, horarios y estado de pedidos
- El header viaja al gateway y llega a catalog-service

**Riesgo**: Bajo · **Commit**: `feat(web): add store selector to the store panel`

---

## T9 · V14: quitar el `UNIQUE(user_id)` (riesgo alto, irreversible)

**Objetivo**: permitir que un merchant tenga N locales.

**Spec**: design.md §3 (V14), §7 (supuestos 1, 2, 3)

**Preliminar obligatorio**
```sql
select user_id from public.restaurant
where user_id is not null
group by user_id having count(*) > 1;
```
Debe devolver **cero filas**. Si devuelve filas, hay que fusionar esos locales
en un mismo merchant antes de seguir.

```sql
-- Invariante D11: todo restaurante con dueño tiene marca
select count(*) from public.restaurant
where user_id is not null and merchant_id is null;
```
También debe devolver **cero filas**.

**Archivos**
- `V14__drop_restaurant_user_id_unique.sql` (nuevo)
- `CreateRestaurantUseCase.java:37-39` (se elimina el guard
  `OwnerAlreadyAssignedException`)
- `AssignRestaurantOwnerUseCase.java:27-31` (se elimina el guard)

**Verificación**
- Un usuario con merchant puede crear un segundo local
- Un usuario con dos locales aparece en ambos locales de `GET /my/restaurants`
- `OwnerAlreadyAssignedException` ya no se lanza en el alta de restaurante
- Un `ENCARGADO` sigue sin poder crear locales (`403`)

**Riesgo**: **Alto** (único punto irreversible) · **Commit**: `feat(catalog): allow multiple stores per merchant`

> Slice suelto en su propio commit para que la revisión nativa lo tome aislado.

---

## T10 · Fase 4: deprecar `restaurant.user_id`

**Objetivo**: quitar la columna cuando ningún consumidor la lea.

**Spec**: design.md §4 (fase 4), §2.4

**Archivos**
- `V15__drop_restaurant_user_id.sql` (nuevo)
- `EraseRestaurantOwnerUseCase.java:23` → borra el `merchant_member` en vez del
  `restaurant.user_id`
- limpieza de los consumidores de `user_id`

**Verificación**
- El borrado de cuenta del usuario desvincula el `merchant_member`
- Si el merchant se queda sin miembros, el merchant y sus locales se conservan
  (mismo criterio que `V6` para restaurantes sin dueño)
- Ningún consumidor lee `restaurant.user_id`

**Riesgo**: Medio · **Commit**: `refactor(catalog): drop restaurant owner column`

---

## Fuera de alcance (ver proposal.md §6)

- Catálogo compartido entre locales con precio o disponibilidad por local.
- Facturación consolidada por marca y reparto de ingresos.
- Roles finos por local más allá de `DUEÑO` / `ENCARGADO`.
- Analítica multi-local y panel de marca.
- Migración de las implementaciones legacy de `juniors/`.
