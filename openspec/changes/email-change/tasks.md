# Tasks: `email-change`

## Status

| PR | Scope | Estado |
|---|---|---|
| `PR-auth-email-change` | Migración V6 + dominio + casos de uso + REST + SecurityConfig + tests | ⏳ Pendiente |
| `PR-gateway-email-change` | `jwtOverrides` para `/auth/email/confirm` en `gateway.yaml` | ⏳ Pendiente |

---

## PR-auth-email-change — `feat/auth-email-change`

**Branch**: `feat/auth-email-change` (base: `main`)
**Scope**: Flujo completo de cambio de email de login en `auth-service`

### T-1 — Migración Flyway `V6__create_email_change_tokens.sql`
- **Files**: `services/auth-service/src/main/resources/db/migration/V6__create_email_change_tokens.sql`
- **Acceptance**: tabla `public.email_change_tokens` con `id`, `user_id` (FK cascade a `users`), `token_hash` UNIQUE, `expires_at`, `used_at`, `created_at` e índice `idx_email_change_tokens_user`.
- **Commit**: `feat(auth): add Flyway V6 migration for email_change_tokens`

### T-2 — Dominio: `User.conEmail` y `EmailChangeToken`
- **Files**:
  - `services/auth-service/src/main/java/com/flashdrop/auth/domain/model/User.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/domain/model/EmailChangeToken.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/domain/exception/InvalidEmailChangeTokenException.java`
- **TDD RED first**: sí — `UserTest` verifica que `conEmail` devuelve nueva instancia con el email actualizado preservando `id, rut, name, lastName, phone, photo, roles, createdAt`.
- **Commit**: `feat(auth): add User.conEmail and EmailChangeToken domain model`

### T-3 — Puertos outbound y adaptadores de persistencia/transacción
- **Files**:
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/port/outbound/CredentialStore.java` (`updateLogin`, `existsByLogin`)
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/port/outbound/EmailChangeTokenStore.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/port/outbound/EmailChangeMailer.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/port/outbound/TransactionalRunner.java`
  - Adaptadores JPA correspondientes (`JpaCredentialStoreAdapter`, `SpringDataLoginRepository`, `EmailChangeTokenEntity`, `JpaEmailChangeTokenStoreAdapter`, `SpringDataEmailChangeTokenRepository`, `SpringTransactionalRunner`)
- **Acceptance**: `consume` es atómico (`UPDATE ... WHERE token_hash = :hash AND used_at IS NULL AND expires_at > :now`). `updateLogin` actualiza `login.login` para `id_users = :userId`.
- **Commit**: `feat(auth): add email change persistence ports and JPA adapters`

### T-4 — Casos de uso `RequestEmailChangeService` y `ConfirmEmailChangeService`
- **Files**:
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/port/inbound/RequestEmailChangeUseCase.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/port/inbound/ConfirmEmailChangeUseCase.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/dto/RequestEmailChangeCommand.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/dto/ConfirmEmailChangeCommand.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/usecase/RequestEmailChangeService.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/application/usecase/ConfirmEmailChangeService.java`
- **TDD RED first**: sí — `RequestEmailChangeServiceTest` y `ConfirmEmailChangeServiceTest` (espejando `RequestPasswordResetServiceTest` y `ResetPasswordServiceTest`):
  - contraseña inválida → `401`, no genera token ni envía correo
  - rate limit excedido → `429`
  - email nuevo inválido o colisión → `400`/`409` sin consumir token
  - confirmación exitosa → actualiza `users.email` + `login.login`, revoca todos los refresh tokens y audita
- **Commit**: `feat(auth): implement RequestEmailChangeService and ConfirmEmailChangeService`

### T-5 — Mailer SMTP y configuración de propiedades
- **Files**:
  - `services/auth-service/src/main/java/com/flashdrop/auth/infrastructure/adapter/outbound/mail/SmtpEmailChangeMailer.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/infrastructure/config/AuthBeansConfig.java` (o config equivalente)
  - `services/auth-service/src/main/resources/application.yml` (propiedad `linkBaseUrl` / `tokenTtl`)
- **TDD RED first**: sí — `SmtpEmailChangeMailerTest`.
- **Commit**: `feat(auth): add SmtpEmailChangeMailer and email change bean wiring`

### T-6 — REST endpoints, DTOs, `SecurityConfig` y manejo de errores
- **Files**:
  - `services/auth-service/src/main/java/com/flashdrop/auth/infrastructure/adapter/inbound/rest/dto/RequestEmailChangeRequest.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/infrastructure/adapter/inbound/rest/dto/ConfirmEmailChangeRequest.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/infrastructure/adapter/inbound/rest/AuthController.java`
  - `services/auth-service/src/main/java/com/flashdrop/auth/infrastructure/adapter/inbound/rest/GlobalExceptionHandler.java` (o equivalente)
  - `services/auth-service/src/main/java/com/flashdrop/auth/infrastructure/config/SecurityConfig.java`
- **TDD RED first**: sí — ampliar `AuthControllerTest` cubriendo `202`, `204`, `400`, `401`, `409`, `429`.
- **Commit**: `feat(auth): expose /auth/email/change-request and /auth/email/confirm endpoints`

### T-7 — Integration test end-to-end en Postgres
- **Files**: `services/auth-service/src/test/java/com/flashdrop/auth/AuthEmailChangeIT.java` (espejando `AuthPostgresIT` / `AuthAccountDeletionIT`)
- **Acceptance**: valida flujo completo contra Postgres real: request con password → confirm con nuevo email → login con email viejo falla (`401`) → login con email nuevo funciona (`200`) → refresh token previo rechazado → `GET /api/internal/users/{id}` devuelve el email nuevo.
- **Commit**: `test(auth): add AuthEmailChangeIT integration test`

---

## PR-gateway-email-change — `chore/gateway-email-change-override`

**Branch**: `chore/gateway-email-change-override` (base: `main`)
**Scope**: permitir acceso público a `POST /auth/email/confirm` en el gateway

### T-8 — Agregar `/auth/email/confirm` a `jwtOverrides`
- **Files**: `gateway/docker/gateway.yaml`
- **Acceptance**: `/auth/email/confirm` configurado con `jwt: { enabled: false, mode: jwks, issuer: flashdrop-auth }`. `/auth/email/change-request` permanece protegido por JWT.
- **Commit**: `chore(gateway): allow public POST /auth/email/confirm in jwtOverrides`
