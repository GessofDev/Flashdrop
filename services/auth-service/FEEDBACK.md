# Feedback — auth-service (nikohomie) — Ronda 4

## Estado actual del repo (rama `feat/supabase-rest-migration`, PR #2)

Excelente trabajo en la ronda 3: el fix del join `roles(*)` quedó aplicado en el GET path, el `.gitignore` quedó ASCII limpio, el import duplicado se borró, hiciste todos los opcionales que sugerimos (README actualizado, `.env.example` raíz borrado, `run-auth.sh` con perfil supabase, `@Email`/`@Size` en `RegisterRequest`). El servicio ahora compila, bootea contra Supabase real y `/actuator/health` responde 200. **Los 3 blockers de ronda 3 están cerrados.**

PERO el smoke test contra Supabase real descubrió **2 blockers nuevos** que antes eran invisibles, más **2 issues importantes** que el próximo dev se va a comer.

| Aspecto | Estado |
|---|---|
| Boot contra Supabase real | OK (6.2s, perfil `supabase` activo) |
| `/actuator/health` | OK (200) |
| Build + tests | OK (16s) |
| Validaciones (`@Email`, `@Size`, `@NotBlank`) | OK (400 cuando corresponde) |
| Wrong password | OK (401) |
| **`/auth/register` happy path** | **ROTO** (500 PGRST204) |
| **`/auth/login` happy path** | **ROTO** (500 42P01, tabla `refresh_tokens` no existe) |
| **`/auth/refresh`** | **ROTO** (500 mismo motivo) |
| **`/auth/logout`** | **ROTO** (500 mismo motivo) |
| JWT keys del `.env` se cargan | NO (cae al path efímero aunque estén en env) |
| `/health` | ROTO (403, no whitelisted en `SecurityConfig`) |
| `/auth/jwks` (sin `.well-known/`) | ROTO (403) |

---

## Blocker 1: `refresh_tokens` no existe — login, refresh y logout devuelven 500

El `SupabaseRestRefreshTokenStoreAdapter` intenta hacer POST a `/refresh_tokens` y la tabla **no existe en Supabase**.

Confirmado por introspección directa del schema (`GET /rest/v1/`):

```
Tablas presentes: users, login, user_has_roles, roles, client, delivery,
                  delivery_routes, categories, products, orders,
                  order_items, restaurant, usuarios
Tablas ausentes:  refresh_tokens  ← acá muere el login
```

El stack trace al hacer login con creds válidas (`cliente@demo.cl` / `123456`):

```
ERROR: 42P01 — relation "public.refresh_tokens" does not exist
  at SupabaseRestRefreshTokenStoreAdapter.save(...)
  at RefreshTokenManager.issueFor(...)
  at LoginUserService.login(...)
```

### Cómo arreglarlo

Tenés dos opciones. **Recomendada la A** (más simple, menos invasiva):

**Opción A — agregar la tabla `refresh_tokens` a Supabase** (desde el SQL editor del panel o con `psql`):

```sql
create table if not exists public.refresh_tokens (
    id           bigserial primary key,
    user_id      bigint not null references public.users(id),
    token_hash   text   not null unique,
    expires_at   timestamptz not null,
    created_at   timestamptz not null default now(),
    revoked_at   timestamptz
);
create index if not exists refresh_tokens_user_idx on public.refresh_tokens(user_id);
create index if not exists refresh_tokens_hash_idx on public.refresh_tokens(token_hash);
```

**Opción B — repurposing de `login`**: si la tabla `login` ya tiene columnas de refresh, extendéla. Pero antes validá con el equipo líder que no haya otro servicio consumiéndola.

### Cómo verificar

Después de aplicar el DDL:

```bash
# Login con un seed user (cliente@demo.cl / 123456)
curl -s -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"login":"cliente@demo.cl","password":"123456"}' \
  -w "\nHTTP=%{http_code}\n"

# Esperado: 200 con {session_token, access_token, refresh_token, userId}
# NO 500
```

Si devuelve 200 y los tokens, el bug está cerrado.

---

## Blocker 2: `/auth/register` devuelve 500 con PGRST204 (el fix de ronda 3 introdujo esto)

Tu fix de ronda 3 agregó el campo `@JsonProperty("user_has_roles")` al record `UserRow` para que el GET pudiera deserializar el join anidado. **El mismo record se usa en el POST**, y como `user_hasRoles` es `null` en `save()`, Jackson lo serializa como `"user_has_roles":null` en el body. PostgREST lo rechaza:

