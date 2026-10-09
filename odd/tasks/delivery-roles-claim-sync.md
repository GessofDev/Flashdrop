# Feature: delivery-service — roles, claim sync, autosignup

> Orquestado por Sebastián (Delivery) en respuesta al feedback del equipo de
> integración. Bug raíz: el seed `V2__seed-delivery_persons.sql` solo creaba
> un perfil de repartidor para `user_id='1'` (que en `auth-service` es el
> cliente demo), y no existía endpoint de autosignup. Un cliente con JWT
> válido entraba a `/api/delivery/**` porque `SecurityConfig` solo exigía
> `authenticated()` y `JwtAuthenticationFilter` no extraía el claim `roles`.

## Estado de los hallazgos del equipo de integración

| # | Pedido del equipo | Estado actual | Acción |
|---|---|---|---|
| 1 | Exigir rol Repartidor para tomar pedidos y consultar rutas | Roto: `JwtAuthenticationFilter` pasa `List.of()` como authorities, `SecurityConfig` solo `authenticated()` | Fix: extraer `roles` del JWT + `hasRole("REPARTIDOR")` |
| 2 | Corregir la relación user ↔ perfil de repartidor | Seed V2 mal escrito (perfil para `user_id=1` que es cliente). Falta endpoint de autosignup | Fix: migración correctiva V6 + endpoint `POST /api/delivery/persons` |
| 3 | Asignación llegue a Orders y conserve estado "Listo para retiro" | Flag `delivery.claim.delegate-to-orders.enabled` en `false`; perfil `mock-orders` activo en FloCI | Fix: flipear flag + sacar perfil mock-orders del deploy + validar IT con orders real |
| 4 | Evite doble asignación | Ya cubierto: `SELECT ... FOR UPDATE` + check `deliveryPersonId != null` + `UNIQUE(order_id)` (V5) | Test: IT de concurrencia con Testcontainers (FALTA) |
| 5 | Pruebas de permisos | FALTA: ningún test cubre `roles` en el filtro | Tests: 4 escenarios `@WebMvcTest` con JWT real firmado con cada rol |
| 6 | Pruebas de fallos de comunicación | Cubierto en `HttpInternalOrdersClientAdapterTest` (4xx/5xx/network) | Test adicional: rollback transaccional cuando orders devuelve 5xx |

## Convenciones del repo aplicadas

- Conventional Commits, scope `delivery`.
- Constructor injection; records para DTOs.
- Migrations `V<n>__<description>.sql`, nunca editar merged.
- Tests unitarios sin Docker; ITs con Testcontainers cuando aplique.
- AGENTS.md §2: services no leen la DB de otros services. La relación
  user-repartidor vive en `delivery_db` (perfil) y se consulta vía
  `auth-service` por el subject del JWT. No se duplica el alta de usuarios.
- AGENTS.md §7: no reintroducir Supabase en delivery. Comunicación con
  orders por `HttpOrderServiceClientAdapter` (ya en uso).

## Work units (cada uno cierra con un commit + tests + docs)

### WU-1 · Extraer `roles` del JWT y poblar authorities
- **Archivos**:
  - `infrastructure/security/JwtAuthenticationFilter.java` — decodifica
    payload, lee claim `roles`, mapea a `SimpleGrantedAuthority("ROLE_<rol>")`.
  - `infrastructure/security/RoleAuthoritiesExtractor.java` (nuevo) —
    helper puro testeable que mapea `List<String>` de roles a
    `List<GrantedAuthority>`. Centraliza la lógica para poder testearla
    sin filtros ni JWKS.
- **TDD**: test rojo en `RoleAuthoritiesExtractorTest` (lista vacía,
  lista con un rol, lista con roles desconocidos, payload sin claim).
  Después test rojo en `JwtAuthenticationFilterTest` con un JWT firmado
  que incluya `roles: ["Repartidor"]` → authorities contiene
  `ROLE_Repartidor`. Después GREEN.
- **Commit**: `feat(delivery): extract roles claim into Spring authorities`.

### WU-2 · Crear enum `Role` y `CurrentUserResolver` (espejo de orders)
- **Archivos**:
  - `domain/model/Role.java` (nuevo) — enum con `Cliente`, `Restaurante`,
    `Repartidor` y `fromClaimValue(String)`. Mismo contrato que
    `orders-service/.../Role.java` para mantener consistencia cross-service.
  - `infrastructure/security/CurrentUserResolver.java` (nuevo) — replica
    el patrón de `orders-service`: `requireCurrentUserId()` y
    `requireCurrentRoles()` con fail-closed.
