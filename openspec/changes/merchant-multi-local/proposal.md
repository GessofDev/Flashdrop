# Proposal: `merchant-multi-local`

## 1. Problem statement

FlashDrop modela la relación entre un usuario y su comercio como **1:1
estricto**. No existe ninguna noción de marca, cadena, grupo, sucursal ni
local en todo el monorepo: la entidad `restaurant` de `catalog-service` es
simultáneamente "el local", "el comercio" y "la marca".

El bloqueo está en cuatro capas independientes, todas vigentes hoy:

| Capa | Restricción | Evidencia |
|---|---|---|
| Base de datos | `restaurant.user_id` es `UNIQUE` | `services/catalog-service/src/main/resources/db/migration/V1__create_schema.sql:11`; `V6__restaurant_owner_nullable.sql` saca el `NOT NULL` y **conserva el `UNIQUE`** a propósito (varios `NULL` son válidos en PostgreSQL, para restaurantes que crea el admin y reasigna después) |
| Aplicación | Un usuario con restaurante no puede recibir otro | `CreateRestaurantUseCase.java:39-41` y `AssignRestaurantOwnerUseCase.java:28-32` lanzan `OwnerAlreadyAssignedException("El usuario ya tiene asignado otro comercio")` → HTTP 409 (`RestExceptionHandler.java:31-35`) |
| Forma de las queries | Todo resuelve **un** restaurante | `SpringDataRestaurantRepository.findByUserId` devuelve `Optional<RestaurantEntity>`; `GetRestaurantByUserIdUseCase.java:17-21`; `GET /api/catalog/my/restaurant` (`MyStoreRestaurantController.java:58-61`); `GET /api/internal/restaurants?userId=` devuelve una única `InternalRestaurantResponse`, consumida por `orders-service` como `Optional<UUID>` (`CatalogHttpClientAdapter.java:94-118`) |
| Contexto de sesión | No hay forma de decir "estoy operando el local X" | El JWT lleva solo `sub`, `email`, `roles` (`JwtTokenService.java:37-47`); el gateway reenvía únicamente `sub/iss/aud/exp/iat/jti` |

Consecuencia de negocio: **un cliente que tiene dos locales no puede existir
en el sistema**. No es que el modelo lo castigue — es que no hay forma de
representarlo. Un operador con dos restaurantes se ve obligado a elegir uno, y
el resto del sistema (catálogo, horarios, radio de reparto, políticas de
pedido, reseñas) está clavado a `restaurant_id`, es decir, al local, no a la
marca.

Peor aún, la pérdida de información se propaga hacia el reparto:
`delivery-service` nunca sabe de qué restaurante viene un pedido — sólo guarda
un snapshot desnormalizado de pickup (`pickup_address`, `pickup_latitude`,
`pickup_longitude`) que `orders-service` le copia al confirmar el pago
(`ConfirmPaymentUseCase.java:132-144`, `delivery-service/V10__delivery_routes_pickup_coordinates.sql`).

Este change introduce un nivel intermedio: la entidad **merchant** (grupo de
locales), que es dueña de N restaurantes y a la que se associate el usuario
operador. `restaurant` deja de ser "el comercio" y pasa a ser "el local".

## 2. Target users / situations

**Usuarios internos:** la app web (React) y las apps Flutter (cliente,
repartidor, tienda) consumiendo vía Gateway.

**Casos cubiertos por este change:**

- Un usuario dueño de dos restaurantes gestiona ambos desde su panel: elige
  local activo y opera catálogo, horarios y estado de pedidos de ese local.
- Un mismo restaurante puede tener varios operadores (el dueño y un encargado),
  sin que el encargado pueda tocar los locales ajenos.
- Un local queda asociado a un merchant y el merchant tiene sus propios datos
  de contacto y estado, sin duplicar la ficha por cada local.
- El cliente del marketplace sigue viendo un local por vez al pedir: elige el
  restaurante concreto y paga contra ese `restaurant_id`, igual que hoy.

**Fuera de alcance de este change:** catálogo compartido entre locales con
precios por local, reparto de ingresos entre locales del mismo merchant,
multi-tenant por marca en el gateway, y cualquier analítica multi-local.

## 3. Decisiones tomadas con el equipo