```
ERROR: PGRST204 — Could not find the 'user_has_roles' column of 'users' in the schema cache
  at SupabaseRestUserRepositoryAdapter.save(SupabaseRestUserRepositoryAdapter.java:52)
```

### Cómo arreglarlo

**Opción A — mínimo (recomendada para esta ronda):**

Agregá `@JsonInclude(JsonInclude.Include.NON_NULL)` al record `UserRow`. Eso evita que campos `null` se serialicen.

```java
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserRow(
    Long id,
    String email,
    String rut,
    String name,
    String lastName,
    String phone,
    String photo,
    Instant createdAt,
    @JsonProperty("user_has_roles") List<UserHasRoleNestedRow> userHasRoles
) {}
```

**Opción B — más limpia (recomendada para el futuro):**

Partí los DTOs en:
- `UserCreateRow` (solo campos escribibles, sin `user_has_roles`)
- `UserReadRow` (con `user_has_roles` para hidratar roles)

Después cambiá `save()` para usar `UserCreateRow` y los métodos de lectura para `UserReadRow`.

### Cómo verificar

```bash
# First register (email válido) — esperado 201 (o 200)
TIMESTAMP=$(date +%s)
EMAIL="verify-r4-${TIMESTAMP}@test.com"
curl -s -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"Segura1234\",\"name\":\"Verify\",\"lastName\":\"R4\",\"phone\":\"+56912345678\"}" \
  -w "\nHTTP=%{http_code}\n"

# Duplicate — esperado 4xx (409 o 400 con error de duplicado)
curl -s -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"Segura1234\",\"name\":\"Verify\",\"lastName\":\"R4\",\"phone\":\"+56912345678\"}" \
  -w "\nHTTP=%{http_code}\n"
```

Si el primer devuelve 2xx y el segundo 4xx (no 500), el fix funciona.

---

## Importante 1: JWT keys del `.env` no se cargan (cae al path efímero)

El servicio tiene `JWT_PRIVATE_KEY` y `JWT_PUBLIC_KEY` en `auth-service/.env` con PEMs RSA reales. Pero `RsaKeyProvider` los ignora y genera claves efímeras al boot, descartando las del env.

**Evidencia** (verificada):
- El endpoint `/auth/.well-known/jwks.json` devuelve un JWKS
- El modulus del JWKS **no coincide** con la clave pública del `.env`
- Resultado: el servicio **no puede validar tokens emitidos por otro sistema**. Cualquier integración queda rota.

**Causa probable**: PEM multi-línea vía env-var en Spring Boot. El wrapper de Gradle o Spring Boot trunca los `\n` y solo lee la primera línea del PEM.

### Cómo arreglarlo

**Opción A — reemplazar `\n` por `\\n` literal en el .env**:

```env
JWT_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQ...\n-----END PRIVATE KEY-----"
JWT_PUBLIC_KEY="-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...\n-----END PUBLIC KEY-----"
```

Y en el código, reemplazar los `\n` literales antes de pasárselos al `KeyFactory`:

```java
String privateKeyPem = privateKeyPemRaw.replace("\\n", "\n");
```

**Opción B — leer los PEMs desde archivos en vez de env-var** (más limpio):

```env
JWT_PRIVATE_KEY_PATH=/run/secrets/jwt-private.pem
JWT_PUBLIC_KEY_PATH=/run/secrets/jwt-public.pem
```

Y en el adapter:
```java
String pem = Files.readString(Path.of(path));
```

### Cómo verificar

Después del fix:

```bash
# Capturar JWKS actual
curl -s http://localhost:8081/auth/.well-known/jwks.json > /tmp/opencode/jwks.json

# Extraer modulus y comparar con la clave pública del .env
openssl rsa -in /path/to/public_key.pem -pubout -modulus -noout 2>&1
```

Si los moduli coinciden, el fix funciona.

---

## Importante 2: `/health` devuelve 403 (no whitelisted)

El `HealthController` existe pero `SecurityConfig` solo whitelist `/actuator/health/**`. El endpoint custom `/health` está muerto en prod.

### Cómo arreglarlo