- **TDD**: test rojo en `CurrentUserResolverTest` con `SecurityContextHolder`
  controlado (caso sin auth → 403, caso con `ROLE_Repartidor` → presente,
  caso con `roles: ["Cliente"]` → no es repartidor).
- **Commit**: `feat(delivery): add Role enum and CurrentUserResolver`.

### WU-3 · Exigir `hasRole("REPARTIDOR")` en endpoints de delivery
- **Archivos**:
  - `infrastructure/config/SecurityConfig.java` — cambiar
    `.requestMatchers("/api/delivery/**", "/delivery/**").authenticated()`
    por `.hasRole("REPARTIDOR")` (incluye `POST /claim`, `GET /routes`,
    `PUT /routes/{id}/status`).
  - Mantener `/api/internal/**` con `permitAll()` (gated por
    `InternalApiKeyFilter` de `shared-observability`).
- **TDD**: actualizar `SecurityConfigTest` con casos:
  - sin JWT → 401 (sigue)
  - JWT sin rol → 403 (NUEVO)
  - JWT con `roles: ["Cliente"]` → 403 (NUEVO)
  - JWT con `roles: ["Repartidor"]` → 200 (NUEVO)
- **Commit**: `feat(delivery): require REPARTIDOR role on delivery endpoints`.

### WU-4 · Corregir seed de `delivery_persons` (V6) + endpoint de autosignup
- **Archivos**:
  - `db/migration/V6__delivery-persons-demo-correction.sql` (nuevo):
    - `DELETE FROM internal.delivery_persons WHERE user_id = '1';` (era
      cliente con perfil indebido).
    - `INSERT ... VALUES ('3', true) ON CONFLICT (user_id) DO NOTHING;`
      (repartidor demo, faltaba).
    - `INSERT ... VALUES ('4', true) ON CONFLICT (user_id) DO NOTHING;`
      (admin multirol, también es repartidor).
  - `application/dto/CreateDeliveryPersonRequest.java` (nuevo) — DTO con
    `vehicle` opcional.
  - `application/port/inbound/CreateDeliveryPersonUseCase.java` (nuevo).
  - `application/usecase/CreateDeliveryPersonUseCaseImpl.java` (nuevo) —
    usa `SecurityContextHolder` para resolver `userId`; rechaza si ya
    existe perfil.
  - `infrastructure/adapter/inbound/rest/DeliveryPersonController.java`
    (nuevo) — `POST /api/delivery/persons` con `hasRole("REPARTIDOR")`
    (ya cubierto por WU-3 vía `/api/delivery/**`).
- **TDD**: test de la migración con Testcontainers (verificar filas
  finales), test del use case (idempotente, falla si ya existe).
- **Commit**: `fix(delivery): correct delivery_persons seed and add courier self-signup`.

### WU-5 · Flipear flag de delegación + sacar `mock-orders` de FloCI
- **Archivos**:
  - `application-delivery.yml` — documentar que el flag queda en `true`
    con default. El default de producción es `true`; tests lo sobreescriben
    a `false` cuando quieren aislarse.
  - `infra/floci/INFRASTRUCTURE.md` — sacar `mock-orders` del perfil de
    delivery; documentar la variable `DELIVERY_CLAIM_DELEGATE_TO_ORDERS=true`.
  - `infra/floci/task-definitions/delivery-service.json` (si existe) —
    agregar la env var.
- **Sin test** (cambio de config/infra). Validación: IT contra orders real
  en WU-7.
- **Commit**: `chore(delivery): enable orders claim delegation by default`.

### WU-6 · IT de concurrencia para doble asignación
- **Archivos**:
  - `JpaRouteRepositoryAdapterConcurrencyIT.java` (nuevo) —
    Testcontainers Postgres + dos threads que llaman
    `assignDeliveryPerson(orderId, courierA)` y `(orderId, courierB)`
    simultáneamente; asserta que solo uno gana (`success`) y el otro
    recibe `RouteAlreadyAssignedException`.
- **TDD**: test rojo primero. Sin lock, ambos ganan. Con lock + UNIQUE,
  solo uno gana.