| Decisión | Elección | Motivo |
|---|---|---|
| D1 · Modelo de dominio | **Entidad `merchant` nueva, propietaria de N `restaurant`** | Un grupo con N locales no es "un usuario con N restaurantes": es una organización con sus propios datos, sus propios usuarios y su propio ciclo de vida. Modelarlo como entidad permite después sumar roles por local, facturación consolidada y métricas de marca sin volver a migrar. La alternativa barata (N:M usuario↔restaurante, quitando el `UNIQUE`) resuelve el caso de uso inmediato pero no deja lugar para "el grupo" y deja la propiedad sin dueño semántico. |
| D2 · Identidad del operador | **N:M entre `merchant` y usuario (`merchant_member`), con rol por local opcional** | Separa "quién es el dueño" de "quién opera". El `UNIQUE(user_id)` de `restaurant` desaparece del modelo de ownership: `restaurant.merchant_id` apunta al grupo, y la pertenencia se resuelve por `merchant_member`. |
| D3 · Contexto de local activo | **Header explícito `X-My-Store-Restaurant` en los endpoints de dueño, con default = primer local** | No ensuciar el JWT (que es de largo plazo) ni obligar al cliente a mandar el id en el body de cada operación. El header es explícito, testeable y por-request. |
| D4 · Identidad hacia adentro | **`merchant_id` explícito en los contratos internos; `restaurant_id` se mantiene como identificador de operación** | `orders-service` y `delivery-service` operan por pedido y por local. El merchant aparece donde hace falta (panel de dueño, reportes), no en el camino caliente del pedido. |
| D5 · Reparto | **El pedido conserva `restaurant_id` y el snapshot de pickup se completa con `merchant_id`** | `delivery-service` no necesita saber del merchant para cumplir su trabajo, pero el snapshot debe quedar trazable: hoy un repartidor no puede ni auditar de qué local salió el pedido. |
| D6 · Nombre en el marketplace | **"Marca — Sucursal"**: `RestaurantResponse` suma `merchantName` y el `name` del local queda como nombre de sucursal | El cliente busca la marca; el local es dónde retira. Dos locales con el mismo nombre de marca son indistinguibles para pedir. No requiere migración: el nombre actual del restaurante pasa a ser el de la sucursal. |
| D7 · Catálogo | **Por local, sin compartir**: cada local tiene su propia carta, con sus precios y su disponibilidad | Compartir catálogo exige productos de marca con precio y disponibilidad por local — otro modelo de datos, otro change. |
| D8 · Resumen de ventas | **Por local y agregado de marca**: `restaurantId` opcional | El dueño de dos locales quiere ver el total del negocio, no sólo el del local activo. |
| D9 · Quién crea locales | **Sólo el `DUEÑO`** del merchant | Crear un local es una decisión de la marca, no del local. El `ENCARGADO` opera locales, no los abre. |
| D10 · Trazabilidad en reparto | **El snapshot de pickup suma `merchant_id`** (`delivery_routes`) | Hoy el repartidor no puede auditar de qué local salió el pedido: `delivery-service` recibe nombre, dirección y coordenadas, pero ninguna marca. Con multi-local, esa trazabilidad deja de ser opcional. |
| D11 · Locales sin marca | **Se permiten sólo si no tienen dueño** (huérfanos del admin). Al asignar dueño o al agregar un segundo local, la marca se crea o se exige | El admin crea restaurantes sin dueño hoy (`V6`) y ese flujo no se rompe. Pero un local con dueño siempre tiene marca: el backfill y las reglas de alta lo garantizan. |

## 4. Impacto

**Servicio modificado — `catalog-service` (dueño del cambio)**

- Nueva tabla `merchants` y `merchant_members` (migraciones Flyway nuevas).
- `restaurant.merchant_id` nullable + backfill desde `restaurant.user_id`; el
  `UNIQUE(user_id)` se elimina (los datos existentes se agrupan 1:1, así que
  la pérdida de información es nula).
- Los endpoints de dueño pasan a operar sobre un local elegido, no sobre "el
  único local del usuario".
- `GET /api/internal/restaurants?userId=` deja de devolver un único restaurante:
  se redefine el contrato.

**Servicios que consumen el contrato interno**

- `orders-service`: `CatalogHttpClientAdapter` resuelve `Optional<UUID>` por
  `userId`; hay que revisar si esa resolución sigue siendo válida o pasa a
  ser por `merchant`/local.

**Servicio con pérdida de información a corregir**

- `delivery-service`: el snapshot de pickup se enriqueece con el identificador
  del comercio para poder auditar el origen del pedido.

**Sin cambios**

- `auth-service`: los roles siguen siendo de plataforma; la pertenencia a un
  merchant vive en `catalog-service` (dueño del dominio de comercio).
- `gateway`: no cambia el routing; sólo se documenta el header de contexto.

## 5. Estrategia de rollout

1. **Fase 1 — aditiva y reversible**: se crean `merchants` y
   `merchant_members`, se agrega `restaurant.merchant_id` y el backfill. La
   columna `user_id` se conserva. Nada cambia para el cliente.
2. **Fase 2 — dual-write**: los endpoints de dueño escriben en ambos lados y
   resuelven por `merchant_member` con fallback a `user_id`.
3. **Fase 3 — el `UNIQUE(user_id)` se elimina** y la lectura de "mi local"
   pasa a ser por merchant. Éste es el único punto irreversible y va detrás de
   la Fase 2 completa.
4. **Fase 4 — limpieza**: se deprecia `user_id` en `restaurant` cuando ningún
   consumidor lo lea.

## 6. Fuera de alcance

- Facturación consolidada por marca y reparto de ingresos entre locales.
- Catálogo compartido entre locales con precio o disponibilidad por local.
- Roles finos por local (cajero vs. encargado vs. dueño) más allá del rol de
  miembro de merchant.
- Panel de Cross / analítica multi-local.
- Migración de los datos de `juniors/` (implementaciones legacy).
