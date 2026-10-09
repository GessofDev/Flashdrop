# Feature: gateway 502 con JSON formateado + Orders Failsafe setup

> Orquestado en respuesta al feedback de Felipe (QA, 2026-10-09).
> Resuelve dos issues independientes: un bug funcional en el gateway introducido
> por PR #50 (`c506ea0`) y la falta de ejecución de pruebas de integración en
> orders-service. Se entregan en dos PRs separados para mantener el review
> focalizado por servicio.

## Contexto

**Tema 1 (gateway).** PR #50 quitó el descarte de `content-length`/`transfer-encoding`/
`connection`/`keep-alive` del *request* que se reenvía, para poder soportar uploads
binarios. La eliminación era correcta solo para cuerpos que el gateway reenvía **tal
cual** (Buffer de Fastify raw-body parser). Pero el motor **siempre** recompone el body
JSON parseado con `JSON.stringify` (compacto), y al copiar la cabecera `content-length`
del cliente quedan bytes prometidos ≠ bytes enviados. Undici aborta, el engine devuelve
502. No afecta a binarios/multipart (Buffer pasa por referencia, content-length coincide).
Reproducir: enviar un JSON con `JSON.stringify(obj, null, 2)` o con `\n` entre
elementos, o un `2500.0` (la re-serialización lo pasa a `2500` y reduce el largo).