Opción A — agregar `/health` al `permitAll()` de `SecurityConfig`:

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health/**", "/health", "/auth/.well-known/jwks.json").permitAll()
    // ... resto
);
```

Opción B — borrar el `HealthController` si no lo usás (el `/actuator/health` ya cubre).

---

## Plan de acción (ronda 4)

### Paso 1 — Crear tabla `refresh_tokens` en Supabase (PRIORIDAD MÁXIMA)

Conectate al panel de Supabase (o `psql` con la URL de Kong) y aplicá el DDL del Bloqueante 1. **Sin esto, login/refresh/logout siguen rotos.** Confirmá con un `select count(*) from refresh_tokens;`.

### Paso 2 — Fix PGRST204 en `UserRow`

Editá `auth-service/src/main/java/com/flashdrop/auth/infrastructure/adapter/outbound/persistence/supabase/dto/UserRow.java` con la Opción A del Bloqueante 2 (agregar `@JsonInclude(NON_NULL)`).

```bash
./gradlew :auth-service:clean build -Dorg.gradle.java.home=/home/pelle/jdk/jdk-21.0.2 --no-daemon
# BUILD SUCCESSFUL
git add auth-service/src/main/java/com/flashdrop/auth/infrastructure/adapter/outbound/persistence/supabase/dto/UserRow.java
git commit -m "fix(auth): prevent null user_has_roles from leaking into POST body (PGRST204)"
```

### Paso 3 — Whitelist `/health` en SecurityConfig

Editá el archivo de SecurityConfig y agregá `/health` al `permitAll()`. Commit:

```bash
git commit -m "fix(auth): whitelist /health endpoint in SecurityConfig"
```

### Paso 4 — Fix JWT keys del env (opcional pero recomendado)

Aplicá la Opción A del Importante 1 (`\\n` literal en el .env + replace en código). **Si no lo hacés en esta ronda, marcalo explícito como TODO** porque bloquea cualquier integración multi-servicio.

### Paso 5 — Push + comentario en el PR

```bash
git push origin feat/supabase-rest-migration
# Comentario en PR #2: "Round-4 fix: refresh_tokens table created in Supabase, UserRow JsonInclude fixed, /health whitelisted. Login/register/refresh/logout now return 2xx."
```

---

## Setup local: crear el `.env` y arrancar el servicio

(Esta sección se mantiene del feedback de ronda 3 — es la receta que ya te pasamos.)

Antes de probar los fixes, asegurate de tener el ambiente configurado:

### 1. Crear el `.env` desde la plantilla

```bash
cd juniors/junior-1-auth
cp auth-service/.env.example auth-service/.env
```

Editá `auth-service/.env` y verificá que tenga estas 3 líneas (el resto puede quedar con los defaults):

```env
SUPABASE_URL=http://supabasekong-wymwq8rktid7ov678oe4va90.76.13.169.150.sslip.io
SUPABASE_SERVICE_ROLE_KEY=<pedirle al líder del equipo — NUNCA versionar>
SPRING_PROFILES_ACTIVE=supabase
```

> **Lo que NO debe quedar**: `DB_URL=jdbc:postgresql://...`, `DB_USER`, `DB_PASSWORD`. Si las ves, borralas.

### 2. Setear JAVA_HOME

```bash
java -version
# Esperado: openjdk version "21.x.x"
```

Si no tenés JDK 21 en PATH:

```bash
export JAVA_HOME=/path/a/tu/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
```

### 3. Arrancar el servicio

```bash
./gradlew :auth-service:bootRun -Dspring.profiles.active=supabase
```

Cuando veas `Started AuthServiceApplication in N seconds`:

```bash
curl http://localhost:8081/actuator/health
# Esperado: {"status":"UP","groups":[...]}
```

---

## Resumen

- **Lo que hiciste bien en ronda 3**: 3 blockers + 5 opcionales en un solo commit, todos aplicados. Compila, bootea, conecta a Supabase real.
- **Lo nuevo que aparece**: el smoke test contra Supabase real descubrió que login/refresh/logout dependen de una tabla que no existe, y que register se rompió por el fix de ronda 3. 4 issues, 2 críticos.
- **Próxima ronda esperada**: después de aplicar el DDL de `refresh_tokens` + el fix de `UserRow`, todos los happy paths deberían dar 2xx. Si pasa, evaluación final.

Cuando termines, subí los cambios a la misma rama y avisame para volver a probar.

---

## Recursos

- **Spring Boot `@JsonInclude` docs**: https://www.baeldung.com/jackson-deserialize-json-unknown-properties
- **Multi-line env-var en Spring**: https://stackoverflow.com/questions/38754237/how-to-inject-a-string-with-newlines-in-spring-using-application-yml
- **PostgREST embedded resources**: https://docs.postgrest.org/en/v12/references/api/resource_embedding.html
- **Patrón Supabase REST en catalog-service** (referencia del repo del junior-3): https://github.com/javier-sudo/catalog-service-java/tree/main/src/main/java/com/flashdrop/catalog/infrastructure/adapter/outbound/persistence/supabase