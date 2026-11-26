# Spec: `email-change`

## 1. Overview

Este spec define los requisitos funcionales y no funcionales para permitir a un usuario autenticado cambiar el email con el que inicia sesión en `auth-service`, manteniendo consistencia entre `public.users.email` y `public.login.login`.

---

## 2. Functional Requirements

### FR-1 — Solicitud de cambio de email (`POST /auth/email/change-request`)

Endpoint autenticado en `AuthController` (expuesto vía gateway bajo el prefijo `/auth`, `jwt.enabled: true`).

- **Auth**: `Authorization: Bearer <accessToken>` validado en el controller con `validateToken.validate(bearer(authorization))`. Si falta o es inválido → `401 Unauthorized`.
- **Request body** (schema estricto: un campo desconocido se rechaza con `400`):
  ```json
  {
    "password": "string (required, @NotBlank)"
  }
  ```
- **Comportamiento**:
  1. Verifica rate limit por `userId` y por IP. Si excede → `429 Too Many Requests`.
  2. **Cuenta cada intento**, válido o no, contra ambos límites: cada solicitud puede poner un correo en la vía, así que una solicitud correcta tampoco es gratis. Es el mismo criterio que usa la recuperación de contraseña.
  3. Obtiene las credenciales **activas** del usuario y verifica `password` con `PasswordHasher.matches(...)`. Si la contraseña no coincide → registra `FAIL` en `AuditLogger` y responde `401 Unauthorized`.
  4. Invalida cualquier token de cambio de email previo pendiente del mismo usuario.
  5. Genera un token opaco aleatorio (`OpaqueTokenService`), persiste solo su hash SHA-256 en `email_change_tokens` con expiración configurable (default: mismo TTL que password reset).
  6. Envía un correo al **email actual** del usuario (`users.email`) con el enlace de confirmación usando fragmento `#token=<rawToken>` (para no filtrar el token en logs de Nginx ni en `Referer`).
  7. Si el envío falla (SMTP caído), invalida el token pendiente, registra `FAIL` en `AuditLogger` y propaga el error: el enlace nunca salió, así que no puede quedar vivo.
  8. Registra evento `EMAIL_CHANGE_REQUEST` con resultado `SUCCESS` en `AuditLogger`.
- **Response**: `202 Accepted` (sin body).

---

### FR-2 — Confirmación de cambio de email (`POST /auth/email/confirm`)

Endpoint público en `AuthController` (no requiere sesión activa; el usuario puede abrir el enlace desde otro dispositivo o navegador).

- **Auth**: público (sin JWT en gateway ni en Spring Security).
- **Request body** (schema estricto: un campo desconocido se rechaza con `400`):
  ```json
  {
    "token": "string (required, @NotBlank)",
    "newEmail": "string (required, @NotBlank)"
  }
  ```
- **Comportamiento**:
  1. Verifica rate limit por IP. Si excede → `429 Too Many Requests`. **Cuenta cada intento**, incluso los que no llegan a tocar la base.
  2. Valida y normaliza `newEmail` mediante el Value Object `Email` (`trim().toLowerCase()` + patrón). Si es inválido → `400 Bad Request` (`InvalidEmailException`).
  3. **El token se valida ANTES de mirar el email destino.** El enlace es público: si la colisión se comprobara antes, un llamador sin sesión distinguiría "ese email existe" (`409`) de "no existe" (`400`) y podría enumerar las cuentas registradas. Con el token inválido la respuesta es siempre la misma (`400 INVALID_EMAIL_CHANGE_TOKEN`), exista o no el email.
  4. Consume el token de forma atómica (`UPDATE ... SET used_at = :now WHERE token_hash = :hash AND used_at IS NULL AND expires_at > :now`). Si no existe, expiró o ya fue usado → registra fallo en rate limiter y `AuditLogger`, y responde `400` con `code="INVALID_EMAIL_CHANGE_TOKEN"`.
  5. **Dentro de la misma transacción**:
     - Si la cuenta ya no está activa (p. ej. eliminación en curso) → `400 INVALID_EMAIL_CHANGE_TOKEN`, sin tocar nada.
     - Si `newEmail` ya está en uso en `users.email` o en `login.login` → `409 Conflict` con `code="EMAIL_ALREADY_EXISTS"`. La excepción hace rollback, así que **el token no queda consumido** y el usuario puede reintentar con otro email.
     - Actualiza `users.email` al nuevo email normalizado (`user.conEmail(newEmail)`).
     - Actualiza `login.login` al nuevo email normalizado (`credentialStore.updateLogin(userId, newEmail.value())`). Si la fila de `login` no existe o no se actualiza, la operación falla y la transacción hace rollback: nunca queda un usuario con email nuevo y login viejo.
     - Revoca todos los refresh tokens del usuario (`refreshTokens.revokeAllForUser(userId)`).
     - Invalida cualquier otro token de cambio de email pendiente del usuario.
  6. Si ocurre un `DataIntegrityViolationException` por carrera de unicidad, la transacción hace rollback completo (incluido el consumo del token) y responde `409 Conflict`.
  7. Registra evento `EMAIL_CHANGE_CONFIRM` en `AuditLogger`.
  8. Envía un correo informativo (sin enlace ni token) a la dirección nueva notificando el cambio realizado. Un fallo en este aviso no revierte el cambio ya confirmado.