El test de regresión existente (`multipart.test.ts`, "application/json still parsed and
forwarded") usa `JSON.stringify(jsonBody)` que ya es compacto, así que el content-length
original y el del body re-empaquetado coinciden por casualidad y el bug no se detecta.

Adicional: en la **respuesta** el engine copia todas las cabeceras del backend, incluidas
las hop-by-hop (`Transfer-Encoding`, `Connection`, `Keep-Alive`, `Proxy-Connection`,
`Upgrade`). El `Transfer-Encoding: chunked` en una respuesta que el cliente lee como
completa rompe la lectura.

**Tema 2.1 (orders Failsafe).** `services/orders-service/pom.xml` no declara el plugin
`maven-failsafe-plugin`. Surefire (fase `test`) por default solo corre clases que
matchean `**/Test*.java`, `**/*Test.java`, `**/*Tests.java`. La clase
`OrderControllerStatusAuthzIT.java` (12 casos, pasa a mano) **nunca corre en CI** ni en
ningún lado. La CI de Orders corre `./mvnw test` que es la fase de unitarias. Auth y
Catalog (Gradle) ya ejecutan sus `*IT` — confirmado por el check de cobertura
`JpaProductRepositoryAdapterIT.xml` en el CI de Catalog. Orders no tiene nada equivalente.

**Tema 2.2 (CI intermitente).** El log de la corrida fallida `37661248044` (>11k líneas)
requiere autenticación GitHub para consultarlo; este orquestador no tiene acceso.
Hipótesis más probable: timeout de arranque de Testcontainers en el runner. Subir
`surefire-reports` (y luego `failsafe-reports`) como artefacto **siempre** (no solo en
falla) es la mejora de diagnóstico de menor costo y mayor retorno.

## Convenciones del repo aplicadas

- AGENTS.md §1: Conventional Commits con scope. Mensaje en inglés, sin trailer
  `Co-Authored-By`.
- AGENTS.md §3: `orders-service` usa Maven, no se agrega a `settings.gradle.kts`.
- AGENTS.md §4: un PR = un cambio cohesivo; no mezclar gateway y orders.
- AGENTS.md §6: una rama por cambio, nombre `fix/<slug>` o `feat/<slug>`. Rebase
  sobre `main` antes de abrir PR. Squash-merge.
- AGENTS.md §7: no secretos, no reintroducir Supabase, no saltarse hooks.
- `odd/tasks/delivery-roles-claim-sync.md` (plantilla interna): formato de
  work units, tabla de hallazgos, sección de riesgos.

## Work units (cada uno cierra con un commit + tests + evidencia)

### WU-1 · Tests RED de regresión para JSON formateado (gateway)

- **Archivos**:
  - `gateway/tests/integration/multipart.test.ts` — agregar dos casos al
    describe "Binary upload integration" (o nuevo describe "JSON content-length
    regression"):
    - `forwards JSON with whitespace and newlines without 502`:
      `JSON.stringify({foo: 'bar', n: 2500.0}, null, 2)`, assert
      `response.statusCode === 200`, `backend.lastHeaders['content-length']
      === String(backend.lastBody.length)`.
    - `forwards JSON with embedded number that round-trips shorter`:
      `JSON.stringify({n: 2500.0})` produce `{"n":2500}` (6 bytes vs 11 bytes
      del formatted input). Mismo shape de asserts.
  - `gateway/tests/unit/proxy/engine.body.test.ts` — agregar caso
    `computeContentLengthFor(string)` / `Buffer` (helper a extraer en WU-2).
- **RED esperado**: ambos casos fallan hoy porque el engine envía
  `content-length: N` con body de M bytes, M < N. En el mock backend
  `lastHeaders['content-length'] !== String(lastBody.length)`.
- **Commit**: `test(gateway): add regression for formatted JSON content-length`.

### WU-2 · Recalcular `content-length` siempre desde el body final

- **Archivos**:
  - `gateway/src/proxy/headers.ts` — nuevo helper exportado
    `computeContentLength(body: ProxyRequestBody): number | null`:
    - `string` → `Buffer.byteLength(s, 'utf8')`.
    - `Buffer` → `buf.length`.
    - `null` → `0`.
    - `AsyncIterable` (stream) → `null` (deja que Undici use chunked; o
      preserva el del cliente si estaba; documentar en el JSDoc).
  - `gateway/src/proxy/engine.ts` — en `executeRequest`, antes de pasar
    headers a `pool.request(...)`, si `body` no es stream, **sobrescribe**
    `headers['content-length']` con el valor calculado. Si es stream,
    déjalo como vino.
  - `gateway/src/proxy/types.ts` — no requiere cambios; el helper consume
    `ProxyRequestBody` ya exportado.
- **GREEN esperado**: los tests de WU-1 pasan. El caso binario (Buffer
  pass-through) sigue funcionando porque `buf.length` es el mismo número
  que el cliente mandó.
- **Verificación extra**: correr el `multipart.test.ts` completo para
  asegurar que el caso binario `application/octet-stream` no se rompió.
- **Commit**: `fix(gateway): recompute content-length from outgoing body`.

### WU-3 · Filtrar cabeceras hop-by-hop de la respuesta

- **Archivos**:
  - `gateway/src/proxy/headers.ts` — nuevo helper exportado
    `HOP_BY_HOP_RESPONSE_HEADERS: ReadonlySet<string>` con la lista
    canónica de RFC 7230 §6.1: `connection`, `keep-alive`,
    `proxy-connection`, `transfer-encoding`, `upgrade`, `te`, `trailers`.
    En minúsculas; se compara case-insensitive.
  - `gateway/src/proxy/engine.ts` — en `sendResponse`, antes de
    `reply.header(key, value)`, saltar si `key.toLowerCase()` está en
    el set.
  - `gateway/tests/integration/proxy.test.ts` — nuevo test:
    mock backend que devuelve `Transfer-Encoding: chunked` +
    `Connection: close`; assert que `response.headers['transfer-encoding']`
    es `undefined` y `response.headers['connection']` es `undefined` del
    lado del cliente.
- **Verificación extra**: el test actual de proxy usa un backend que NO
  setea estas cabeceras, así que no rompe. Confirmar con la suite
  integration completa.
- **Commit**: `fix(gateway): strip hop-by-hop headers from upstream response`.

### WU-4 · Orders: declarar Failsafe + ajustar CI

- **Archivos**:
  - `services/orders-service/pom.xml` — agregar bloque
    `<plugin><artifactId>maven-failsafe-plugin</artifactId></plugin>`
    en `<build><plugins>`. **Sin `<version>`** porque Spring Boot
    starter parent (`3.2.5`) ya lo tiene en `pluginManagement`. Bind
    `integration-test` + `verify` al goal default. Agregar comentario
    explicando que Surefire corre `*Test` (fase `test`) y Failsafe corre
    `*IT` (fase `integration-test`/`verify`).
  - `.github/workflows/orders-service-ci.yml`:
    - Cambiar `./mvnw test` por `./mvnw verify`.
    - Agregar step de upload de `target/surefire-reports/**` y
      `target/failsafe-reports/**` como artefacto, **siempre**
      (`if: always()`, no `if: failure()`), con nombre
      `test-results-orders-service`.
    - Agregar step de check de cobertura `*IT` estilo Catalog (script
      Python que parsea `TEST-*IT.xml` y rompe si `tests == 0`).
      Aplicar al menos a `OrderControllerStatusAuthzIT.xml`; el script
      puede ser genérico (busca cualquier `TEST-*IT.xml` bajo
      `target/failsafe-reports/` y verifica `tests > 0`).
- **Verificación local**:
  - `cd services/orders-service && ./mvnw validate` debe pasar (sintaxis
    POM + Failsafe plugin bien declarado).
  - `cd services/orders-service && ./mvnw help:effective-pom | grep -A2 failsafe`
    debe mostrar la versión del plugin resuelta.
  - `cd services/orders-service && ./mvnw failsafe:help` no debe fallar
    (prueba que el plugin cargó).
  - **No se puede correr `mvnw verify` localmente** en Windows por el
    issue documentado en el CI workflow (Testcontainers + named-pipe
    Docker Desktop). El runner ubuntu del CI es el que valida la
    ejecución real.
- **Commit**: `feat(orders): enable maven-failsafe for *IT tests in CI`.

## Branching

- `fix/gateway-content-length-and-hop-by-hop-headers` desde `main` → PR #1
  (cubre WU-1, WU-2, WU-3; idealmente un commit por WU o dos commits:
  tests + fix + response filter).
- `feat/orders-failsafe-and-ci-reports` desde `main` → PR #2 (cubre WU-4).

## Commit strategy

Siguiendo `odd/tasks/delivery-roles-claim-sync.md` como plantilla:

- Cada WU cierra con un commit.
- Subject imperative, ≤ 72 chars, scope `(gateway)` o `(orders)` o `(ci)`.
- Body explica el **por qué**, no el **qué**.
- Conventional Commits (`test:`, `fix:`, `feat:`, `chore:`).
- **NO** trailer `Co-Authored-By` (AGENTS.md §1, §7).
- Mensaje en inglés.
- Rebase sobre `main` antes de abrir PR; squash-merge.

## Out of scope (dejar nota, no implementar)

- **Investigación del log `37661248044`** de GitHub Actions: requiere
  cuenta autenticada. Crear issue de seguimiento "Investigate Orders CI
  intermittent failure on run 37661248044" con el primer `Caused by:`
  como punto de partida. Mientras tanto, los reports subidos como
  artefacto dan la red de seguridad para futuras corridas.
- **No** agregar `maven-surefire-plugin` explícito al pom (Spring Boot
  starter ya lo provee y bindea a `test`).
- **No** cambiar la convención de nombres `*IT` (AGENTS.md plan §4).
- **No** agregar `forkCount`/`reuseForks` ni tuning de Testcontainers
  en este PR — la causa raíz no está diagnosticada. Hacerlo cuando se
  tenga el log.
- **No** tocar delivery, catalog, auth, ni shared-observability.

## Riesgos identificados

- **R1**: recalcular `content-length` siempre puede romper clientes que
  hoy dependen de un comportamiento específico (poco probable, pero
  documentado en el JSDoc del helper). El test `application/octet-stream`
  y el de `multipart/form-data` cubren el caso binario y deben seguir
  verdes.
- **R2**: el script Python de check `*IT` en el CI de Orders es
  inspirado en el de Catalog. Si el formato XML cambia entre versiones
  de Surefire/Failsafe, puede romperse. Mitigación: el script falla
  ruidosamente (no silenciosamente), y el paso de `mvnw verify` ya
  rompe el build si un test falla.
- **R3**: subir artefactos **siempre** consume más storage que solo en
  `if: failure()`. Para 2 services (Orders + su futuro Failsafe), sigue
  siendo trivial. Si el repo crece a muchos servicios, reconsiderar.
- **R4**: el merge de PR #1 (gateway) podría entrar antes que el de
  PR #2 (orders) — son independientes. Cualquier orden sirve. Si por
  alguna razón se quiere serializar, hacerlo con PR #2 primero (es
  trivial y desbloquea diagnósticos).

## Orden de ejecución

1. WU-1 (tests RED) en `fix/gateway-content-length-and-hop-by-hop-headers`.
2. WU-2 (fix engine + helper). Confirmar GREEN.
3. WU-3 (response headers + test). Confirmar GREEN.
4. Commit work-unit. Push.
5. Crear `feat/orders-failsafe-and-ci-reports` desde `main`.
6. WU-4 (pom + CI). Validar localmente con `mvnw validate` y `failsafe:help`.
7. Commit work-unit. Push.
8. Reportar a Sebastián/Javier con resumen + comandos de pre-PR audit.

## Cierre

Después del merge:

- Cerrar el issue de seguimiento del CI intermitente con el link al log
  una vez investigado.
- Actualizar `infra/floci/INFRASTRUCTURE.md` si la causa del CI
  intermitente resulta ser de infraestructura (no esperado, pero por
  si acaso).