- **Commit**: `test(delivery): add concurrency IT for route claim race`.

### WU-7 · IT end-to-end con orders real (opcional, recomendado)
- **Archivos**:
  - `ClaimDeliveryEndToEndIT.java` (nuevo) — Testcontainers para
    Postgres de delivery + Postgres de orders. Carga dos pedidos, hace
    claim vía `POST /api/delivery/claim` con JWT de repartidor real,
    verifica que (a) el `route` queda con `deliveryPersonId` correcto,
    (b) la fila en `orders` tiene `delivery_id` actualizado vía la
    delegación, (c) el `status` del pedido **no cambió** (sigue
    "Listo para retiro" / `READY_FOR_PICKUP`), (d) la respuesta
    externa de orders no es 5xx.
- **Setup**: usa `WireMock` o testcontainers para orders-service si la
  red de Testcontainers es compleja. Si no, un `MockRestServiceServer`
  contra el `RestClient` de la delegación.
- **TDD**: primero test con orders simulado que devuelve 409 (rollback
  transaccional), después happy path.
- **Commit**: `test(delivery): add E2E IT for claim delegation to orders`.

### WU-8 · Tests de permisos (cubren WU-1, WU-2, WU-3)
- **Archivos**:
  - `DeliveryControllerSecurityTest.java` (nuevo) — 4 escenarios
    (sin JWT, sin rol, rol Cliente, rol Repartidor) cubriendo
    `POST /api/delivery/claim`.
  - `RouteControllerSecurityTest.java` (existente) — agregar
    `roles: ["Cliente"]` → 403 a `GET /delivery/routes` y
    `PUT /delivery/routes/{id}/status`.
  - `CreateDeliveryPersonUseCaseImplTest.java` (nuevo) — test unitario
    del use case de autosignup.
- **TDD**: rojos primero.
- **Commit**: `test(delivery): add permission tests for delivery endpoints`.

## Out of scope (dejar nota, no implementar)

- **No** agregar `status` a `OrderServicePort.OrderInfo` para pre-validar
  estado en delivery. La fuente de verdad es orders, y ya devuelve 409 si
  no es `isClaimable()`. Duplicar validación genera drift.
- **No** cambiar `delivery_persons.user_id` de `VARCHAR(64)` a `BIGINT`.
  El seed actual pasa a `VARCHAR(64)` porque el contrato del JWT es
  `Long.toString(...)`; cambiar el tipo es un refactor invasivo sin
  ganancia concreta. Dejarlo como nota para después.
- **No** agregar `Co-Authored-By:` en commits (AGENTS.md §1, §7).

## Orden de ejecución recomendado

1. WU-1 (extraer roles) — fundacional, todos los demás lo usan.
2. WU-2 (Role + Resolver) — fundacional.
3. WU-3 (hasRole en SecurityConfig) — bloquea clientes. Sin esto, el
   caso "cliente tomó pedido" se reproduce aún con el seed arreglado.
4. WU-4 (V6 + autosignup) — cierra el caso del dev.
5. WU-5 (flipear flag + sacar mock-orders) — habilita la delegación real.
6. WU-6 (IT concurrencia) — valida la invariante de doble asignación.
7. WU-8 (tests de permisos) — refuerza WU-3.
8. WU-7 (IT E2E con orders) — opcional pero muy recomendado; si el
   ambiente es complejo, dejar para follow-up y abrir issue.

## Riesgos identificados

- **R1**: flipear el flag de delegación sin validar contra orders real
  puede romper la integración si orders-service tiene su propio bug. Por
  eso el WU-7 es crítico antes de mergear WU-5. Si el IT falla,
  revertir el flag y mergear solo los cambios de seguridad (WU-1 a WU-4).
- **R2**: la migración V6 borra el perfil de `user_id='1'`. Si en otros
  ambientes (staging, demos previas) hay repartidores con `user_id=1`,
  van a perder su perfil. La nota en el commit debe ser explícita y
  se sugiere correr un backup antes.
- **R3**: el nuevo endpoint `POST /api/delivery/persons` con
  `hasRole("REPARTIDOR")` permite que un usuario se auto-asigne perfil
  de repartidor sin validación adicional. Para el caso demo es OK; en
  producción real probablemente se quiera un flujo de aprobación. Lo
  dejamos como follow-up en el issue tracker.
