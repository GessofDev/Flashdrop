# Proposal: `email-change`

## 1. Problem statement

En `auth-service`, el correo electrónico del usuario no es editable: `PUT /auth/profile` excluye explícitamente `email` y `rut` (ver `openspec/changes/profile-and-delivery/spec.md`). Sin embargo, los usuarios necesitan poder cambiar el email con el que inician sesión cuando pierden acceso futuro a su casilla o desean migrar a otra dirección.

El diseño no es trivial porque el identificador de acceso está duplicado en dos columnas `UNIQUE` (`public.users.email` y `public.login.login`), y `AuthenticateUserService` autentica contra `login.login`, no contra `users.email`. Actualizar solo una tabla deja al usuario bloqueado.

## 2. Target users / situations

Cualquier usuario autenticado (`Cliente`, `Restaurante`, `Repartidor`, `Admin`, `Soporte`) consumiendo vía Gateway.

**Flujo elegido (decisiones de producto):**
1. El usuario autenticado solicita el cambio ingresando su **contraseña actual** (`POST /auth/email/change-request`).
2. El sistema envía un enlace de confirmación de un solo uso **únicamente al email actual** del usuario.
3. El usuario abre el enlace e ingresa su **nuevo email** (`POST /auth/email/confirm`).
4. El sistema valida y actualiza `users.email` y `login.login` de forma atómica, y **revoca todos los refresh tokens activos** del usuario.

## 3. Business rules / constraints

| Regla | Fuente |
|---|---|
| Cada microservicio es dueño de su DB (`users` y `login` viven solo en `auth-service`) | `AGENTS.md` §2 |
| Hexagonal — sin imports de Spring/JPA/Hibernate en `domain/` o `application/` | `User.java`, `profile-and-delivery/spec.md` |
| Migración Flyway nueva (`V6__create_email_change_tokens.sql`) | `AGENTS.md` §4 |
| Conventional commits con scope `auth` / `gateway`, sin AI-attribution | `AGENTS.md` §1 |
| `SecurityConfig` usa `anyRequest().denyAll()` — los nuevos paths requieren `.permitAll()` explícito | `SecurityConfig.java` |
| Tokens opacos de un solo uso almacenados solo como hash SHA-256 | `V3__create_password_reset_tokens.sql`, `Sha256OpaqueTokenService.java` |

## 4. Product outcome / acceptance

- El usuario autenticado puede solicitar cambio de email validando su contraseña actual (`202 Accepted`).
- Recibe un enlace en su email actual con un token opaco de un solo uso y TTL acotado.
- Al confirmar con el token y el nuevo email (`204 No Content`), tanto `users.email` como `login.login` quedan actualizados al nuevo email normalizado.
- El usuario puede iniciar sesión inmediatamente con el nuevo email y su contraseña existente; el email anterior deja de funcionar.
- Todos sus refresh tokens activos quedan revocados tras la confirmación.
- Colisiones contra emails existentes devuelven `409 Conflict` sin consumir el token (o sin aplicar cambios parciales).

## 5. Scope (in)

1. `auth-service` (migración `V6`, dominio `User.conEmail` y modelo `EmailChangeToken`, puertos, casos de uso, adaptadores JPA/SMTP, DTOs, `AuthController`, `SecurityConfig`).
2. `gateway`: agregar `POST /auth/email/confirm` a `jwtOverrides` (`jwt.enabled: false`) en `gateway/docker/gateway.yaml` (y config de ejemplo si aplica).
3. Tests unitarios y de integración en `auth-service`.

## 6. Non-goals

- Verificación por enlace en el email **nuevo** (decisión de producto: el enlace va solo al email actual). Se recomienda enviar un aviso informativo sin enlace al nuevo email como mitigación de typos, pero no bloquea la confirmación.
- Revocación inmediata de access tokens RS256 ya emitidos (stateless, expiran en 15 min; no se introduce blacklist ni `token_version`).
- Migraciones o backfills en `orders-service`, `delivery-service`, `reviews-service` o `catalog-service` (ninguno persiste el email del usuario).