- **Response**: `204 No Content`.

---

### FR-3 — Consistencia de autenticación post-cambio

- Inmediatamente después de `204 No Content` en `/auth/email/confirm`:
  - `POST /auth/login` con el **nuevo email** y la contraseña actual → `200 OK`.
  - `POST /auth/login` con el **email anterior** → `401 Unauthorized`.
  - `POST /auth/refresh` con cualquier refresh token emitido antes del cambio → rechazado (revocado).
  - `GET /api/internal/users/{id}` → devuelve el nuevo `email`.
- Un enlace de confirmación no se puede reutilizar: el segundo intento con el mismo token responde `400`.

---

### FR-4 — Configuración de Gateway y SecurityConfig

- `services/auth-service/.../SecurityConfig.java`: agregar `.requestMatchers(HttpMethod.POST, "/auth/email/change-request", "/auth/email/confirm").permitAll()`.
- `gateway/docker/gateway.yaml`: agregar `/auth/email/confirm` en `jwtOverrides` con `jwt: { enabled: false, mode: jwks, issuer: flashdrop-auth }`. `/auth/email/change-request` mantiene JWT habilitado por defecto.
- `EMAIL_CHANGE_URL` (página de la web que recibe el enlace) se cablea en `gateway/docker/docker-compose.stack.yml` y `gateway/docker/env.stack.template`.
- **Degradación explícita**: si el correo está habilitado pero falta `EMAIL_CHANGE_URL` (o falta SMTP), el flujo de cambio de email queda sin correo y lo avisa en el log. **No se aborta el arranque**: un despliegue con SMTP configurado pero sin la URL nueva perdería el servicio entero de `auth-service`.

---

## 3. Non-Functional Requirements

### NFR-1 — Pureza hexagonal

Cero imports de `jakarta.persistence`, `org.springframework.data`, `org.springframework.boot` o `org.springframework.transaction` en `domain/` y `application/`. La atomicidad se resuelve con un port (`TransactionalRunner`) cuya implementación vive en `infrastructure/`.

### NFR-2 — Atomicidad sin datos partidos

Bajo ninguna circunstancia `users.email` y `login.login` pueden quedar con valores distintos tras `POST /auth/email/confirm`. Si no hay `PlatformTransactionManager` disponible, el runner **falla de forma explícita** en vez de ejecutar el bloque sin transacción.

### NFR-3 — Seguridad de tokens y privacidad

- El token crudo nunca se persiste en base de datos ni se loguea; solo se almacena su hash SHA-256.
- El formato de error público respeta `ApiError(code, service, traceId, message)`.
- Un email inválido o en colisión no quema el token válido del usuario.
- La respuesta ante un token inválido no revela si el email destino está registrado.

### NFR-4 — Rate limiting

Ambos endpoints cuentan cada intento contra el límite por IP, y el endpoint de solicitud también contra el límite por usuario. Ninguna solicitud correcta queda sin costo: cada una puede enviar un correo.
