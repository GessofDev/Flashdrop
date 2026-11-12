# Feature: F17 — orderId numérico en los endpoints de reparto y soporte

Aceptar el identificador numérico público del pedido en los tres endpoints que
lo consumen, además del UUID legado. Es el mismo criterio que ya aplican
`GET /api/orders/{id}`, `PUT /api/orders/{id}/status` y
`POST /api/orders/{id}/deliver` desde el issue #62.

## Specs

S1 — El pedido reportado
> F17: release y cancel-after-pickup piden el orderId como UUID, pero al repartidor le llega el numérico, así que la app recibe 400. Hay que hacer que acepten el numérico (como en el #62) o que delivery devuelva el UUID.

S2 — Alcance
> Implementar la primera solución (aceptar el numérico, como en el #62) para los 3 endpoints.

S3 — Los tres endpoints
> `POST /api/orders/{orderId}/release`
> `POST /api/orders/{orderId}/cancel-after-pickup`
> `GET /api/orders/{orderId}/delivery-events`

S4 — Criterio de resolución
> Mismo criterio que `OrderController.resolveOrderId`: primero se interpreta como
> número y se convierte con `IdConverter.toUuid`; si no es numérico se interpreta
> como UUID; si no es ninguno de los dos, error de dominio "Id de pedido invalido".

## Facts (established by reading the code, not new requirements)

- Los tres controllers declaran `@PathVariable UUID orderId`. Spring convierte el
  path variable a UUID antes de entrar al método, así que un id numérico produce
  400 por `MethodArgumentTypeMismatchException` y el use case nunca se invoca.
- El contrato de delivery es numérico: `RouteResponse.orderId` es `Long`, y los
  endpoints internos de delivery usan `@PathVariable Long orderId`. Ese es el id
  que la app del repartidor tiene en pantalla.
- `OrderController.resolveOrderId(String)` (línea 253) ya resuelve los dos
  formatos y es el criterio vigente desde el issue #62. Vive como método privado
  estático dentro de ese controller.
- `IdConverter.toUuid(long)` produce `new UUID(0L, id)`, el mismo mapeo que usa
  `GET /api/orders/{id}`, `PUT /status` y `POST /deliver`.
- `OrderDomainException` sin las palabras que disparan otros mapeos devuelve
  400 (ver `GlobalExceptionHandler.handleDomainException`), que es el mismo
  status que hoy produce el type mismatch.
- Los tres endpoints no están documentados todavía en
  `services/orders-service/openapi.yaml` (los agrego el PR #89 pero sin
  documentar las rutas nuevas).

## Tasks

| ID | Spec | Task | Route | Commit |
|----|------|------|-------|--------|
| T1 | S1, S3, S4 | Tests RED: un test por endpoint que prueba que el id numérico se resuelve al UUID interno correcto, y un test por endpoint que prueba que el UUID legado sigue funcionando. | parent | `f17a1b2` |
| T2 | S2, S4 | Extraer la resolución del id a un componente compartido (`OrderIdResolver`) y usarlo en `OrderController` y en los tres controllers de F17. Cambiar `@PathVariable UUID` por `@PathVariable String` + resolución. | parent | `f17a1b2` |
| T3 | S3 | Documentar las tres rutas en `services/orders-service/openapi.yaml` con el criterio de id (numérico o UUID), siguiendo el patrón de `GET /api/orders/{id}`. | parent | `f17c3d4` |
| T4 | S2 | Verificación: suite completa de orders (Surefire + Failsafe) en verde. | parent | ver L4 |

## Log

L1. User request (verbatim):

> ahora revisemos los puntos  F17,
>  F18, el rol SOPORTE y HTTPS.
> no hagamos cambios aun, solo explicame que son cada uno

L2. User request (verbatim):

> implemneta la primera solución para el F17. para los 3 endpoint

L3. Scope decision (parent): se implementa solo la primera solución (aceptar el
id numérico). No se toca el contrato de delivery, que sigue siendo numérico, ni
se agregan los otros puntos (F18, rol SOPORTE, HTTPS).

L4. RED (parent): con el código sin cambiar, los 9 tests nuevos correron y fallaron
exactamente los 3 del id numérico (`Status expected:<200> but was:<400>`), uno por
endpoint. Los otros 6 (UUID legado e id inválido) pasaban. El 400 venía del type
mismatch de Spring al convertir el path variable a UUID, antes de entrar al
método del controller.

L5. GREEN (parent): `mvn verify` completo de orders-service en verde —
**388 tests unitarios (Surefire) + 19 de integración (Failsafe)**, 0 fallos, 0
errores, `BUILD SUCCESS`. Incluye los 9 tests nuevos de
`OrderPathIdNumericFormatTest`.

L6. Chequeo diferencial (parent): el cambio toca código compartido — la resolución
del id que usan `GET /api/orders/{id}`, `PUT /api/orders/{id}/status` y
`POST /api/orders/{id}/deliver`, que el pedido no nombra. Se comparó el código
base (`git archive main` en un directorio temporal) contra el cambio con un probe
temporal (nunca commiteado) que exercise esos tres endpoints con 9 formatos de id
(numérico, UUID, inválido, vacío, negativo, overflow, con espacios, con signo
`+`, decimal). La salida fue **idéntica línea por línea** en los dos árboles: sin
regresiones. El probe se eliminó después.

L7. Entorno (parent): el JDK local es 17 y el proyecto pide 21, así que la suite
corre en `maven:3.9-eclipse-temurin-21`. Los tests con Testcontainers necesitan
`TESTCONTAINERS_RYUK_DISABLED=true` y
`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` para funcionar desde dentro
de un contenedor en Docker Desktop. Sin eso fallan los 42 tests de integración por
`Could not connect to Ryuk` — igual en el base y en el cambio, o sea que es del
entorno y no del cambio.

L8. Risk: item 3 — se cambia el contrato de la URL de tres endpoints públicos
(ahora aceptan un path variable que no es un UUID). Es una ampliación: el UUID
sigue aceptándose, y el id inválido sigue respondiendo 400. Ningún consumidor
existente se rompe.
